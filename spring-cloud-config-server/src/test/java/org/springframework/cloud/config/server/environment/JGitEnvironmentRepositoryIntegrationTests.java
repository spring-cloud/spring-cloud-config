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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.util.FileUtils;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.config.EnvironmentRepositoryConfiguration;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StreamUtils;

/**
 * @author Dave Syer
 * @author Roy Clarkson
 * @author Daniel Lavoie
 */
public class JGitEnvironmentRepositoryIntegrationTests {

	private ConfigurableApplicationContext context;

	private File basedir = new File("target/config");

	@Before
	public void init() throws Exception {
		if (this.basedir.exists()) {
			FileUtils.delete(this.basedir, FileUtils.RECURSIVE);
		}
		ConfigServerTestUtils.deleteLocalRepo("");
	}

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void vanilla() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo();
		this.context = new SpringApplicationBuilder(TestConfiguration.class).web(false)
				.properties("spring.cloud.config.server.git.uri:" + uri).run();
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		repository.findOne("bar", "staging", "master");
		Environment environment = repository.findOne("bar", "staging", "master");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals("bar", environment.getName());
		assertArrayEquals(new String[] { "staging" }, environment.getProfiles());
		assertEquals("master", environment.getLabel());
	}

	@Test
	public void pull() throws Exception {
		ConfigServerTestUtils.prepareLocalRepo();
		String uri = ConfigServerTestUtils.copyLocalRepo("config-copy");
		this.context = new SpringApplicationBuilder(TestConfiguration.class).web(false)
				.run("--spring.cloud.config.server.git.uri=" + uri);
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		repository.findOne("bar", "staging", "master");
		Environment environment = repository.findOne("bar", "staging", "master");
		assertEquals("bar",
				environment.getPropertySources().get(0).getSource().get("foo"));
		Git git = Git.open(ResourceUtils.getFile(uri).getAbsoluteFile());
		git.checkout().setName("master").call();
		StreamUtils.copy("foo: foo", Charset.defaultCharset(),
				new FileOutputStream(ResourceUtils.getFile(uri + "/bar.properties")));
		git.add().addFilepattern("bar.properties").call();
		git.commit().setMessage("Updated for pull").call();
		environment = repository.findOne("bar", "staging", "master");
		assertEquals("foo",
				environment.getPropertySources().get(0).getSource().get("foo"));
	}

	/**
	 * Tests a special use case where the remote repository has been updated with a forced
	 * push conflicting with the local repo of the Config Server. The Config Server has to
	 * reset hard on the new reference because a simple pull operation could result in a
	 * conflicting local repository.
	 */
	@Test
	public void pullDirtyRepo() throws Exception {
		ConfigServerTestUtils.prepareLocalRepo();
		String uri = ConfigServerTestUtils.copyLocalRepo("config-copy");

		// Create a remote bare repository.
		Repository remote = ConfigServerTestUtils.prepareBareRemote();

		Git git = Git.open(ResourceUtils.getFile(uri).getAbsoluteFile());
		StoredConfig config = git.getRepository().getConfig();
		config.setString("remote", "origin", "url",
				remote.getDirectory().getAbsolutePath());
		config.setString("remote", "origin", "fetch",
				"+refs/heads/*:refs/remotes/origin/*");
		config.save();

		// Pushes the raw branch to remote repository.
		git.push().call();

		String commitToRevertBeforePull = git.log().setMaxCount(1).call().iterator()
				.next().getName();

		this.context = new SpringApplicationBuilder(TestConfiguration.class).web(false)
				.run("--spring.cloud.config.server.git.uri=" + uri);

		JGitEnvironmentRepository repository = this.context
				.getBean(JGitEnvironmentRepository.class);

		// Fetches the repository for the first time.
		repository.getLocations("bar", "test", "raw");

		// Resets to the original commit.
		git.reset().setMode(ResetType.HARD).setRef("master").call();

		// Generate a conflicting commit who will be forced on the origin.
		Path applicationFilePath = Paths
				.get(ResourceUtils.getFile(uri).getAbsoluteFile() + "/application.yml");

		Files.write(applicationFilePath,
				Arrays.asList("info:", "  foo: bar", "raw: false"),
				StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
		git.add().addFilepattern(".").call();
		git.commit().setMessage("Conflicting commit.").call();
		git.push().setForce(true).call();

		// Reset to the raw branch.
		git.reset().setMode(ResetType.HARD).setRef(commitToRevertBeforePull).call();

		// Triggers the repository refresh.
		repository.getLocations("bar", "test", "raw");

		Assert.assertTrue("Local repository is not cleaned after retreiving resources.",
				git.status().call().isClean());
	}

	@Test
	public void nested() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo("another-config-repo");
		this.context = new SpringApplicationBuilder(TestConfiguration.class).web(false)
				// TODO: why didn't .properties() work for me?
				.run("--spring.cloud.config.server.git.uri=" + uri,
						"--spring.cloud.config.server.git.searchPaths=sub");
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		repository.findOne("bar", "staging", "master");
		Environment environment = repository.findOne("bar", "staging", "master");
		assertEquals(2, environment.getPropertySources().size());
	}

	@Test
	public void nestedWithApplicationPlaceholders() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo("nested-repo");
		this.context = new SpringApplicationBuilder(TestConfiguration.class).web(false)
				// TODO: why didn't .properties() work for me?
				.run("--spring.cloud.config.server.git.uri=" + uri,
						"--spring.cloud.config.server.git.searchPaths={application}");
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		repository.findOne("foo,bar", "staging", "master");
		Environment environment = repository.findOne("foo,bar", "staging", "master");
		assertEquals(3, environment.getPropertySources().size());
	}

	@Test
	public void nestedWithProfilePlaceholders() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo("nested-repo");
		this.context = new SpringApplicationBuilder(TestConfiguration.class).web(false)
				// TODO: why didn't .properties() work for me?
				.run("--spring.cloud.config.server.git.uri=" + uri,
						"--spring.cloud.config.server.git.searchPaths={profile}");
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		repository.findOne("foo,bar", "staging", "master");
		Environment environment = repository.findOne("staging", "foo,bar", "master");
		assertEquals(3, environment.getPropertySources().size());
	}

	@Test
	public void singleElementArrayIndexSearchPath() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo("nested-repo");
		this.context = new SpringApplicationBuilder(TestConfiguration.class).web(false)
				.run("--spring.cloud.config.server.git.uri=" + uri,
						"--spring.cloud.config.server.git.searchPaths[0]={application}");
		JGitEnvironmentRepository repository = this.context
				.getBean(JGitEnvironmentRepository.class);
		assertThat(repository.getSearchPaths(), Matchers.arrayContaining("{application}"));
		assertFalse(Arrays.equals(repository.getSearchPaths(),
				new JGitEnvironmentRepository(repository.getEnvironment())
						.getSearchPaths()));
	}

	@Test
	public void defaultLabel() throws Exception {
		String uri = ConfigServerTestUtils.prepareLocalRepo();
		this.context = new SpringApplicationBuilder(TestConfiguration.class).web(false)
				.properties("spring.cloud.config.server.git.uri:" + uri).run();
		JGitEnvironmentRepository repository = this.context
				.getBean(JGitEnvironmentRepository.class);
		assertEquals("master", repository.getDefaultLabel());
	}

	@Test(expected = NoSuchLabelException.class)
	public void invalidLabel() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo();
		this.context = new SpringApplicationBuilder(TestConfiguration.class).web(false)
				.properties("spring.cloud.config.server.git.uri:" + uri).run();
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		repository.findOne("bar", "staging", "unknownlabel");
	}

	@Test
	public void findOne_CloneOnStartTrue_FindOneSuccess() throws Exception {
		ConfigServerTestUtils.prepareLocalRepo();
		String uri = ConfigServerTestUtils.copyLocalRepo("config-copy");
		this.context = new SpringApplicationBuilder(TestConfiguration.class).web(false)
				.run("--spring.cloud.config.server.git.uri=" + uri,
						"--spring.cloud.config.server.git.cloneOnStart=true");
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		assertTrue(((JGitEnvironmentRepository) repository).isCloneOnStart());
		Environment environment = repository.findOne("bar", "staging", "master");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals("bar", environment.getName());
		assertArrayEquals(new String[] { "staging" }, environment.getProfiles());
		assertEquals("master", environment.getLabel());
	}

	@Test
	public void findOne_FileAddedToRepo_FindOneSuccess() throws Exception {
		ConfigServerTestUtils.prepareLocalRepo();
		String uri = ConfigServerTestUtils.copyLocalRepo("config-copy");
		this.context = new SpringApplicationBuilder(TestConfiguration.class).web(false)
				.run("--spring.cloud.config.server.git.uri=" + uri,
						"--spring.cloud.config.server.git.cloneOnStart=true");
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		repository.findOne("bar", "staging", "master");
		Environment environment = repository.findOne("bar", "staging", "master");
		assertEquals("bar",
				environment.getPropertySources().get(0).getSource().get("foo"));
		Git git = Git.open(ResourceUtils.getFile(uri).getAbsoluteFile());
		git.checkout().setName("master").call();
		StreamUtils.copy("foo: foo", Charset.defaultCharset(),
				new FileOutputStream(ResourceUtils.getFile(uri + "/bar.properties")));
		git.add().addFilepattern("bar.properties").call();
		git.commit().setMessage("Updated for pull").call();
		environment = repository.findOne("bar", "staging", "master");
		assertEquals("foo",
				environment.getPropertySources().get(0).getSource().get("foo"));
	}

	@Test
	public void findOne_NestedSearchPath_FindOneSuccess() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo("another-config-repo");
		this.context = new SpringApplicationBuilder(TestConfiguration.class).web(false)
				// TODO: why didn't .properties() work for me?
				.run("--spring.cloud.config.server.git.uri=" + uri,
						"--spring.cloud.config.server.git.searchPaths=sub",
						"--spring.cloud.config.server.git.cloneOnStart=true");
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		repository.findOne("bar", "staging", "master");
		Environment environment = repository.findOne("bar", "staging", "master");
		assertEquals(2, environment.getPropertySources().size());
	}

	@Test(expected = NoSuchLabelException.class)
	public void findOne_FindInvalidLabel_IllegalStateExceptionThrown()
			throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo();
		this.context = new SpringApplicationBuilder(TestConfiguration.class).web(false)
				.properties("spring.cloud.config.server.git.uri:" + uri,
						"--spring.cloud.config.server.git.cloneOnStart=true")
				.run();
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		repository.findOne("bar", "staging", "unknownlabel");
	}

	@Configuration
	@Import({ PropertyPlaceholderAutoConfiguration.class,
			EnvironmentRepositoryConfiguration.class })
	protected static class TestConfiguration {
	}

}
