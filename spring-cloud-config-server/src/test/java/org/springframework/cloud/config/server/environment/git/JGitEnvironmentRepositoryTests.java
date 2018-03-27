/*
 * Copyright 2013-2018 the original author or authors.
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

package org.springframework.cloud.config.server.environment.git;

import java.io.File;
import java.util.Collection;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.cloud.config.server.environment.NoSuchRepositoryException;
import org.springframework.cloud.config.server.environment.SearchPathLocator;
import org.springframework.cloud.config.server.environment.git.command.JGitCommandConfigurer;
import org.springframework.cloud.config.server.environment.git.command.JGitCommandExecutor;
import org.springframework.cloud.config.server.environment.git.command.JGitFactory;
import org.springframework.cloud.config.server.support.AwsCodeCommitCredentialProvider;
import org.springframework.cloud.config.server.support.GitCredentialsProviderFactory;
import org.springframework.cloud.config.server.support.PassphraseCredentialsProvider;
import org.springframework.core.env.StandardEnvironment;
import static java.util.Collections.singletonList;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Dave Syer
 */
public class JGitEnvironmentRepositoryTests {

	private static final String URI = "http://somegitserver/somegitrepo";
	private Git gitMock;
	private CloneCommand cloneCommandMock;
	private JGitCommandConfigurer jGitCommandConfigurer;
	private JGitCommandExecutor jGitCommandExecutor;
	private StandardEnvironment environment = new StandardEnvironment();
	private JGitEnvironmentRepository repository;

	private File basedir = new File("target/config");

	@Rule
	public final ExpectedException exception = ExpectedException.none();

	@Before
	public void init() throws Exception {
		this.repository = new JGitEnvironmentRepository(this.environment, new JGitEnvironmentProperties());
		this.repository.setUri(URI);
		if (this.basedir.exists()) {
			FileUtils.delete(this.basedir, FileUtils.RECURSIVE | FileUtils.RETRY);
		}
		repository.setBasedir(this.basedir);

		jGitCommandConfigurer = mock(JGitCommandConfigurer.class);
		jGitCommandExecutor = mock(JGitCommandExecutor.class);
		repository.jGitCommandConfigurer = jGitCommandConfigurer;
		repository.jGitCommandExecutor = jGitCommandExecutor;

		cloneCommandMock = mock(CloneCommand.class);
		gitMock = mock(Git.class);
		MockGitFactory factory = new MockGitFactory(gitMock, cloneCommandMock);
		this.repository.setGitFactory(factory);
	}

	@Test
	public void basedirExists() throws Exception {
		assertTrue(this.basedir.mkdirs());
		assertTrue(new File(this.basedir, ".nothing").createNewFile());

		repository.setBasedir(this.basedir);
		repository.setCloneOnStart(true);

		when(cloneCommandMock.setURI(anyString())).thenReturn(cloneCommandMock);
		when(cloneCommandMock.setDirectory(any(File.class))).thenReturn(cloneCommandMock);
		when(cloneCommandMock.call()).thenReturn(gitMock);

		repository.afterPropertiesSet();

		assertFalse(new File(this.basedir, ".nothing").exists());
		verify(gitMock, times(2)).close();
	}

	@Test
	public void uriWithHostOnly() {
		this.repository.setUri("git://localhost");
		assertEquals("git://localhost/", this.repository.getUri());
	}

	@Test
	public void uriWithHostAndPath() {
		this.repository.setUri("git://localhost/foo/");
		assertEquals("git://localhost/foo", this.repository.getUri());
	}

	@Test
	public void afterPropertiesSet_CloneOnStartTrue_CloneAndFetchCalled() throws Exception {
		when(cloneCommandMock.setURI(anyString())).thenReturn(cloneCommandMock);
		when(cloneCommandMock.setDirectory(any(File.class))).thenReturn(cloneCommandMock);

		repository.setUri("http://somegitserver/somegitrepo");
		repository.setCloneOnStart(true);
		repository.afterPropertiesSet();
		verify(cloneCommandMock, times(1)).call();
	}

	@Test
	public void afterPropertiesSet_CloneOnStartFalse_CloneAndFetchNotCalled() throws Exception {
		when(cloneCommandMock.setURI(anyString())).thenReturn(cloneCommandMock);
		when(cloneCommandMock.setDirectory(any(File.class))).thenReturn(cloneCommandMock);

		repository.setGitFactory(new MockGitFactory(gitMock, cloneCommandMock));
		repository.setUri("http://somegitserver/somegitrepo");
		repository.afterPropertiesSet();
		verify(cloneCommandMock, times(0)).call();
		verify(gitMock, times(0)).fetch();
	}

