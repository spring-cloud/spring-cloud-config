/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.config.server.environment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.config.EnvironmentRepositoryConfiguration;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * @author Dave Syer
 * @author Roy Clarkson
 */
public class JGitEnvironmentRepositoryConcurrencyTests {

	private ConfigurableApplicationContext context;

	private File basedir = new File("target/config");

	@Before
	public void init() throws Exception {
		if (this.basedir.exists()) {
			FileUtils.delete(this.basedir, FileUtils.RECURSIVE);
		}
		ConfigServerTestUtils.deleteLocalRepo("config-copy");
	}

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void vanilla() throws Exception {
		String uri = ConfigServerTestUtils.prepareLocalRepo();
		this.context = new SpringApplicationBuilder(TestConfiguration.class).web(false)
				.properties("spring.cloud.config.server.git.uri:" + uri).run();
		final EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		ExecutorService threads = Executors.newFixedThreadPool(4);
		List<Future<Boolean>> tasks = new ArrayList<Future<Boolean>>();
		for (int i=0; i<30; i++) {
			tasks.add(threads.submit(new Runnable() {
				@Override
				public void run() {
					repository.findOne("bar", "staging", "master");
				}
			}, true));
		}
		for (Future<Boolean> future : tasks) {
			future.get();
		}
		Environment environment = repository.findOne("bar", "staging", "master");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals("bar", environment.getName());
		assertArrayEquals(new String[] { "staging" }, environment.getProfiles());
		assertEquals("master", environment.getLabel());
	}

	protected Log logger = LogFactory.getLog(getClass());

	/**
	 * Simulates following actions in parallel:
	 *   - Client tries to obtain configuration with specified label
	 *   - Spring Refresh Context Event occurs
	 */
	@Test
	public void concurrentRefreshContextAndGetLabels() throws Exception {
		// Prepare the repo
		final JGitConfigServerTestData testData = JGitConfigServerTestData.prepareClonedGitRepository(TestConfiguration.class);
		JGitEnvironmentRepository repository = testData.getRepository();
		repository.setCloneOnStart(true);
		repository.setGitFactory(new DelayedGitFactoryMock());
		repository.setBasedir(testData.getClonedGit().getGitWorkingDirectory());
		repository.setUri(testData.getServerGit().getGitWorkingDirectory().getAbsolutePath().replace("file://", ""));

		final AtomicInteger errorCount = new AtomicInteger();

		// Prepare two threads to do the parallel work
		Thread client = new Thread(new Runnable() {
			@Override
			public void run() {
				logger.info("client start.");
				try {
					Environment environment = testData.getRepository().findOne("bar", "staging", "master");
				} catch (Exception e) {
					errorCount.incrementAndGet();
					e.printStackTrace();
				}
				logger.info("client end.");
			}
		});

		Thread refresh = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					logger.info("refresh start.");
					testData.getRepository().afterPropertiesSet();
					logger.info("refresh end.");
				} catch (Exception e) {
					errorCount.incrementAndGet();
					e.printStackTrace();
				}
			}
		});

		// Start the parallel actions and wait till the end.
		refresh.start();
		client.start();
		refresh.join();
		client.join();

		assertEquals(0, errorCount.get());
	}

	@Configuration
	@EnableConfigurationProperties(ConfigServerProperties.class)
	@Import({ PropertyPlaceholderAutoConfiguration.class,
		EnvironmentRepositoryConfiguration.class })
	protected static class TestConfiguration {
	}

	private static class DelayedGitFactoryMock extends JGitEnvironmentRepository.JGitFactory {

		@Override
		public Git getGitByOpen(File file) throws IOException {
			Git originalGit = DelayedGitMock.open(file);
			return new DelayedGitMock(originalGit.getRepository());
		}

		@Override
		public CloneCommand getCloneCommandByCloneRepository() {
			return new DelayedCloneCommand();
		}
	}

	private static class DelayedGitMock extends Git {

		public DelayedGitMock(Repository repo) {
			super(repo);
		}

		@Override
		public FetchCommand fetch() {
			return new DelayedFetchCommand(getRepository());
		}

		@Override
		public CheckoutCommand checkout() {
			return new DelayedCheckoutCommand(getRepository());
		}
	}

	private static class DelayedCloneCommand extends CloneCommand {
		@Override
		public Git call() throws GitAPIException, InvalidRemoteException, TransportException {
			try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			return super.call();
		}
	}

	private static class DelayedFetchCommand extends FetchCommand {

		public DelayedFetchCommand(Repository repo) {
			super(repo);
		}

		@Override
		public FetchResult call() throws GitAPIException, InvalidRemoteException, TransportException {
			try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			return super.call();
		}
	}

	private static class DelayedCheckoutCommand extends CheckoutCommand {
		public DelayedCheckoutCommand(Repository repo) {
			super(repo);
		}

		@Override
		public Ref call() throws GitAPIException, RefAlreadyExistsException, RefNotFoundException, InvalidRefNameException, CheckoutConflictException {
			try {
				Thread.sleep(250);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			return super.call();
		}
	}

}
