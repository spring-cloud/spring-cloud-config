/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.config.server.environment;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.config.EnvironmentRepositoryConfiguration;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

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

	@Configuration
	@EnableConfigurationProperties(ConfigServerProperties.class)
	@Import({ PropertyPlaceholderAutoConfiguration.class,
		EnvironmentRepositoryConfiguration.class })
	protected static class TestConfiguration {
	}

}