	@Test
	public void afterPropertiesSet_CloneOnStartTrueWithFileURL_CloneAndFetchNotCalled() throws Exception {
		when(cloneCommandMock.setURI(anyString())).thenReturn(cloneCommandMock);
		when(cloneCommandMock.setDirectory(any(File.class))).thenReturn(cloneCommandMock);

		repository.setUri("file://somefilesystem/somegitrepo");
		repository.setCloneOnStart(true);
		repository.afterPropertiesSet();
		verify(cloneCommandMock, times(0)).call();
		verify(gitMock, times(0)).fetch();
	}

	@Test
	public void shouldPullForcepullNotClean() throws Exception {
		Status status = mock(Status.class);
		Repository gitRepository = mock(Repository.class);
		StoredConfig storedConfig = mock(StoredConfig.class);

		when(gitMock.getRepository()).thenReturn(gitRepository);
		when(gitRepository.getConfig()).thenReturn(storedConfig);
		when(storedConfig.getString("remote", "origin", "url")).thenReturn("http://example/git");
		when(jGitCommandExecutor.status(gitMock)).thenReturn(status);
		when(status.isClean()).thenReturn(false);

		repository.setForcePull(true);

		//test
		boolean shouldPull = repository.shouldPull(gitMock);

		assertThat("shouldPull was false", shouldPull, is(true));
	}

	@Test
	public void shouldPullNotClean() throws Exception {
		Status status = mock(Status.class);
		Repository gitRepository = mock(Repository.class);
		StoredConfig storedConfig = mock(StoredConfig.class);

		when(gitMock.getRepository()).thenReturn(gitRepository);
		when(gitRepository.getConfig()).thenReturn(storedConfig);
		when(storedConfig.getString("remote", "origin", "url")).thenReturn("http://example/git");
		when(jGitCommandExecutor.status(gitMock)).thenReturn(status);
		when(status.isClean()).thenReturn(false);

		boolean shouldPull = repository.shouldPull(gitMock);

		assertThat("shouldPull was true", shouldPull, is(false));
	}

	@Test
	public void shouldPullClean() throws Exception {
		Status status = mock(Status.class);
		Repository gitRepository = mock(Repository.class);
		StoredConfig storedConfig = mock(StoredConfig.class);

		when(gitMock.getRepository()).thenReturn(gitRepository);
		when(gitRepository.getConfig()).thenReturn(storedConfig);
		when(storedConfig.getString("remote", "origin", "url")).thenReturn("http://example/git");
		when(jGitCommandExecutor.status(gitMock)).thenReturn(status);
		when(status.isClean()).thenReturn(true);

		boolean shouldPull = repository.shouldPull(gitMock);

		assertThat("shouldPull was false", shouldPull, is(true));
	}

	@Test
	public void shouldDeleteBaseDirWhenCloneFails() throws Exception {
		when(cloneCommandMock.setURI(URI)).thenReturn(cloneCommandMock);
		when(cloneCommandMock.setDirectory(any(File.class))).thenReturn(cloneCommandMock);
		when(cloneCommandMock.call()).thenThrow(new TransportException("failed to clone"));

		try {
			repository.findOne("bar", "staging", "master");
		} catch (Exception ex) {
			assertThat(ex, is(instanceOf(NoSuchRepositoryException.class)));
		}

		assertFalse("baseDir should be deleted when clone fails", this.basedir.listFiles().length > 0);
		verify(jGitCommandConfigurer).configureCommand(cloneCommandMock);
	}

