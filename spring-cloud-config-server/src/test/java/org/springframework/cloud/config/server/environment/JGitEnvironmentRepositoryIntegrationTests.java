/*
 * Copyright 2013-2019 the original author or authors.
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;

import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.config.EnvironmentRepositoryConfiguration;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * @author Dave Syer
 * @author Roy Clarkson
 * @author Daniel Lavoie
 * @author Ryan Lynch
 */
public class JGitEnvironmentRepositoryIntegrationTests {

	private ConfigurableApplicationContext context;

	private File basedir = new File("target/config");

	@BeforeClass
	public static void initClass() {
		// mock Git configuration to make tests independent of local Git configuration
		SystemReader.setInstance(new MockSystemReader());
	}

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
		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				.properties("spring.cloud.config.server.git.uri:" + uri).run();
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		Environment environment = repository.findOne("bar", "staging", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(environment.getName()).isEqualTo("bar");
		assertThat(environment.getProfiles()).isEqualTo(new String[] { "staging" });
		assertThat(environment.getLabel()).isEqualTo("master");
	}

	@Test
	public void pull() throws Exception {
		ConfigServerTestUtils.prepareLocalRepo();
		String uri = ConfigServerTestUtils.copyLocalRepo("config-copy");
		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				.run("--spring.cloud.config.server.git.uri=" + uri);
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		Environment environment = repository.findOne("bar", "staging", "master");
		assertThat(environment.getPropertySources().get(0).getSource().get("foo"))
				.isEqualTo("bar");
		Git git = Git.open(ResourceUtils.getFile(uri).getAbsoluteFile());
		git.checkout().setName("master").call();
		StreamUtils.copy("foo: foo", Charset.defaultCharset(),
				new FileOutputStream(ResourceUtils.getFile(uri + "/bar.properties")));
		git.add().addFilepattern("bar.properties").call();
		git.commit().setMessage("Updated for pull").call();
		environment = repository.findOne("bar", "staging", "master");
		assertThat(environment.getPropertySources().get(0).getSource().get("foo"))
				.isEqualTo("foo");
	}

