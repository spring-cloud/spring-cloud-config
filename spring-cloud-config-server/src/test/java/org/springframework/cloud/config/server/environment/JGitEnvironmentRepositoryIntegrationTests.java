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

import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.util.FileUtils;
import org.hamcrest.Matchers;
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
import org.springframework.util.ResourceUtils;
import org.springframework.util.StreamUtils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Dave Syer
 * @author Roy Clarkson
 * @author Daniel Lavoie
 * @author Ryan Lynch
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
		SearchPathLocator.Locations locations = repository.getLocations("bar", "test", "raw");
		assertEquals(locations.getVersion(), commitToRevertBeforePull);

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
		String conflictingCommit = git.log().setMaxCount(1).call().iterator()
				.next().getName();

		// Reset to the raw branch.
		git.reset().setMode(ResetType.HARD).setRef(commitToRevertBeforePull).call();

		// Triggers the repository refresh.
		locations = repository.getLocations("bar", "test", "raw");
		assertEquals(locations.getVersion(), conflictingCommit);

		assertTrue("Local repository is not cleaned after retrieving resources.",
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
				.getBean(JGitEnvironmentRepository.class);
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

	@Test
	public void testVersionUpdate() throws Exception {
		JGitConfigServerTestData testData = JGitConfigServerTestData.prepareClonedGitRepository(TestConfiguration.class);

		//get our starting versions
		String startingLocalVersion = getCommitID(testData.getClonedGit().getGit(), "master");
		String startingRemoteVersion = getCommitID(testData.getServerGit().getGit(), "master");

		//make sure we get the right version out of the gate
		Environment environment = testData.getRepository().findOne("bar", "staging", "master");

		//make sure the environments version is the same as the remote repo version
		assertEquals(environment.getVersion(), startingRemoteVersion);

		//update the remote repo
		FileOutputStream out = new FileOutputStream(new File(testData.getServerGit().getGitWorkingDirectory(), "bar.properties"));
		StreamUtils.copy("foo: foo", Charset.defaultCharset(), out);
		testData.getServerGit().getGit().add().addFilepattern("bar.properties").call();
		testData.getServerGit().getGit().commit().setMessage("Updated for pull").call();

		//pull the environment again which should update the local repo from the just updated remote repo
		environment = testData.getRepository().findOne("bar", "staging", "master");

		//do some more check outs to get updated version numbers
		String updatedLocalVersion = getCommitID(testData.getClonedGit().getGit(), "master");
		String updatedRemoteVersion = getCommitID(testData.getClonedGit().getGit(), "master");

		//make sure our versions have been updated
		assertEquals(updatedRemoteVersion, updatedLocalVersion);
		assertNotEquals(updatedRemoteVersion, startingRemoteVersion);
		assertNotEquals(updatedLocalVersion, startingLocalVersion);

		//make sure our environment also reflects the updated version
		//this used to have a bug
		assertEquals(environment.getVersion(), updatedRemoteVersion);
	}

	@Test
	public void testNewRemoteBranch() throws Exception {
		JGitConfigServerTestData testData = JGitConfigServerTestData.prepareClonedGitRepository(TestConfiguration.class);

		Environment environment = testData.getRepository().findOne("bar", "staging", "master");
		Object fooProperty = ConfigServerTestUtils.getProperty(environment, "bar.properties", "foo");
		assertEquals(fooProperty, "bar");

		testData.getServerGit().getGit().branchCreate()
				.setName("testNewRemoteBranch")
				.call();

		testData.getServerGit().getGit().checkout()
				.setName("testNewRemoteBranch")
				.call();

		//update the remote repo
		FileOutputStream out = new FileOutputStream(
				new File(testData.getServerGit().getGitWorkingDirectory(), "/bar.properties"));
		StreamUtils.copy("foo: branchBar", Charset.defaultCharset(), out);
		testData.getServerGit().getGit().add().addFilepattern("bar.properties").call();
		testData.getServerGit().getGit().commit().setMessage("Updated for branch test").call();

		environment = testData.getRepository().findOne("bar", "staging", "testNewRemoteBranch");
		fooProperty = ConfigServerTestUtils.getProperty(environment, "bar.properties", "foo");
		assertEquals(fooProperty, "branchBar");
	}

	@Test
	public void testNewRemoteTag() throws Exception {
		JGitConfigServerTestData testData = JGitConfigServerTestData.prepareClonedGitRepository(TestConfiguration.class);

		Git serverGit = testData.getServerGit().getGit();

		Environment environment = testData.getRepository().findOne("bar", "staging", "master");
		Object fooProperty = ConfigServerTestUtils.getProperty(environment, "bar.properties", "foo");
		assertEquals(fooProperty, "bar");

		serverGit.checkout().setName("master").call();

		//create a new tag
		serverGit.tag().setName("testTag").setMessage("Testing a tag").call();

		//update the remote repo
		FileOutputStream out = new FileOutputStream(
				new File(testData.getServerGit().getGitWorkingDirectory(), "/bar.properties"));
		StreamUtils.copy("foo: testAfterTag", Charset.defaultCharset(), out);
		testData.getServerGit().getGit().add().addFilepattern("bar.properties").call();
		testData.getServerGit().getGit().commit().setMessage("Updated for branch test").call();

		environment = testData.getRepository().findOne("bar", "staging", "master");
		fooProperty = ConfigServerTestUtils.getProperty(environment, "bar.properties", "foo");
		assertEquals(fooProperty, "testAfterTag");

		environment = testData.getRepository().findOne("bar", "staging", "testTag");
		fooProperty = ConfigServerTestUtils.getProperty(environment, "bar.properties", "foo");
		assertEquals(fooProperty, "bar");

        //now move the tag and test again
        serverGit.tag().setName("testTag").setForceUpdate(true).setMessage("Testing a moved tag").call();

        environment = testData.getRepository().findOne("bar", "staging", "testTag");
        fooProperty = ConfigServerTestUtils.getProperty(environment, "bar.properties", "foo");
        assertEquals(fooProperty, "testAfterTag");

	}

    @Test
	public void testNewCommitID() throws Exception {
		JGitConfigServerTestData testData = JGitConfigServerTestData.prepareClonedGitRepository(TestConfiguration.class);

		//get our starting versions
		String startingRemoteVersion = getCommitID(testData.getServerGit().getGit(), "master");

		//make sure we get the right version out of the gate
		Environment environment = testData.getRepository().findOne("bar", "staging", "master");
		assertEquals(environment.getVersion(), startingRemoteVersion);

		//update the remote repo
		FileOutputStream out = new FileOutputStream(new File(testData.getServerGit().getGitWorkingDirectory(), "bar.properties"));
		StreamUtils.copy("foo: barNewCommit", Charset.defaultCharset(), out);
		testData.getServerGit().getGit().add().addFilepattern("bar.properties").call();
		testData.getServerGit().getGit().commit().setMessage("Updated for pull").call();
		String updatedRemoteVersion = getCommitID(testData.getServerGit().getGit(), "master");

		//do a normal request and verify we get the new version
		environment = testData.getRepository().findOne("bar", "staging", "master");
		assertEquals(environment.getVersion(), updatedRemoteVersion);
		Object fooProperty = ConfigServerTestUtils.getProperty(environment, "bar.properties", "foo");
		assertEquals(fooProperty, "barNewCommit");

		//request the prior commit ID and make sure we get it
		environment = testData.getRepository().findOne("bar", "staging", startingRemoteVersion);
		assertEquals(environment.getVersion(), startingRemoteVersion);
		fooProperty = ConfigServerTestUtils.getProperty(environment, "bar.properties", "foo");
		assertEquals(fooProperty, "bar");
	}


	@Test(expected = NoSuchLabelException.class)
	public void testUnknownLabelWithRemote() throws Exception {
		JGitConfigServerTestData testData = JGitConfigServerTestData.prepareClonedGitRepository(TestConfiguration.class);
		testData.getRepository().findOne("bar", "staging", "BADLabel");
	}

	private String getCommitID(Git git, String label) throws GitAPIException {
		CheckoutCommand checkout = git.checkout();
		checkout.setName(label);
		Ref localRef = checkout.call();
		return localRef.getObjectId().getName();
	}

	public void passphrase() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo("config-repo");
		final String passphrase = "thisismypassphrase";
		this.context = new SpringApplicationBuilder(TestConfiguration.class).web(false)
				.run("--spring.cloud.config.server.git.uri=" + uri,
						"--spring.cloud.config.server.git.passphrase=" + passphrase);
		JGitEnvironmentRepository repository = this.context.getBean(JGitEnvironmentRepository.class);
		assertThat(repository.getPassphrase(), Matchers.containsString(passphrase));
	}

	@Test
	public void strictHostKeyChecking() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo("config-repo");
		final boolean strictHostKeyChecking = true;
		this.context = new SpringApplicationBuilder(TestConfiguration.class).web(false)
				.run("--spring.cloud.config.server.git.uri=" + uri,
						"--spring.cloud.config.server.git.strict-host-key-checking=" + strictHostKeyChecking);
		JGitEnvironmentRepository repository = this.context.getBean(JGitEnvironmentRepository.class);
		assertEquals(repository.isStrictHostKeyChecking(), strictHostKeyChecking);
	}

	@Configuration
	@EnableConfigurationProperties(ConfigServerProperties.class)
	@Import({ PropertyPlaceholderAutoConfiguration.class,
			EnvironmentRepositoryConfiguration.class })
	protected static class TestConfiguration {
	}

}
