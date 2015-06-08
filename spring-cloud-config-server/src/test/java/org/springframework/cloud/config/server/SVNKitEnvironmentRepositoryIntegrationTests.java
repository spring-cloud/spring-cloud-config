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

package org.springframework.cloud.config.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnCommit;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StreamUtils;

import static org.junit.Assert.assertEquals;

/**
 * @author Michael Prankl
 * @author Roy Clarkson
 */
public class SVNKitEnvironmentRepositoryIntegrationTests {

	private ConfigurableApplicationContext context;

	private File workingDir;

	@Before
	public void init() {
		workingDir = new File("target/repos/svn-config-repo-update");
		if (workingDir.exists()) {
			FileSystemUtils.deleteRecursively(workingDir);
		}
	}

	@After
	public void close() {
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void vanilla() throws Exception {
		String uri = ConfigServerTestUtils.prepareLocalSvnRepo(
				"src/test/resources/svn-config-repo", "target/config");
		context = new SpringApplicationBuilder(TestConfiguration.class).web(false)
				.profiles("subversion")
				.run("--spring.cloud.config.server.svn.uri=" + uri);
		EnvironmentRepository repository = context.getBean(EnvironmentRepository.class);
		repository.findOne("bar", "staging", "trunk");
		Environment environment = repository.findOne("bar", "staging", "trunk");
		assertEquals(2, environment.getPropertySources().size());
	}

	@Test
	public void update() throws Exception {
		String uri = ConfigServerTestUtils.prepareLocalSvnRepo(
				"src/test/resources/svn-config-repo", "target/config");
		context = new SpringApplicationBuilder(TestConfiguration.class).web(false)
				.profiles("subversion")
				.run("--spring.cloud.config.server.svn.uri=" + uri);
		EnvironmentRepository repository = context.getBean(EnvironmentRepository.class);
		repository.findOne("bar", "staging", "trunk");
		Environment environment = repository.findOne("bar", "staging", "trunk");
		assertEquals("bar", environment.getPropertySources().get(0).getSource()
				.get("foo"));
		updateRepoForUpdate(uri);
		environment = repository.findOne("bar", "staging", "trunk");
		assertEquals("foo", environment.getPropertySources().get(0).getSource()
				.get("foo"));
	}

	private void updateRepoForUpdate(String uri) throws SVNException,
			FileNotFoundException, IOException {
		SvnOperationFactory svnFactory = new SvnOperationFactory();
		final SvnCheckout checkout = svnFactory.createCheckout();
		checkout.setSource(SvnTarget.fromURL(SVNURL.parseURIEncoded(uri)));
		checkout.setSingleTarget(SvnTarget.fromFile(workingDir));
		checkout.run();

		// update bar.properties
		File barProps = new File(workingDir, "trunk/bar.properties");
		StreamUtils.copy("foo: foo", Charset.defaultCharset(), new FileOutputStream(
				barProps));
		// commit to repo
		SvnCommit svnCommit = svnFactory.createCommit();
		svnCommit.setCommitMessage("update bar.properties");
		svnCommit.setSingleTarget(SvnTarget.fromFile(barProps));
		svnCommit.run();
	}

	@Test
	public void defaultLabel() throws Exception {
		String uri = ConfigServerTestUtils.prepareLocalSvnRepo(
				"src/test/resources/svn-config-repo", "target/config");
		context = new SpringApplicationBuilder(TestConfiguration.class).web(false)
				.profiles("subversion")
				.run("--spring.cloud.config.server.svn.uri=" + uri);
		EnvironmentRepository repository = context.getBean(EnvironmentRepository.class);
		assertEquals("trunk", repository.getDefaultLabel());
	}

	@Test(expected=NoSuchLabelException.class)
	public void invalidLabel() throws Exception {
		String uri = ConfigServerTestUtils.prepareLocalSvnRepo(
				"src/test/resources/svn-config-repo", "target/config");
		context = new SpringApplicationBuilder(TestConfiguration.class).web(false)
				.profiles("subversion")
				.run("--spring.cloud.config.server.svn.uri=" + uri);
		EnvironmentRepository repository = context.getBean(EnvironmentRepository.class);
		repository.findOne("bar", "staging", "unknownlabel");
		Environment environment = repository.findOne("bar", "staging", "unknownlabel");
		assertEquals(0, environment.getPropertySources().size());
	}

	@Configuration
	@Import({ PropertyPlaceholderAutoConfiguration.class, EnvironmentRepositoryConfiguration.class })
	protected static class TestConfiguration {
	}

}
