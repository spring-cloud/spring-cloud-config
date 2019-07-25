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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.DeleteBranchCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.NotMergedException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.attributes.AttributesNodeProvider;
import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.lib.BaseRepositoryBuilder;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.SystemReader;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.support.AwsCodeCommitCredentialProvider;
import org.springframework.cloud.config.server.support.GitSkipSslValidationCredentialsProvider;
import org.springframework.cloud.config.server.support.PassphraseCredentialsProvider;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.core.env.StandardEnvironment;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Dave Syer
 * @author Gareth Clay
 */
public class JGitEnvironmentRepositoryTests {

	@Rule
	public final ExpectedException exception = ExpectedException.none();

	RefDatabase database = Mockito.mock(RefDatabase.class);

	private StandardEnvironment environment = new StandardEnvironment();

	private JGitEnvironmentRepository repository;

	private File basedir = new File("target/config");

	@BeforeClass
	public static void initClass() {
		// mock Git configuration to make tests independent of local Git configuration
		SystemReader.setInstance(new MockSystemReader());
	}

	@Before
	public void init() throws Exception {
		String uri = ConfigServerTestUtils.prepareLocalRepo();
		this.repository = new JGitEnvironmentRepository(this.environment,
				new JGitEnvironmentProperties());
		this.repository.setUri(uri);
		if (this.basedir.exists()) {
			FileUtils.delete(this.basedir, FileUtils.RECURSIVE | FileUtils.RETRY);
		}
	}

	@Test
	public void vanilla() {
		Environment environment = this.repository.findOne("bar", "staging", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(environment.getPropertySources().get(0).getName())
				.isEqualTo(this.repository.getUri() + "/bar.properties");
		assertVersion(environment);
	}

	@Test
	public void nested() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo("another-config-repo");
		this.repository.setUri(uri);
		this.repository.setSearchPaths(new String[] { "sub" });
		Environment environment = this.repository.findOne("bar", "staging", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(environment.getPropertySources().get(0).getName())
				.isEqualTo(this.repository.getUri() + "/sub/application.yml");
		assertVersion(environment);
	}

	@Test
	public void placeholderInSearchPath() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo("another-config-repo");
		this.repository.setUri(uri);
		this.repository.setSearchPaths(new String[] { "{application}" });
		Environment environment = this.repository.findOne("sub", "staging", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getPropertySources().get(0).getName())
				.isEqualTo(this.repository.getUri() + "/sub/application.yml");
		assertVersion(environment);
	}

	private void assertVersion(Environment environment) {
		String version = environment.getVersion();
		assertThat(version).as("version was null").isNotNull();
		assertTrue("version length was wrong",
				version.length() >= 40 && version.length() <= 64);
	}

	@Test
	public void nestedPattern() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo("another-config-repo");
		this.repository.setUri(uri);
		this.repository.setSearchPaths(new String[] { "sub*" });
		Environment environment = this.repository.findOne("bar", "staging", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(environment.getPropertySources().get(0).getName())
				.isEqualTo(this.repository.getUri() + "/sub/application.yml");
		assertVersion(environment);
	}

	@Test
	public void branch() {
		this.repository.setBasedir(this.basedir);
		Environment environment = this.repository.findOne("bar", "staging", "raw");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(environment.getPropertySources().get(0).getName())
				.isEqualTo(this.repository.getUri() + "/bar.properties");
		assertVersion(environment);
	}

	@Test
	public void tag() {
		this.repository.setBasedir(this.basedir);
		Environment environment = this.repository.findOne("bar", "staging", "foo");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(environment.getPropertySources().get(0).getName())
				.isEqualTo(this.repository.getUri() + "/bar.properties");
		assertVersion(environment);
	}