	/**
	 * Tests a special use case where the remote repository has been updated with a forced
	 * push conflicting with the local repo of the Config Server. The Config Server has to
	 * reset hard on the new reference because a simple pull operation could result in a
	 * conflicting local repository.
	 * @throws Exception when git related exception happens
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

		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				.run("--spring.cloud.config.server.git.uri=" + uri);

		JGitEnvironmentRepository repository = this.context
				.getBean(JGitEnvironmentRepository.class);

		// Fetches the repository for the first time.
		SearchPathLocator.Locations locations = repository.getLocations("bar", "test",
				"raw");
		assertThat(commitToRevertBeforePull).isEqualTo(locations.getVersion());

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
		String conflictingCommit = git.log().setMaxCount(1).call().iterator().next()
				.getName();

		// Reset to the raw branch.
		git.reset().setMode(ResetType.HARD).setRef(commitToRevertBeforePull).call();

		// Triggers the repository refresh.
		locations = repository.getLocations("bar", "test", "raw");
		assertThat(conflictingCommit).isEqualTo(locations.getVersion());

		assertThat(git.status().call().isClean())
				.as("Local repository is not cleaned after retrieving resources.")
				.isTrue();
	}

	@Test
	public void pullMissingRepo() throws Exception {
		pull();
		JGitEnvironmentRepository repository = this.context
				.getBean(JGitEnvironmentRepository.class);
		new File(repository.getUri().replaceAll("file:", ""), ".git/index.lock")
				.createNewFile();
		Environment environment = repository.findOne("bar", "staging", "master");
		assertThat(environment.getPropertySources().get(0).getSource().get("foo"))
				.isEqualTo("foo");
	}

	@Test
	public void nested() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo("another-config-repo");
		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				// TODO: why didn't .properties() work for me?
				.run("--spring.cloud.config.server.git.uri=" + uri,
						"--spring.cloud.config.server.git.searchPaths=sub");
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		Environment environment = repository.findOne("bar", "staging", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
	}

	@Test
	public void nestedWithApplicationPlaceholders() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo("nested-repo");
		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				// TODO: why didn't .properties() work for me?
				.run("--spring.cloud.config.server.git.uri=" + uri,
						"--spring.cloud.config.server.git.searchPaths={application}");
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		Environment environment = repository.findOne("foo,bar", "staging", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(3);
	}

	@Test
	public void nestedWithProfilePlaceholders() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo("nested-repo");
		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				// TODO: why didn't .properties() work for me?
				.run("--spring.cloud.config.server.git.uri=" + uri,
						"--spring.cloud.config.server.git.searchPaths={profile}");
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		Environment environment = repository.findOne("staging", "foo,bar", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(3);
	}

	@Test
	public void singleElementArrayIndexSearchPath() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo("nested-repo");
		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				.run("--spring.cloud.config.server.git.uri=" + uri,
						"--spring.cloud.config.server.git.searchPaths[0]={application}");
		JGitEnvironmentRepository repository = this.context
				.getBean(JGitEnvironmentRepository.class);
		assertThat(repository.getSearchPaths()).containsExactly("{application}");
		assertThat(
				Arrays.equals(repository.getSearchPaths(),
						new JGitEnvironmentRepository(repository.getEnvironment(),
								new JGitEnvironmentProperties()).getSearchPaths()))
										.isFalse();
	}

	@Test
	public void defaultLabel() throws Exception {
		String uri = ConfigServerTestUtils.prepareLocalRepo();
		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				.properties("spring.cloud.config.server.git.uri:" + uri).run();
		JGitEnvironmentRepository repository = this.context
				.getBean(JGitEnvironmentRepository.class);
		assertThat(repository.getDefaultLabel()).isEqualTo("master");
	}

	@Test(expected = NoSuchLabelException.class)
	public void invalidLabel() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo();
		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				.properties("spring.cloud.config.server.git.uri:" + uri).run();
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		repository.findOne("bar", "staging", "unknownlabel");
	}

	@Test
	public void findOne_CloneOnStartTrue_FindOneSuccess() throws Exception {
		ConfigServerTestUtils.prepareLocalRepo();
		String uri = ConfigServerTestUtils.copyLocalRepo("config-copy");
		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				.run("--spring.cloud.config.server.git.uri=" + uri,
						"--spring.cloud.config.server.git.cloneOnStart=true");
		EnvironmentRepository repository = this.context
				.getBean(JGitEnvironmentRepository.class);
		assertThat(((JGitEnvironmentRepository) repository).isCloneOnStart()).isTrue();
		Environment environment = repository.findOne("bar", "staging", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(environment.getName()).isEqualTo("bar");
		assertThat(environment.getProfiles()).isEqualTo(new String[] { "staging" });
		assertThat(environment.getLabel()).isEqualTo("master");
	}

	@Test
	public void findOne_FileAddedToRepo_FindOneSuccess() throws Exception {
		ConfigServerTestUtils.prepareLocalRepo();
		String uri = ConfigServerTestUtils.copyLocalRepo("config-copy");
		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				.run("--spring.cloud.config.server.git.uri=" + uri,
						"--spring.cloud.config.server.git.cloneOnStart=true");
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		Environment environment = repository.findOne("bar", "staging", "master");
		assertThat(environment.getPropertySources().get(0).getSource().get("foo"))
				.isEqualTo("bar");
		Git git = Git.open(ResourceUtils.getFile(uri).getAbsoluteFile());
		git.checkout().setName("master").call();
		StreamUtils.copy("foo: foo", Charset.defaultCharset(),
				new FileOutputStream(ResourceUtils.getFile(uri + "/bar.properties")));
		git.add().addFilepattern("bar.properties").call();
		git.commit().setMessage("Updated for pull").call();
		environment = repository.findOne("bar", "staging", "master");
		assertThat(environment.getPropertySources().get(0).getSource().get("foo"))
				.isEqualTo("foo");
	}

	@Test
	public void findOne_NestedSearchPath_FindOneSuccess() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo("another-config-repo");
		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				// TODO: why didn't .properties() work for me?
				.run("--spring.cloud.config.server.git.uri=" + uri,
						"--spring.cloud.config.server.git.searchPaths=sub",
						"--spring.cloud.config.server.git.cloneOnStart=true");
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		Environment environment = repository.findOne("bar", "staging", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
	}

	@Test(expected = NoSuchLabelException.class)
	public void findOne_FindInvalidLabel_IllegalStateExceptionThrown()
			throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo();
		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				.properties("spring.cloud.config.server.git.uri:" + uri,
						"--spring.cloud.config.server.git.cloneOnStart=true")
				.run();
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		repository.findOne("bar", "staging", "unknownlabel");
	}

	@Test
	public void testVersionUpdate() throws Exception {
		JGitConfigServerTestData testData = JGitConfigServerTestData
				.prepareClonedGitRepository(TestConfiguration.class);

		// get our starting versions
		String startingLocalVersion = getCommitID(testData.getClonedGit().getGit(),
				"master");
		String startingRemoteVersion = getCommitID(testData.getServerGit().getGit(),
				"master");

		// make sure we get the right version out of the gate
		Environment environment = testData.getRepository().findOne("bar", "staging",
				"master");

		// make sure the environments version is the same as the remote repo
		// version
		assertThat(startingRemoteVersion).isEqualTo(environment.getVersion());

		// update the remote repo
		FileOutputStream out = new FileOutputStream(new File(
				testData.getServerGit().getGitWorkingDirectory(), "bar.properties"));
		StreamUtils.copy("foo: foo", Charset.defaultCharset(), out);
		testData.getServerGit().getGit().add().addFilepattern("bar.properties").call();
		testData.getServerGit().getGit().commit().setMessage("Updated for pull").call();

		// pull the environment again which should update the local repo from
		// the just updated remote repo
		environment = testData.getRepository().findOne("bar", "staging", "master");

		// do some more check outs to get updated version numbers
		String updatedLocalVersion = getCommitID(testData.getClonedGit().getGit(),
				"master");
		String updatedRemoteVersion = getCommitID(testData.getClonedGit().getGit(),
				"master");

		// make sure our versions have been updated
		assertThat(updatedLocalVersion).isEqualTo(updatedRemoteVersion);
		assertThat(startingRemoteVersion).isNotEqualTo(updatedRemoteVersion);
		assertThat(startingLocalVersion).isNotEqualTo(updatedLocalVersion);

		// make sure our environment also reflects the updated version
		// this used to have a bug
		assertThat(updatedRemoteVersion).isEqualTo(environment.getVersion());
	}

	@Test
	public void testNewRemoteBranch() throws Exception {
		JGitConfigServerTestData testData = JGitConfigServerTestData
				.prepareClonedGitRepository(TestConfiguration.class);

		Environment environment = testData.getRepository().findOne("bar", "staging",
				"master");
		Object fooProperty = ConfigServerTestUtils.getProperty(environment,
				"bar.properties", "foo");
		assertThat("bar").isEqualTo(fooProperty);

		testData.getServerGit().getGit().branchCreate().setName("testNewRemoteBranch")
				.call();

		testData.getServerGit().getGit().checkout().setName("testNewRemoteBranch").call();

		// update the remote repo
		FileOutputStream out = new FileOutputStream(new File(
				testData.getServerGit().getGitWorkingDirectory(), "/bar.properties"));
		StreamUtils.copy("foo: branchBar", Charset.defaultCharset(), out);
		testData.getServerGit().getGit().add().addFilepattern("bar.properties").call();
		testData.getServerGit().getGit().commit().setMessage("Updated for branch test")
				.call();

		environment = testData.getRepository().findOne("bar", "staging",
				"testNewRemoteBranch");
		fooProperty = ConfigServerTestUtils.getProperty(environment, "bar.properties",
				"foo");
		assertThat("branchBar").isEqualTo(fooProperty);
	}

	@Test
	public void testNewRemoteTag() throws Exception {
		JGitConfigServerTestData testData = JGitConfigServerTestData
				.prepareClonedGitRepository(TestConfiguration.class);

		Git serverGit = testData.getServerGit().getGit();

		Environment environment = testData.getRepository().findOne("bar", "staging",
				"master");
		Object fooProperty = ConfigServerTestUtils.getProperty(environment,
				"bar.properties", "foo");
		assertThat("bar").isEqualTo(fooProperty);

		serverGit.checkout().setName("master").call();

		// create a new tag
		serverGit.tag().setName("testTag").setMessage("Testing a tag").call();

		// update the remote repo
		FileOutputStream out = new FileOutputStream(new File(
				testData.getServerGit().getGitWorkingDirectory(), "/bar.properties"));
		StreamUtils.copy("foo: testAfterTag", Charset.defaultCharset(), out);
		testData.getServerGit().getGit().add().addFilepattern("bar.properties").call();
		testData.getServerGit().getGit().commit().setMessage("Updated for branch test")
				.call();

		environment = testData.getRepository().findOne("bar", "staging", "master");
		fooProperty = ConfigServerTestUtils.getProperty(environment, "bar.properties",
				"foo");
		assertThat("testAfterTag").isEqualTo(fooProperty);

		environment = testData.getRepository().findOne("bar", "staging", "testTag");
		fooProperty = ConfigServerTestUtils.getProperty(environment, "bar.properties",
				"foo");
		assertThat("bar").isEqualTo(fooProperty);

		// now move the tag and test again
		serverGit.tag().setName("testTag").setForceUpdate(true)
				.setMessage("Testing a moved tag").call();

		environment = testData.getRepository().findOne("bar", "staging", "testTag");
		fooProperty = ConfigServerTestUtils.getProperty(environment, "bar.properties",
				"foo");
		assertThat("testAfterTag").isEqualTo(fooProperty);

	}

	@Test
	public void testNewCommitID() throws Exception {
		JGitConfigServerTestData testData = JGitConfigServerTestData
				.prepareClonedGitRepository(TestConfiguration.class);

		// get our starting versions
		String startingRemoteVersion = getCommitID(testData.getServerGit().getGit(),
				"master");

		// make sure we get the right version out of the gate
		Environment environment = testData.getRepository().findOne("bar", "staging",
				"master");
		assertThat(startingRemoteVersion).isEqualTo(environment.getVersion());

		// update the remote repo
		FileOutputStream out = new FileOutputStream(new File(
				testData.getServerGit().getGitWorkingDirectory(), "bar.properties"));
		StreamUtils.copy("foo: barNewCommit", Charset.defaultCharset(), out);
		testData.getServerGit().getGit().add().addFilepattern("bar.properties").call();
		testData.getServerGit().getGit().commit().setMessage("Updated for pull").call();
		String updatedRemoteVersion = getCommitID(testData.getServerGit().getGit(),
				"master");

		// do a normal request and verify we get the new version
		environment = testData.getRepository().findOne("bar", "staging", "master");
		assertThat(updatedRemoteVersion).isEqualTo(environment.getVersion());
		Object fooProperty = ConfigServerTestUtils.getProperty(environment,
				"bar.properties", "foo");
		assertThat("barNewCommit").isEqualTo(fooProperty);

		// request the prior commit ID and make sure we get it
		environment = testData.getRepository().findOne("bar", "staging",
				startingRemoteVersion);
		assertThat(startingRemoteVersion).isEqualTo(environment.getVersion());
		fooProperty = ConfigServerTestUtils.getProperty(environment, "bar.properties",
				"foo");
		assertThat("bar").isEqualTo(fooProperty);
	}

	@Test
	/**
	 * In this scenario there is set the refresh rate so the remote repository is not
	 * fetched for every configuration read.
	 *
	 * There is more than one label queried - test and master - but only one such branch
	 * exists - master.
	 *
	 * There is a new commit to master branch but when client loads new configuration, the
	 * "test" label is queried first.
	 */
	public void testNewCommitIDWithRefreshRate() throws Exception {
		JGitConfigServerTestData testData = JGitConfigServerTestData
				.prepareClonedGitRepository(TestConfiguration.class);

		// get our starting versions
		String startingRemoteVersion = getCommitID(testData.getServerGit().getGit(),
				"master");

		// Ask test label configuration first
		try {
			testData.getRepository().findOne("bar", "staging", "test");
			fail("Should have thrown NoSuchLabelException.");
		}
		catch (NoSuchLabelException ex) {
			// OK
		}

		// make sure we get the right version out of the gate
		Environment environment = testData.getRepository().findOne("bar", "staging",
				"master");
		assertThat(startingRemoteVersion).isEqualTo(environment.getVersion());

		// update the remote repo
		FileOutputStream out = new FileOutputStream(new File(
				testData.getServerGit().getGitWorkingDirectory(), "bar.properties"));
		StreamUtils.copy("foo: barNewCommit", Charset.defaultCharset(), out);
		testData.getServerGit().getGit().add().addFilepattern("bar.properties").call();
		testData.getServerGit().getGit().commit().setMessage("Updated for pull").call();
		String updatedRemoteVersion = getCommitID(testData.getServerGit().getGit(),
				"master");

		// Set refresh rate to 60 seconds (now it will fetch the remote repo only once)
		testData.getRepository().setRefreshRate(60);

		// Ask test label configuration first
		try {
			testData.getRepository().findOne("bar", "staging", "test");
			fail("Should have thrown NoSuchLabelException.");
		}
		catch (NoSuchLabelException ex) {
			// OK
		}

		// do a normal request and verify we get the new version
		environment = testData.getRepository().findOne("bar", "staging", "master");
		assertThat(updatedRemoteVersion).isEqualTo(environment.getVersion());
		Object fooProperty = ConfigServerTestUtils.getProperty(environment,
				"bar.properties", "foo");
		assertThat("barNewCommit").isEqualTo(fooProperty);

		// request the prior commit ID and make sure we get it
		environment = testData.getRepository().findOne("bar", "staging",
				startingRemoteVersion);
		assertThat(startingRemoteVersion).isEqualTo(environment.getVersion());
		fooProperty = ConfigServerTestUtils.getProperty(environment, "bar.properties",
				"foo");
		assertThat("bar").isEqualTo(fooProperty);
	}