	@Test
	public void shouldCreateProperCommandConfigurerWithUserPasswordCredProvider() throws Exception {
		TransportConfigCallback transportConfigCallback = mock(TransportConfigCallback.class);
		MockCloneCommand mockCloneCommand = new MockCloneCommand(gitMock);
		JGitEnvironmentRepository envRepository = new JGitEnvironmentRepository(this.environment,
				new JGitEnvironmentProperties());
		envRepository.setGitFactory(new MockGitFactory(gitMock, mockCloneCommand));
		envRepository.setUri("git+ssh://git@somegitserver/somegitrepo");
		envRepository.setBasedir(new File("./mybasedir"));
		final String username = "someuser";
		final String password = "mypassword";
		envRepository.setUsername(username);
		envRepository.setPassword(password);
		envRepository.setCloneOnStart(true);
		envRepository.setTransportConfigCallback(transportConfigCallback);
		envRepository.afterPropertiesSet();

		JGitCommandConfigurer jGitCommandConfigurer = envRepository.jGitCommandConfigurer;

		//check credential provider
		assertTrue(mockCloneCommand.getCredentialsProvider() instanceof UsernamePasswordCredentialsProvider);
		CredentialsProvider credentialsProvider = jGitCommandConfigurer.getCredentialsProvider();
		assertThat(credentialsProvider, is(instanceOf(UsernamePasswordCredentialsProvider.class)));
		CredentialItem.Username usernameCredential = new CredentialItem.Username();
		CredentialItem.Password passwordCredential = new CredentialItem.Password();
		assertTrue(credentialsProvider.supports(usernameCredential));
		assertTrue(credentialsProvider.get(new URIish(), usernameCredential));
		assertEquals(usernameCredential.getValue(), username);
		assertTrue(credentialsProvider.supports(passwordCredential));
		assertTrue(credentialsProvider.get(new URIish(), passwordCredential));
		assertEquals(String.valueOf(passwordCredential.getValue()), password);

		assertThat(jGitCommandConfigurer.getTransportConfigCallback(), is(transportConfigCallback));
		assertThat(jGitCommandConfigurer.getTimeout(), is(5));
	}

	@Test
	public void passphraseShouldSetCredentials() throws Exception {
		final String passphrase = "mypassphrase";
		MockCloneCommand mockCloneCommand = new MockCloneCommand(gitMock);
		JGitEnvironmentRepository envRepository = new JGitEnvironmentRepository(this.environment,
				new JGitEnvironmentProperties());
		envRepository.setGitFactory(new MockGitFactory(gitMock, mockCloneCommand));
		envRepository.setUri("git+ssh://git@somegitserver/somegitrepo");
		envRepository.setBasedir(new File("./mybasedir"));
		envRepository.setPassphrase(passphrase);
		envRepository.setCloneOnStart(true);
		envRepository.afterPropertiesSet();

		assertTrue(mockCloneCommand.hasPassphraseCredentialsProvider());

		CredentialsProvider provider = mockCloneCommand.getCredentialsProvider();
		assertFalse(provider.isInteractive());

		CredentialItem.StringType stringCredential = new CredentialItem.StringType(PassphraseCredentialsProvider.PROMPT,
				true);

		assertTrue(provider.supports(stringCredential));
		provider.get(new URIish(), stringCredential);
		assertEquals(stringCredential.getValue(), passphrase);
	}

	@Test
	public void gitCredentialsProviderFactoryCreatesPassphraseProvider() throws Exception {
		final String passphrase = "mypassphrase";
		final String gitUri = "git+ssh://git@somegitserver/somegitrepo";
		GitCredentialsProviderFactory credentialsFactory = new GitCredentialsProviderFactory();
		MockCloneCommand mockCloneCommand = new MockCloneCommand(gitMock);

		JGitEnvironmentRepository envRepository = new JGitEnvironmentRepository(this.environment,
				new JGitEnvironmentProperties());
		envRepository.setGitFactory(new MockGitFactory(gitMock, mockCloneCommand));
		envRepository.setUri(gitUri);
		envRepository.setBasedir(new File("./mybasedir"));
		envRepository.setGitCredentialsProvider(credentialsFactory.createFor(gitUri, null, null, passphrase));
		envRepository.setCloneOnStart(true);
		envRepository.afterPropertiesSet();

		assertTrue(mockCloneCommand.hasPassphraseCredentialsProvider());

		CredentialsProvider provider = mockCloneCommand.getCredentialsProvider();
		assertFalse(provider.isInteractive());

		CredentialItem.StringType stringCredential = new CredentialItem.StringType(PassphraseCredentialsProvider.PROMPT,
				true);

		assertTrue(provider.supports(stringCredential));
		provider.get(new URIish(), stringCredential);
		assertEquals(stringCredential.getValue(), passphrase);
	}