	@Test
	public void basedir() {
		this.repository.setBasedir(this.basedir);
		Environment environment = this.repository.findOne("bar", "staging", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(environment.getPropertySources().get(0).getName())
				.isEqualTo(this.repository.getUri() + "/bar.properties");
		assertVersion(environment);
	}

	@Test
	public void basedirExists() throws Exception {
		assertThat(this.basedir.mkdirs()).isTrue();
		assertThat(new File(this.basedir, ".nothing").createNewFile()).isTrue();
		this.repository.setBasedir(this.basedir);
		Environment environment = this.repository.findOne("bar", "staging", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(environment.getPropertySources().get(0).getName())
				.isEqualTo(this.repository.getUri() + "/bar.properties");
		assertVersion(environment);
	}

	@Test
	public void uriWithHostOnly() throws Exception {
		this.repository.setUri("git://localhost");
		assertThat(this.repository.getUri()).isEqualTo("git://localhost/");
	}

	@Test
	public void uriWithHostAndPath() throws Exception {
		this.repository.setUri("git://localhost/foo/");
		assertThat(this.repository.getUri()).isEqualTo("git://localhost/foo");
	}

	@Test
	public void afterPropertiesSet_CloneOnStartTrue_CloneAndFetchCalled()
			throws Exception {
		Git mockGit = mock(Git.class);
		CloneCommand mockCloneCommand = mock(CloneCommand.class);

		when(mockCloneCommand.setURI(anyString())).thenReturn(mockCloneCommand);
		when(mockCloneCommand.setDirectory(any(File.class))).thenReturn(mockCloneCommand);

		JGitEnvironmentRepository envRepository = new JGitEnvironmentRepository(
				this.environment, new JGitEnvironmentProperties());
		envRepository.setGitFactory(new MockGitFactory(mockGit, mockCloneCommand));
		envRepository.setUri("http://somegitserver/somegitrepo");
		envRepository.setCloneOnStart(true);
		envRepository.afterPropertiesSet();
		verify(mockCloneCommand, times(1)).call();
	}

	@Test
	public void afterPropertiesSet_CloneOnStartFalse_CloneAndFetchNotCalled()
			throws Exception {
		Git mockGit = mock(Git.class);
		CloneCommand mockCloneCommand = mock(CloneCommand.class);

		when(mockCloneCommand.setURI(anyString())).thenReturn(mockCloneCommand);
		when(mockCloneCommand.setDirectory(any(File.class))).thenReturn(mockCloneCommand);

		JGitEnvironmentRepository envRepository = new JGitEnvironmentRepository(
				this.environment, new JGitEnvironmentProperties());
		envRepository.setGitFactory(new MockGitFactory(mockGit, mockCloneCommand));
		envRepository.setUri("http://somegitserver/somegitrepo");
		envRepository.afterPropertiesSet();
		verify(mockCloneCommand, times(0)).call();
		verify(mockGit, times(0)).fetch();
	}

	@Test
	public void afterPropertiesSet_CloneOnStartTrueWithFileURL_CloneAndFetchNotCalled()
			throws Exception {
		Git mockGit = mock(Git.class);
		CloneCommand mockCloneCommand = mock(CloneCommand.class);

		when(mockCloneCommand.setURI(anyString())).thenReturn(mockCloneCommand);
		when(mockCloneCommand.setDirectory(any(File.class))).thenReturn(mockCloneCommand);

		JGitEnvironmentRepository envRepository = new JGitEnvironmentRepository(
				this.environment, new JGitEnvironmentProperties());
		envRepository.setGitFactory(new MockGitFactory(mockGit, mockCloneCommand));
		envRepository.setUri("file://somefilesystem/somegitrepo");
		envRepository.setCloneOnStart(true);
		envRepository.afterPropertiesSet();
		verify(mockCloneCommand, times(0)).call();
		verify(mockGit, times(0)).fetch();
	}

	@Test
	public void shouldPullForcepullNotClean() throws Exception {
		Git git = mock(Git.class);
		StatusCommand statusCommand = mock(StatusCommand.class);
		Status status = mock(Status.class);
		Repository repository = mock(Repository.class);
		StoredConfig storedConfig = mock(StoredConfig.class);

		when(git.status()).thenReturn(statusCommand);
		when(git.getRepository()).thenReturn(repository);
		when(repository.getConfig()).thenReturn(storedConfig);
		when(storedConfig.getString("remote", "origin", "url"))
				.thenReturn("http://example/git");
		when(statusCommand.call()).thenReturn(status);
		when(status.isClean()).thenReturn(false);

		JGitEnvironmentRepository repo = new JGitEnvironmentRepository(this.environment,
				new JGitEnvironmentProperties());
		repo.setForcePull(true);

		boolean shouldPull = repo.shouldPull(git);

		assertThat(shouldPull).as("shouldPull was false").isTrue();
	}

	@Test
	public void shouldPullNotClean() throws Exception {
		Git git = mock(Git.class);
		StatusCommand statusCommand = mock(StatusCommand.class);
		Status status = mock(Status.class);
		Repository repository = mock(Repository.class);
		StoredConfig storedConfig = mock(StoredConfig.class);

		when(git.status()).thenReturn(statusCommand);
		when(git.getRepository()).thenReturn(repository);
		when(repository.getConfig()).thenReturn(storedConfig);
		when(storedConfig.getString("remote", "origin", "url"))
				.thenReturn("http://example/git");
		when(statusCommand.call()).thenReturn(status);
		when(status.isClean()).thenReturn(false);

		JGitEnvironmentRepository repo = new JGitEnvironmentRepository(this.environment,
				new JGitEnvironmentProperties());

		boolean shouldPull = repo.shouldPull(git);

		assertThat(shouldPull).as("shouldPull was true").isFalse();
	}

	@Test
	public void shouldPullClean() throws Exception {
		Git git = mock(Git.class);
		StatusCommand statusCommand = mock(StatusCommand.class);
		Status status = mock(Status.class);
		Repository repository = mock(Repository.class);
		StoredConfig storedConfig = mock(StoredConfig.class);

		when(git.status()).thenReturn(statusCommand);
		when(git.getRepository()).thenReturn(repository);
		when(repository.getConfig()).thenReturn(storedConfig);
		when(storedConfig.getString("remote", "origin", "url"))
				.thenReturn("http://example/git");
		when(statusCommand.call()).thenReturn(status);
		when(status.isClean()).thenReturn(true);

		JGitEnvironmentRepository repo = new JGitEnvironmentRepository(this.environment,
				new JGitEnvironmentProperties());

		boolean shouldPull = repo.shouldPull(git);

		assertThat(shouldPull).as("shouldPull was false").isTrue();
	}

	@Test
	public void shouldNotRefresh() throws Exception {
		Git git = mock(Git.class);
		StatusCommand statusCommand = mock(StatusCommand.class);
		Status status = mock(Status.class);
		Repository repository = mock(Repository.class);
		StoredConfig storedConfig = mock(StoredConfig.class);

		when(git.status()).thenReturn(statusCommand);
		when(git.getRepository()).thenReturn(repository);
		when(repository.getConfig()).thenReturn(storedConfig);
		when(storedConfig.getString("remote", "origin", "url"))
				.thenReturn("http://example/git");
		when(statusCommand.call()).thenReturn(status);
		when(status.isClean()).thenReturn(true);

		JGitEnvironmentProperties properties = new JGitEnvironmentProperties();
		properties.setRefreshRate(2);

		JGitEnvironmentRepository repo = new JGitEnvironmentRepository(this.environment,
				properties);

		repo.setLastRefresh(System.currentTimeMillis() - 5000);

		boolean shouldPull = repo.shouldPull(git);

		assertThat(shouldPull).as("shouldPull was false").isTrue();

		repo.setRefreshRate(30);

		shouldPull = repo.shouldPull(git);

		assertThat(shouldPull).as("shouldPull was true").isFalse();
	}

	@Test
	public void shouldUpdateLastRefresh() throws Exception {
		Git git = mock(Git.class);
		StatusCommand statusCommand = mock(StatusCommand.class);
		Status status = mock(Status.class);
		Repository repository = mock(Repository.class);
		StoredConfig storedConfig = mock(StoredConfig.class);
		FetchCommand fetchCommand = mock(FetchCommand.class);
		FetchResult fetchResult = mock(FetchResult.class);

		when(git.status()).thenReturn(statusCommand);
		when(git.getRepository()).thenReturn(repository);
		when(fetchCommand.call()).thenReturn(fetchResult);
		when(git.fetch()).thenReturn(fetchCommand);
		when(repository.getConfig()).thenReturn(storedConfig);
		when(storedConfig.getString("remote", "origin", "url"))
				.thenReturn("http://example/git");
		when(statusCommand.call()).thenReturn(status);
		when(status.isClean()).thenReturn(true);

		JGitEnvironmentProperties properties = new JGitEnvironmentProperties();
		properties.setRefreshRate(1000);
		JGitEnvironmentRepository repo = new JGitEnvironmentRepository(this.environment,
				properties);

		repo.setLastRefresh(0);
		repo.fetch(git, "master");

		long timeDiff = System.currentTimeMillis() - repo.getLastRefresh();

		assertThat(timeDiff < 1000L)
				.as("time difference (" + timeDiff + ") was longer than 1 second")
				.isTrue();
	}

	@Test
	public void testFetchException() throws Exception {

		Git git = mock(Git.class);
		CloneCommand cloneCommand = mock(CloneCommand.class);
		MockGitFactory factory = new MockGitFactory(git, cloneCommand);
		this.repository.setGitFactory(factory);
		this.repository.setDeleteUntrackedBranches(true);

		// refresh()->shouldPull
		StatusCommand statusCommand = mock(StatusCommand.class);
		Status status = mock(Status.class);
		when(git.status()).thenReturn(statusCommand);

		Repository repository = stubbedRepo();
		when(git.getRepository()).thenReturn(repository);
		StoredConfig storedConfig = mock(StoredConfig.class);
		when(repository.getConfig()).thenReturn(storedConfig);
		when(storedConfig.getString("remote", "origin", "url"))
				.thenReturn("http://example/git");
		when(statusCommand.call()).thenReturn(status);
		when(status.isClean()).thenReturn(true);

		// refresh()->fetch
		FetchCommand fetchCommand = mock(FetchCommand.class);
		when(git.fetch()).thenReturn(fetchCommand);
		when(fetchCommand.setRemote(anyString())).thenReturn(fetchCommand);
		when(fetchCommand.call())
				.thenThrow(new InvalidRemoteException("invalid mock remote")); // here
																				// is
																				// our
																				// exception
																				// we
																				// are
																				// testing

		// refresh()->checkout
		CheckoutCommand checkoutCommand = mock(CheckoutCommand.class);
		// refresh()->checkout->containsBranch
		ListBranchCommand listBranchCommand = mock(ListBranchCommand.class);
		when(git.checkout()).thenReturn(checkoutCommand);
		when(git.branchList()).thenReturn(listBranchCommand);
		List<Ref> refs = new ArrayList<>();
		Ref ref = mock(Ref.class);
		refs.add(ref);
		when(ref.getName()).thenReturn("/master");
		when(listBranchCommand.call()).thenReturn(refs);

		// refresh()->merge
		MergeCommand mergeCommand = mock(MergeCommand.class);
		when(git.merge()).thenReturn(mergeCommand);
		when(mergeCommand.call()).thenThrow(new NotMergedException()); // here
																		// is
																		// our
																		// exception
																		// we
																		// are
																		// testing

		// refresh()->return
		// git.getRepository().findRef("HEAD").getObjectId().getName();
		Ref headRef = mock(Ref.class);
		when(this.database.getRef(anyString())).thenReturn(headRef);

		ObjectId newObjectId = ObjectId.fromRaw(new int[] { 1, 2, 3, 4, 5 });
		when(headRef.getObjectId()).thenReturn(newObjectId);

		SearchPathLocator.Locations locations = this.repository.getLocations("bar",
				"staging", null);
		assertThat(newObjectId.getName()).isEqualTo(locations.getVersion());

		verify(git, times(0)).branchDelete();
	}

	private Repository stubbedRepo() {
		return spy(new Repository(new BaseRepositoryBuilder()) {
			@Override
			public void create(boolean bare) throws IOException {

			}

			@Override
			public ObjectDatabase getObjectDatabase() {
				return null;
			}

			@Override
			public RefDatabase getRefDatabase() {
				return JGitEnvironmentRepositoryTests.this.database;
			}

			@Override
			public StoredConfig getConfig() {
				return null;
			}

			@Override
			public AttributesNodeProvider createAttributesNodeProvider() {
				return null;
			}

			@Override
			public void scanForRepoChanges() throws IOException {

			}

			@Override
			public void notifyIndexChanged(boolean internal) {

			}

			@Override
			public ReflogReader getReflogReader(String refName) throws IOException {
				return null;
			}
		});
	}

	@Test
	public void testMergeException() throws Exception {

		Git git = mock(Git.class);
		CloneCommand cloneCommand = mock(CloneCommand.class);
		MockGitFactory factory = new MockGitFactory(git, cloneCommand);
		this.repository.setGitFactory(factory);

		// refresh()->shouldPull
		StatusCommand statusCommand = mock(StatusCommand.class);
		Status status = mock(Status.class);
		when(git.status()).thenReturn(statusCommand);
		Repository repository = stubbedRepo();
		when(git.getRepository()).thenReturn(repository);
		StoredConfig storedConfig = mock(StoredConfig.class);
		when(repository.getConfig()).thenReturn(storedConfig);
		when(storedConfig.getString("remote", "origin", "url"))
				.thenReturn("http://example/git");
		when(statusCommand.call()).thenReturn(status);
		when(status.isClean()).thenReturn(true);

		// refresh()->fetch
		FetchCommand fetchCommand = mock(FetchCommand.class);
		FetchResult fetchResult = mock(FetchResult.class);
		when(git.fetch()).thenReturn(fetchCommand);
		when(fetchCommand.setRemote(anyString())).thenReturn(fetchCommand);
		when(fetchCommand.call()).thenReturn(fetchResult);
		when(fetchResult.getTrackingRefUpdates())
				.thenReturn(Collections.<TrackingRefUpdate>emptyList());

		// refresh()->checkout
		CheckoutCommand checkoutCommand = mock(CheckoutCommand.class);
		// refresh()->checkout->containsBranch
		ListBranchCommand listBranchCommand = mock(ListBranchCommand.class);
		when(git.checkout()).thenReturn(checkoutCommand);
		when(git.branchList()).thenReturn(listBranchCommand);
		List<Ref> refs = new ArrayList<>();
		Ref ref = mock(Ref.class);
		refs.add(ref);
		when(ref.getName()).thenReturn("/master");
		when(listBranchCommand.call()).thenReturn(refs);

		// refresh()->merge
		MergeCommand mergeCommand = mock(MergeCommand.class);
		when(git.merge()).thenReturn(mergeCommand);
		when(mergeCommand.call()).thenThrow(new NotMergedException()); // here is our
																		// exception we
																		// are testing

		// refresh()->return git.getRepository().findRef("HEAD").getObjectId().getName();
		Ref headRef = mock(Ref.class);
		when(this.database.getRef(anyString())).thenReturn(headRef);

		ObjectId newObjectId = ObjectId.fromRaw(new int[] { 1, 2, 3, 4, 5 });
		when(headRef.getObjectId()).thenReturn(newObjectId);

		SearchPathLocator.Locations locations = this.repository.getLocations("bar",
				"staging", "master");
		assertThat(newObjectId.getName()).isEqualTo(locations.getVersion());

		verify(git, times(0)).branchDelete();
	}

	@Test
	public void testRefreshWithoutFetch() throws Exception {
		Git git = mock(Git.class);

		CloneCommand cloneCommand = mock(CloneCommand.class);
		when(cloneCommand.setURI(anyString())).thenReturn(cloneCommand);
		when(cloneCommand.setDirectory(any(File.class))).thenReturn(cloneCommand);
		when(cloneCommand.call()).thenReturn(git);

		MockGitFactory factory = new MockGitFactory(git, cloneCommand);

		StatusCommand statusCommand = mock(StatusCommand.class);
		CheckoutCommand checkoutCommand = mock(CheckoutCommand.class);
		Status status = mock(Status.class);
		Repository repository = mock(Repository.class, Mockito.RETURNS_DEEP_STUBS);
		StoredConfig storedConfig = mock(StoredConfig.class);
		Ref ref = mock(Ref.class);
		ListBranchCommand listBranchCommand = mock(ListBranchCommand.class);
		FetchCommand fetchCommand = mock(FetchCommand.class);
		FetchResult fetchResult = mock(FetchResult.class);
		Ref branch1Ref = mock(Ref.class);

		when(git.branchList()).thenReturn(listBranchCommand);
		when(git.status()).thenReturn(statusCommand);
		when(git.getRepository()).thenReturn(repository);
		when(git.checkout()).thenReturn(checkoutCommand);
		when(git.fetch()).thenReturn(fetchCommand);
		when(git.merge())
				.thenReturn(mock(MergeCommand.class, Mockito.RETURNS_DEEP_STUBS));
		when(repository.getConfig()).thenReturn(storedConfig);
		when(storedConfig.getString("remote", "origin", "url"))
				.thenReturn("http://example/git");
		when(statusCommand.call()).thenReturn(status);
		when(checkoutCommand.call()).thenReturn(ref);
		when(listBranchCommand.call()).thenReturn(Arrays.asList(branch1Ref));
		when(fetchCommand.call()).thenReturn(fetchResult);
		when(branch1Ref.getName()).thenReturn("origin/master");
		when(status.isClean()).thenReturn(true);

		JGitEnvironmentRepository repo = new JGitEnvironmentRepository(this.environment,
				new JGitEnvironmentProperties());
		repo.setGitFactory(factory);
		repo.setUri("http://somegitserver/somegitrepo");
		repo.setBasedir(this.basedir);

		// Set the refresh rate to 2 seconds and last update before 100ms. There should be
		// no remote repo fetch.
		repo.setLastRefresh(System.currentTimeMillis() - 100);
		repo.setRefreshRate(2);

		repo.refresh("master");

		// Verify no fetch but merge only.
		verify(git, times(0)).fetch();
		verify(git).merge();
	}

	@Test
	public void testResetHardException() throws Exception {

		Git git = mock(Git.class);
		CloneCommand cloneCommand = mock(CloneCommand.class);
		MockGitFactory factory = new MockGitFactory(git, cloneCommand);
		this.repository.setGitFactory(factory);

		// refresh()->shouldPull
		StatusCommand statusCommand = mock(StatusCommand.class);
		Status status = mock(Status.class);
		when(git.status()).thenReturn(statusCommand);
		Repository repository = stubbedRepo();
		when(git.getRepository()).thenReturn(repository);
		StoredConfig storedConfig = mock(StoredConfig.class);
		when(repository.getConfig()).thenReturn(storedConfig);
		when(storedConfig.getString("remote", "origin", "url"))
				.thenReturn("http://example/git");
		when(statusCommand.call()).thenReturn(status);
		when(status.isClean()).thenReturn(true).thenReturn(false);

		// refresh()->fetch
		FetchCommand fetchCommand = mock(FetchCommand.class);
		FetchResult fetchResult = mock(FetchResult.class);
		when(git.fetch()).thenReturn(fetchCommand);
		when(fetchCommand.setRemote(anyString())).thenReturn(fetchCommand);
		when(fetchCommand.call()).thenReturn(fetchResult);
		when(fetchResult.getTrackingRefUpdates())
				.thenReturn(Collections.<TrackingRefUpdate>emptyList());

		// refresh()->checkout
		CheckoutCommand checkoutCommand = mock(CheckoutCommand.class);
		// refresh()->checkout->containsBranch
		ListBranchCommand listBranchCommand = mock(ListBranchCommand.class);
		when(git.checkout()).thenReturn(checkoutCommand);
		when(git.branchList()).thenReturn(listBranchCommand);
		List<Ref> refs = new ArrayList<>();
		Ref ref = mock(Ref.class);
		refs.add(ref);
		when(ref.getName()).thenReturn("/master");
		when(listBranchCommand.call()).thenReturn(refs);

		// refresh()->merge
		MergeCommand mergeCommand = mock(MergeCommand.class);
		when(git.merge()).thenReturn(mergeCommand);
		when(mergeCommand.call()).thenThrow(new NotMergedException()); // here
																		// is
																		// our
																		// exception
																		// we
																		// are
																		// testing

		// refresh()->hardReset
		ResetCommand resetCommand = mock(ResetCommand.class);
		when(git.reset()).thenReturn(resetCommand);
		when(resetCommand.call()).thenReturn(ref);

		// refresh()->return
		// git.getRepository().findRef("HEAD").getObjectId().getName();
		Ref headRef = mock(Ref.class);
		when(this.database.getRef(anyString())).thenReturn(headRef);

		ObjectId newObjectId = ObjectId.fromRaw(new int[] { 1, 2, 3, 4, 5 });
		when(headRef.getObjectId()).thenReturn(newObjectId);

		SearchPathLocator.Locations locations = this.repository.getLocations("bar",
				"staging", "master");
		assertThat(newObjectId.getName()).isEqualTo(locations.getVersion());

		verify(git, times(0)).branchDelete();
	}

	@Test
	public void shouldDeleteBaseDirWhenCloneFails() throws Exception {
		Git mockGit = mock(Git.class);
		CloneCommand mockCloneCommand = mock(CloneCommand.class);

		when(mockCloneCommand.setURI(anyString())).thenReturn(mockCloneCommand);
		when(mockCloneCommand.setDirectory(any(File.class))).thenReturn(mockCloneCommand);
		when(mockCloneCommand.call())
				.thenThrow(new TransportException("failed to clone"));

		JGitEnvironmentRepository envRepository = new JGitEnvironmentRepository(
				this.environment, new JGitEnvironmentProperties());
		envRepository.setGitFactory(new MockGitFactory(mockGit, mockCloneCommand));
		envRepository.setUri("http://somegitserver/somegitrepo");
		envRepository.setBasedir(this.basedir);

		try {
			envRepository.findOne("bar", "staging", "master");
		}
		catch (Exception ex) {
			// expected - ignore
		}

		assertThat(this.basedir.listFiles().length > 0)
				.as("baseDir should be deleted when clone fails").isFalse();
	}

	@Test
	public void usernamePasswordShouldSetCredentials() throws Exception {
		Git mockGit = mock(Git.class);
		MockCloneCommand mockCloneCommand = new MockCloneCommand(mockGit);

		JGitEnvironmentRepository envRepository = new JGitEnvironmentRepository(
				this.environment, new JGitEnvironmentProperties());
		envRepository.setGitFactory(new MockGitFactory(mockGit, mockCloneCommand));
		envRepository.setUri("git+ssh://git@somegitserver/somegitrepo");
		envRepository.setBasedir(new File("./mybasedir"));
		final String username = "someuser";
		final String password = "mypassword";
		envRepository.setUsername(username);
		envRepository.setPassword(password);
		envRepository.setCloneOnStart(true);
		envRepository.afterPropertiesSet();

		assertTrue(mockCloneCommand
				.getCredentialsProvider() instanceof UsernamePasswordCredentialsProvider);

		CredentialsProvider provider = mockCloneCommand.getCredentialsProvider();
		CredentialItem.Username usernameCredential = new CredentialItem.Username();
		CredentialItem.Password passwordCredential = new CredentialItem.Password();
		assertThat(provider.supports(usernameCredential)).isTrue();
		assertThat(provider.supports(passwordCredential)).isTrue();

		provider.get(new URIish(), usernameCredential);
		assertThat(username).isEqualTo(usernameCredential.getValue());
		provider.get(new URIish(), passwordCredential);
		assertThat(password).isEqualTo(String.valueOf(passwordCredential.getValue()));
	}

	@Test
	public void passphraseShouldSetCredentials() throws Exception {
		final String passphrase = "mypassphrase";
		Git mockGit = mock(Git.class);
		MockCloneCommand mockCloneCommand = new MockCloneCommand(mockGit);

		JGitEnvironmentRepository envRepository = new JGitEnvironmentRepository(
				this.environment, new JGitEnvironmentProperties());
		envRepository.setGitFactory(new MockGitFactory(mockGit, mockCloneCommand));
		envRepository.setUri("git+ssh://git@somegitserver/somegitrepo");
		envRepository.setBasedir(new File("./mybasedir"));
		envRepository.setPassphrase(passphrase);
		envRepository.setCloneOnStart(true);
		envRepository.afterPropertiesSet();

		assertThat(mockCloneCommand.hasPassphraseCredentialsProvider()).isTrue();

		CredentialsProvider provider = mockCloneCommand.getCredentialsProvider();
		assertThat(provider.isInteractive()).isFalse();

		CredentialItem.StringType stringCredential = new CredentialItem.StringType(
				PassphraseCredentialsProvider.PROMPT, true);

		assertThat(provider.supports(stringCredential)).isTrue();
		provider.get(new URIish(), stringCredential);
		assertThat(passphrase).isEqualTo(stringCredential.getValue());
	}

	@Test
	public void gitCredentialsProviderFactoryCreatesPassphraseProvider()
			throws Exception {
		final String passphrase = "mypassphrase";
		final String gitUri = "git+ssh://git@somegitserver/somegitrepo";
		Git mockGit = mock(Git.class);
		MockCloneCommand mockCloneCommand = new MockCloneCommand(mockGit);

		JGitEnvironmentRepository envRepository = new JGitEnvironmentRepository(
				this.environment, new JGitEnvironmentProperties());
		envRepository.setGitFactory(new MockGitFactory(mockGit, mockCloneCommand));
		envRepository.setUri(gitUri);
		envRepository.setBasedir(new File("./mybasedir"));
		envRepository.setPassphrase(passphrase);
		envRepository.setCloneOnStart(true);
		envRepository.afterPropertiesSet();

		assertThat(mockCloneCommand.hasPassphraseCredentialsProvider()).isTrue();

		CredentialsProvider provider = mockCloneCommand.getCredentialsProvider();
		assertThat(provider.isInteractive()).isFalse();

		CredentialItem.StringType stringCredential = new CredentialItem.StringType(
				PassphraseCredentialsProvider.PROMPT, true);

		assertThat(provider.supports(stringCredential)).isTrue();
		provider.get(new URIish(), stringCredential);
		assertThat(passphrase).isEqualTo(stringCredential.getValue());

	}

	@Test
	public void gitCredentialsProviderFactoryCreatesUsernamePasswordProvider()
			throws Exception {
		Git mockGit = mock(Git.class);
		MockCloneCommand mockCloneCommand = new MockCloneCommand(mockGit);
		final String username = "someuser";
		final String password = "mypassword";

		JGitEnvironmentRepository envRepository = new JGitEnvironmentRepository(
				this.environment, new JGitEnvironmentProperties());
		envRepository.setGitFactory(new MockGitFactory(mockGit, mockCloneCommand));
		envRepository.setUri("git+ssh://git@somegitserver/somegitrepo");
		envRepository.setBasedir(new File("./mybasedir"));
		envRepository.setUsername(username);
		envRepository.setPassword(password);
		envRepository.setCloneOnStart(true);
		envRepository.afterPropertiesSet();

		assertTrue(mockCloneCommand
				.getCredentialsProvider() instanceof UsernamePasswordCredentialsProvider);

		CredentialsProvider provider = mockCloneCommand.getCredentialsProvider();
		CredentialItem.Username usernameCredential = new CredentialItem.Username();
		CredentialItem.Password passwordCredential = new CredentialItem.Password();
		assertThat(provider.supports(usernameCredential)).isTrue();
		assertThat(provider.supports(passwordCredential)).isTrue();

		provider.get(new URIish(), usernameCredential);
		assertThat(username).isEqualTo(usernameCredential.getValue());
		provider.get(new URIish(), passwordCredential);
		assertThat(password).isEqualTo(String.valueOf(passwordCredential.getValue()));
	}

	@Test
	public void gitCredentialsProviderFactoryCreatesAwsCodeCommitProvider()
			throws Exception {
		Git mockGit = mock(Git.class);
		MockCloneCommand mockCloneCommand = new MockCloneCommand(mockGit);
		final String awsUri = "https://git-codecommit.us-east-1.amazonaws.com/v1/repos/test";

		JGitEnvironmentRepository envRepository = new JGitEnvironmentRepository(
				this.environment, new JGitEnvironmentProperties());
		envRepository.setGitFactory(new MockGitFactory(mockGit, mockCloneCommand));
		envRepository.setUri(awsUri);
		envRepository.setCloneOnStart(true);
		envRepository.afterPropertiesSet();

		assertTrue(mockCloneCommand
				.getCredentialsProvider() instanceof AwsCodeCommitCredentialProvider);
	}

	@Test
	public void gitCredentialsProviderFactoryCreatesSkipSslValidationProvider()
			throws Exception {
		Git mockGit = mock(Git.class);
		MockCloneCommand mockCloneCommand = new MockCloneCommand(mockGit);
		final String username = "someuser";
		final String password = "mypassword";

		JGitEnvironmentRepository envRepository = new JGitEnvironmentRepository(
				this.environment, new JGitEnvironmentProperties());
		envRepository.setGitFactory(new MockGitFactory(mockGit, mockCloneCommand));
		envRepository.setUri("https://somegitserver/somegitrepo");
		envRepository.setBasedir(new File("./mybasedir"));
		envRepository.setUsername(username);
		envRepository.setPassword(password);
		envRepository.setSkipSslValidation(true);
		envRepository.setCloneOnStart(true);
		envRepository.afterPropertiesSet();

		CredentialsProvider provider = mockCloneCommand.getCredentialsProvider();

		assertThat(provider instanceof GitSkipSslValidationCredentialsProvider).isTrue();

		CredentialItem.Username usernameCredential = new CredentialItem.Username();
		CredentialItem.Password passwordCredential = new CredentialItem.Password();
		assertThat(provider.supports(usernameCredential)).isTrue();
		assertThat(provider.supports(passwordCredential)).isTrue();

		provider.get(new URIish(), usernameCredential);
		assertThat(username).isEqualTo(usernameCredential.getValue());
		provider.get(new URIish(), passwordCredential);
		assertThat(password).isEqualTo(String.valueOf(passwordCredential.getValue()));
	}

	@Test
	public void shouldPrintStacktraceIfDebugEnabled() throws Exception {
		final Log mockLogger = mock(Log.class);
		JGitEnvironmentRepository envRepository = new JGitEnvironmentRepository(
				this.environment, new JGitEnvironmentProperties()) {
			@Override
			public void afterPropertiesSet() throws Exception {
				this.logger = mockLogger;
			}
		};
		envRepository.afterPropertiesSet();
		when(mockLogger.isDebugEnabled()).thenReturn(true);

		envRepository.warn("", new RuntimeException());

		verify(mockLogger).warn(eq(""));
		verify(mockLogger).debug(eq("Stacktrace for: "), any(RuntimeException.class));

		int numberOfInvocations = mockingDetails(mockLogger).getInvocations().size();
		assertThat(numberOfInvocations).as("should call isDebugEnabled warn and debug")
				.isEqualTo(3);
	}

	@Test
	public void shouldSetTransportConfigCallbackOnCloneAndFetch() throws Exception {
		Git mockGit = mock(Git.class);
		FetchCommand fetchCommand = mock(FetchCommand.class);
		when(mockGit.fetch()).thenReturn(fetchCommand);
		when(fetchCommand.call()).thenReturn(mock(FetchResult.class));

		CloneCommand mockCloneCommand = mock(CloneCommand.class);
		when(mockCloneCommand.setURI(anyString())).thenReturn(mockCloneCommand);
		when(mockCloneCommand.setDirectory(any(File.class))).thenReturn(mockCloneCommand);

		TransportConfigCallback configCallback = mock(TransportConfigCallback.class);
		JGitEnvironmentRepository envRepository = new JGitEnvironmentRepository(
				this.environment, new JGitEnvironmentProperties());
		envRepository.setGitFactory(new MockGitFactory(mockGit, mockCloneCommand));
		envRepository.setUri("http://somegitserver/somegitrepo");
		envRepository.setTransportConfigCallback(configCallback);
		envRepository.setCloneOnStart(true);

		envRepository.afterPropertiesSet();
		verify(mockCloneCommand, times(1)).setTransportConfigCallback(configCallback);

		envRepository.fetch(mockGit, "master");
		verify(fetchCommand, times(1)).setTransportConfigCallback(configCallback);
	}

	@Test
	public void shouldSetRemoveBranchesFlagToFetchCommand() throws Exception {
		Git mockGit = mock(Git.class);
		FetchCommand fetchCommand = mock(FetchCommand.class);

		when(mockGit.fetch()).thenReturn(fetchCommand);
		when(fetchCommand.call()).thenReturn(mock(FetchResult.class));

		JGitEnvironmentRepository envRepository = new JGitEnvironmentRepository(
				this.environment, new JGitEnvironmentProperties());
		envRepository
				.setGitFactory(new MockGitFactory(mockGit, mock(CloneCommand.class)));
		envRepository.setUri("http://somegitserver/somegitrepo");
		envRepository.setDeleteUntrackedBranches(true);

		envRepository.fetch(mockGit, "master");

		verify(fetchCommand, times(1)).setRemoveDeletedRefs(true);
		verify(fetchCommand, times(1)).call();
	}

	@Test
	public void shouldHandleExceptionWhileRemovingBranches() throws Exception {
		Git git = mock(Git.class);
		CloneCommand cloneCommand = mock(CloneCommand.class);
		MockGitFactory factory = new MockGitFactory(git, cloneCommand);
		this.repository.setGitFactory(factory);
		this.repository.setDeleteUntrackedBranches(true);

		// refresh()->shouldPull
		StatusCommand statusCommand = mock(StatusCommand.class);
		Status status = mock(Status.class);
		when(git.status()).thenReturn(statusCommand);
		Repository repository = stubbedRepo();
		when(git.getRepository()).thenReturn(repository);
		StoredConfig storedConfig = mock(StoredConfig.class);
		when(repository.getConfig()).thenReturn(storedConfig);
		when(storedConfig.getString("remote", "origin", "url"))
				.thenReturn("http://example/git");
		when(statusCommand.call()).thenReturn(status);
		when(status.isClean()).thenReturn(true);

		// refresh()->fetch
		FetchCommand fetchCommand = mock(FetchCommand.class);
		FetchResult fetchResult = mock(FetchResult.class);

		TrackingRefUpdate trackingRefUpdate = mock(TrackingRefUpdate.class);
		Collection<TrackingRefUpdate> trackingRefUpdates = Collections
				.singletonList(trackingRefUpdate);

		when(git.fetch()).thenReturn(fetchCommand);
		when(fetchCommand.setRemote(anyString())).thenReturn(fetchCommand);
		when(fetchCommand.call()).thenReturn(fetchResult);
		when(fetchResult.getTrackingRefUpdates()).thenReturn(trackingRefUpdates);

		// refresh()->deleteBranch
		ReceiveCommand receiveCommand = mock(ReceiveCommand.class);
		when(trackingRefUpdate.asReceiveCommand()).thenReturn(receiveCommand);
		when(receiveCommand.getType()).thenReturn(ReceiveCommand.Type.DELETE);
		when(trackingRefUpdate.getLocalName())
				.thenReturn("refs/remotes/origin/feature/deletedBranchFromOrigin");

		DeleteBranchCommand deleteBranchCommand = mock(DeleteBranchCommand.class);
		when(git.branchDelete()).thenReturn(deleteBranchCommand);
		when(deleteBranchCommand.setBranchNames(eq("feature/deletedBranchFromOrigin")))
				.thenReturn(deleteBranchCommand);
		when(deleteBranchCommand.setForce(true)).thenReturn(deleteBranchCommand);
		when(deleteBranchCommand.call()).thenThrow(new NotMergedException()); // here
																				// is
																				// our
																				// exception
																				// we
																				// are
																				// testing

		// refresh()->checkout
		CheckoutCommand checkoutCommand = mock(CheckoutCommand.class);
		// refresh()->checkout->containsBranch
		ListBranchCommand listBranchCommand = mock(ListBranchCommand.class);
		when(git.checkout()).thenReturn(checkoutCommand);
		when(git.branchList()).thenReturn(listBranchCommand);
		List<Ref> refs = new ArrayList<>();
		Ref ref = mock(Ref.class);
		refs.add(ref);
		when(ref.getName()).thenReturn("/master");
		when(listBranchCommand.call()).thenReturn(refs);

		// refresh()->merge
		MergeResult mergeResult = mock(MergeResult.class);
		MergeResult.MergeStatus mergeStatus = mock(MergeResult.MergeStatus.class);
		MergeCommand mergeCommand = mock(MergeCommand.class);
		when(git.merge()).thenReturn(mergeCommand);
		when(mergeCommand.call()).thenReturn(mergeResult);
		when(mergeResult.getMergeStatus()).thenReturn(mergeStatus);
		when(mergeStatus.isSuccessful()).thenReturn(true);

		// refresh()->return
		// git.getRepository().findRef("HEAD").getObjectId().getName();
		Ref headRef = mock(Ref.class);
		when(this.database.getRef(anyString())).thenReturn(headRef);

		ObjectId newObjectId = ObjectId.fromRaw(new int[] { 1, 2, 3, 4, 5 });
		when(headRef.getObjectId()).thenReturn(newObjectId);

		SearchPathLocator.Locations locations = this.repository.getLocations("bar",
				"staging", "master");
		assertThat(newObjectId.getName()).isEqualTo(locations.getVersion());

		verify(deleteBranchCommand).setBranchNames(eq("feature/deletedBranchFromOrigin"));
		verify(deleteBranchCommand).setForce(true);
		verify(deleteBranchCommand).call();
	}

	class MockCloneCommand extends CloneCommand {

		private Git mockGit;

		MockCloneCommand(Git mockGit) {
			this.mockGit = mockGit;
		}

		@Override
		public Git call() throws GitAPIException, InvalidRemoteException {
			return this.mockGit;
		}

		public boolean hasPassphraseCredentialsProvider() {
			return this.credentialsProvider instanceof PassphraseCredentialsProvider;
		}

		public CredentialsProvider getCredentialsProvider() {
			return this.credentialsProvider;
		}

	}

	class MockGitFactory extends JGitEnvironmentRepository.JGitFactory {

		private Git mockGit;

		private CloneCommand mockCloneCommand;

		MockGitFactory(Git mockGit, CloneCommand mockCloneCommand) {
			this.mockGit = mockGit;
			this.mockCloneCommand = mockCloneCommand;
		}

		@Override
		public Git getGitByOpen(File file) throws IOException {
			return this.mockGit;
		}

		@Override
		public CloneCommand getCloneCommandByCloneRepository() {
			return this.mockCloneCommand;
		}

	}

}