	@Test(expected = NoSuchLabelException.class)
	public void testUnknownLabelWithRemote() throws Exception {
		JGitConfigServerTestData testData = JGitConfigServerTestData
				.prepareClonedGitRepository(TestConfiguration.class);
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
		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				.run("--spring.cloud.config.server.git.uri=" + uri,
						"--spring.cloud.config.server.git.passphrase=" + passphrase);
		JGitEnvironmentRepository repository = this.context
				.getBean(JGitEnvironmentRepository.class);
		assertThat(repository.getPassphrase()).contains(passphrase);
	}

	@Test
	public void strictHostKeyChecking() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo("config-repo");
		final boolean strictHostKeyChecking = true;
		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				.run("--spring.cloud.config.server.git.uri=" + uri,
						"--spring.cloud.config.server.git.strict-host-key-checking="
								+ strictHostKeyChecking);
		JGitEnvironmentRepository repository = this.context
				.getBean(JGitEnvironmentRepository.class);
		assertThat(strictHostKeyChecking).isEqualTo(repository.isStrictHostKeyChecking());
	}

	@Test
	public void shouldSetTransportConfigCallback() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo();
		this.context = new SpringApplicationBuilder(
				TestConfigurationWithTransportConfigCallback.class)
						.web(WebApplicationType.NONE)
						.properties("spring.cloud.config.server.git.uri:" + uri).run();

		JGitEnvironmentRepository repository = this.context
				.getBean(JGitEnvironmentRepository.class);
		assertThat(repository.getTransportConfigCallback()).isNotNull();
	}

	@Test
	public void testShouldReturnEnvironmentFromLocalBranchInCaseRemoteDeleted()
			throws Exception {
		JGitConfigServerTestData testData = JGitConfigServerTestData
				.prepareClonedGitRepository(TestConfiguration.class);

		String branchToDelete = "branchToDelete";
		testData.getServerGit().getGit().branchCreate().setName(branchToDelete).call();

		Environment environment = testData.getRepository().findOne("bar", "staging",
				"branchToDelete");
		assertThat(environment).isNotNull();

		testData.getServerGit().getGit().branchDelete().setBranchNames(branchToDelete)
				.call();
		testData.getRepository().findOne("bar", "staging", "branchToDelete");
		assertThat(environment).isNotNull();
	}

	@Test(expected = NoSuchLabelException.class)
	public void testShouldFailIfRemoteBranchWasDeleted() throws Exception {
		JGitConfigServerTestData testData = JGitConfigServerTestData
				.prepareClonedGitRepository(Collections.singleton(
						"spring.cloud.config.server.git.deleteUntrackedBranches=true"),
						TestConfiguration.class);

		String branchToDelete = "branchToDelete";
		testData.getServerGit().getGit().branchCreate().setName(branchToDelete).call();

		// checkout and simulate regular flow
		Environment environment = testData.getRepository().findOne("bar", "staging",
				"branchToDelete");
		assertThat(environment).isNotNull();

		// remove branch
		testData.getServerGit().getGit().branchDelete().setBranchNames(branchToDelete)
				.call();

		// test
		testData.getRepository().findOne("bar", "staging", "branchToDelete");
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(ConfigServerProperties.class)
	@Import({ PropertyPlaceholderAutoConfiguration.class,
			EnvironmentRepositoryConfiguration.class })
	protected static class TestConfiguration {

	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(ConfigServerProperties.class)
	@Import({ PropertyPlaceholderAutoConfiguration.class,
			EnvironmentRepositoryConfiguration.class })
	protected static class TestConfigurationWithTransportConfigCallback {

		@Bean
		public TransportConfigCallback transportConfigCallback() {
			return Mockito.mock(TransportConfigCallback.class);
		}

	}

}