	@Test
	public void gitCredentialsProviderFactoryCreatesUsernamePasswordProvider() throws Exception {
		GitCredentialsProviderFactory credentialsFactory = new GitCredentialsProviderFactory();
		MockCloneCommand mockCloneCommand = new MockCloneCommand(gitMock);
		final String username = "someuser";
		final String password = "mypassword";

		JGitEnvironmentRepository envRepository = new JGitEnvironmentRepository(this.environment,
				new JGitEnvironmentProperties());
		envRepository.setGitFactory(new MockGitFactory(gitMock, mockCloneCommand));
		envRepository.setUri("git+ssh://git@somegitserver/somegitrepo");
		envRepository.setBasedir(new File("./mybasedir"));
		envRepository.setGitCredentialsProvider(
				credentialsFactory.createFor(envRepository.getUri(), username, password, null));
		envRepository.setCloneOnStart(true);
		envRepository.afterPropertiesSet();

		assertTrue(mockCloneCommand.getCredentialsProvider() instanceof UsernamePasswordCredentialsProvider);

		CredentialsProvider provider = mockCloneCommand.getCredentialsProvider();
		CredentialItem.Username usernameCredential = new CredentialItem.Username();
		CredentialItem.Password passwordCredential = new CredentialItem.Password();
		assertTrue(provider.supports(usernameCredential));
		assertTrue(provider.supports(passwordCredential));

		provider.get(new URIish(), usernameCredential);
		assertEquals(usernameCredential.getValue(), username);
		provider.get(new URIish(), passwordCredential);
		assertEquals(String.valueOf(passwordCredential.getValue()), password);
	}

	@Test
	public void gitCredentialsProviderFactoryCreatesAwsCodeCommitProvider() throws Exception {
		GitCredentialsProviderFactory credentialsFactory = new GitCredentialsProviderFactory();
		Git mockGit = mock(Git.class);
		MockCloneCommand mockCloneCommand = new MockCloneCommand(mockGit);
		final String awsUri = "https://git-codecommit.us-east-1.amazonaws.com/v1/repos/test";

		JGitEnvironmentRepository envRepository = new JGitEnvironmentRepository(this.environment,
				new JGitEnvironmentProperties());
		envRepository.setGitFactory(new MockGitFactory(mockGit, mockCloneCommand));
		envRepository.setUri(awsUri);
		envRepository.setGitCredentialsProvider(credentialsFactory.createFor(envRepository.getUri(), null, null, null));
		envRepository.setCloneOnStart(true);
		envRepository.afterPropertiesSet();

		assertTrue(mockCloneCommand.getCredentialsProvider() instanceof AwsCodeCommitCredentialProvider);
	}

	@Test
	public void shouldRemoveBranchesIfPropertySet() throws Exception {
		this.repository.setDeleteUntrackedBranches(true);
		String label = "master";

		//clone
		when(cloneCommandMock.setURI(URI)).thenReturn(cloneCommandMock);
		when(cloneCommandMock.setDirectory(any(File.class))).thenReturn(cloneCommandMock);
		when(cloneCommandMock.call()).thenReturn(gitMock);

		// refresh()->shouldPull
		Status status = mock(Status.class);
		when(jGitCommandExecutor.status(gitMock)).thenReturn(status);
		when(status.isClean()).thenReturn(true);

		Repository repository = mock(Repository.class);
		when(gitMock.getRepository()).thenReturn(repository);
		StoredConfig storedConfig = mock(StoredConfig.class);
		when(repository.getConfig()).thenReturn(storedConfig);
		when(storedConfig.getString("remote", "origin", "url")).thenReturn("http://example/git");

		// refresh()->fetch
		FetchResult fetchResult = mock(FetchResult.class);
		TrackingRefUpdate trackingRefUpdate = mock(TrackingRefUpdate.class);
		Collection<TrackingRefUpdate> trackingRefUpdates = singletonList(trackingRefUpdate);

		when(jGitCommandExecutor.safeFetch(gitMock, label, true)).thenReturn(fetchResult);
		when(fetchResult.getTrackingRefUpdates()).thenReturn(trackingRefUpdates);

		// refresh()->deleteBranch
		ReceiveCommand receiveCommand = mock(ReceiveCommand.class);
		when(trackingRefUpdate.asReceiveCommand()).thenReturn(receiveCommand);
		when(receiveCommand.getType()).thenReturn(ReceiveCommand.Type.DELETE);
		when(trackingRefUpdate.getLocalName()).thenReturn("refs/remotes/origin/feature/deletedBranchFromOrigin");

		when(jGitCommandExecutor.safeDeleteBranches(gitMock, singletonList("feature/deletedBranchFromOrigin"), ""))
				.thenReturn(singletonList("feature/deletedBranchFromOrigin"));

		// refresh()->checkout
		when(jGitCommandExecutor.checkout(gitMock, label)).thenReturn(mock(Ref.class));
		when(jGitCommandExecutor.isBranch(gitMock, label)).thenReturn(true);

		// refresh()->merge
		when(jGitCommandExecutor.safeMerge(gitMock, label)).thenReturn(mock(MergeResult.class));

		// refresh()->return
		Ref headRef = mock(Ref.class);
		when(repository.findRef(anyString())).thenReturn(headRef);
		ObjectId newObjectId = ObjectId.fromRaw(new int[]{1, 2, 3, 4, 5});
		when(headRef.getObjectId()).thenReturn(newObjectId);

		SearchPathLocator.Locations locations = this.repository.getLocations("bar", "staging", label);
		assertEquals(locations.getVersion(), newObjectId.getName());

		verify(jGitCommandExecutor).checkout(gitMock, label);
		verify(jGitCommandExecutor).safeDeleteBranches(gitMock, singletonList("feature/deletedBranchFromOrigin"), label);
		verify(jGitCommandExecutor).safeMerge(gitMock, label);
	}

	@Test
	public void shouldHardResetIfNotClean() throws Exception {
		repository.setForcePull(true);
		String label = "master";

		//clone
		when(cloneCommandMock.setURI(URI)).thenReturn(cloneCommandMock);
		when(cloneCommandMock.setDirectory(any(File.class))).thenReturn(cloneCommandMock);
		when(cloneCommandMock.call()).thenReturn(gitMock);

		// refresh()->shouldPull
		Status status = mock(Status.class);
		when(jGitCommandExecutor.status(gitMock)).thenReturn(status);
		when(status.isClean()).thenReturn(false);

		Repository repository = mock(Repository.class);
		when(gitMock.getRepository()).thenReturn(repository);
		StoredConfig storedConfig = mock(StoredConfig.class);
		when(repository.getConfig()).thenReturn(storedConfig);
		when(storedConfig.getString("remote", "origin", "url")).thenReturn("http://example/git");

		// refresh()->fetch
		when(jGitCommandExecutor.safeFetch(gitMock, label, true)).thenReturn(mock(FetchResult.class));

		when(jGitCommandExecutor.safeDeleteBranches(gitMock, singletonList("feature/deletedBranchFromOrigin"), ""))
				.thenReturn(singletonList("feature/deletedBranchFromOrigin"));

		// refresh()->checkout
		when(jGitCommandExecutor.checkout(gitMock, label)).thenReturn(mock(Ref.class));
		when(jGitCommandExecutor.isBranch(gitMock, label)).thenReturn(true);

		// refresh()->merge
		when(jGitCommandExecutor.safeMerge(gitMock, label)).thenReturn(mock(MergeResult.class));

		// refresh()->return
		Ref headRef = mock(Ref.class);
		when(repository.findRef(anyString())).thenReturn(headRef);
		ObjectId newObjectId = ObjectId.fromRaw(new int[]{1, 2, 3, 4, 5});
		when(headRef.getObjectId()).thenReturn(newObjectId);

		SearchPathLocator.Locations locations = this.repository.getLocations("bar", "staging", label);
		assertEquals(locations.getVersion(), newObjectId.getName());

		verify(jGitCommandExecutor).checkout(gitMock, label);
		verify(jGitCommandExecutor).safeMerge(gitMock, label);
		verify(jGitCommandExecutor).safeHardReset(gitMock, label, "refs/remotes/origin/master");
	}

	class MockCloneCommand extends CloneCommand {
		private Git mockGit;

		public MockCloneCommand(Git mockGit) {
			this.mockGit = mockGit;
		}

		@Override
		public Git call() {
			return mockGit;
		}

		public boolean hasPassphraseCredentialsProvider() {
			return credentialsProvider instanceof PassphraseCredentialsProvider;
		}

		public CredentialsProvider getCredentialsProvider() {
			return credentialsProvider;
		}
	}

	class MockGitFactory extends JGitFactory {

		private Git mockGit;
		private CloneCommand mockCloneCommand;

		public MockGitFactory(Git mockGit, CloneCommand mockCloneCommand) {
			this.mockGit = mockGit;
			this.mockCloneCommand = mockCloneCommand;
		}

		@Override
		public Git getGitByOpen(File file) {
			return this.mockGit;
		}

		@Override
		public CloneCommand getCloneCommandByCloneRepository() {
			return this.mockCloneCommand;
		}
	}
}
