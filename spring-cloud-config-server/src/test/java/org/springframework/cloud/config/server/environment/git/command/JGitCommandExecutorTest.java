package org.springframework.cloud.config.server.environment.git.command;

import java.util.List;

import org.apache.commons.logging.Log;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.DeleteBranchCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.TagOpt;
import org.junit.Before;
import org.junit.Test;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Taras Danylchuk
 */
public class JGitCommandExecutorTest {

	private static final String LABEL = "customLabel";
	private Log mockedLog = mock(Log.class);
	private Git gitMock;
	private JGitCommandConfigurer jGitCommandConfigurer;
	private JGitCommandExecutor jGitCommandExecutor;

	@Before
	public void setUp() {
		mockedLog = mock(Log.class);
		jGitCommandConfigurer = mock(JGitCommandConfigurer.class);
		jGitCommandExecutor = new JGitCommandExecutor(jGitCommandConfigurer);
		jGitCommandExecutor.logger = mockedLog;
		gitMock = mock(Git.class);
	}

	@Test
	public void shouldSafeMerge() throws Exception {
		MergeCommand mergeCommand = mock(MergeCommand.class);
		Repository repository = mock(Repository.class);
		Ref ref = mock(Ref.class);
		MergeResult mergeResult = mock(MergeResult.class);
		MergeResult.MergeStatus mergeStatus = mock(MergeResult.MergeStatus.class);

		when(gitMock.merge()).thenReturn(mergeCommand);
		when(mergeCommand.call()).thenReturn(mergeResult);
		when(gitMock.getRepository()).thenReturn(repository);
		when(repository.findRef("origin/customLabel")).thenReturn(ref);
		when(mergeResult.getMergeStatus()).thenReturn(mergeStatus);
		when(mergeStatus.isSuccessful()).thenReturn(true);

		//test
		MergeResult result = jGitCommandExecutor.safeMerge(gitMock, LABEL);

		assertThat(result).isSameAs(mergeResult);
		verify(mergeCommand).include(ref);
		verifyNoMoreInteractions(mockedLog);
	}

	@Test
	public void shouldLogOnUnsuccessfulMerge() throws Exception {
		MergeCommand mergeCommand = mock(MergeCommand.class);
		Repository repository = mock(Repository.class);
		Ref ref = mock(Ref.class);
		MergeResult mergeResult = mock(MergeResult.class);
		MergeResult.MergeStatus mergeStatus = mock(MergeResult.MergeStatus.class);

		when(gitMock.merge()).thenReturn(mergeCommand);
		when(mergeCommand.call()).thenReturn(mergeResult);
		when(gitMock.getRepository()).thenReturn(repository);
		when(repository.findRef("origin/customLabel")).thenReturn(ref);
		when(mergeResult.getMergeStatus()).thenReturn(mergeStatus);
		when(mergeStatus.isSuccessful()).thenReturn(false);

		//test
		MergeResult result = jGitCommandExecutor.safeMerge(gitMock, LABEL);

		assertThat(result).isSameAs(mergeResult);
		verify(mergeCommand).include(ref);
		verify(mockedLog).warn(startsWith("Merged from remote customLabel with result "));
	}

	@Test
	public void shouldNotFailOnMergeInCaseOfException() {
		Repository repository = mock(Repository.class);
		StoredConfig storedConfig = mock(StoredConfig.class);

		when(gitMock.getRepository()).thenReturn(repository);
		when(repository.getConfig()).thenReturn(storedConfig);
		when(storedConfig.getString("remote", "origin", "url")).thenReturn("http://example/git");

		when(gitMock.merge()).thenThrow(new RuntimeException("wrong"));

		//test
		MergeResult result = jGitCommandExecutor.safeMerge(gitMock, LABEL);

		assertThat(result).isNull();
		verify(mockedLog).warn(eq("Could not merge remote for customLabel remote: http://example/git"));
	}

	@Test
	public void shouldResetHard() throws Exception {
		String resetTo = "resetTo";
		ResetCommand resetCommand = mock(ResetCommand.class);
		Ref ref = mock(Ref.class);

		when(gitMock.reset()).thenReturn(resetCommand);
		when(resetCommand.call()).thenReturn(ref);

		//test
		Ref result = jGitCommandExecutor.safeHardReset(gitMock, LABEL, resetTo);

		assertThat(result).isSameAs(ref);

		verify(resetCommand).setRef(resetTo);
		verify(resetCommand).setMode(ResetCommand.ResetType.HARD);
		verify(mockedLog).info(anyString());
	}

	@Test
	public void shouldNotFailOnResetHard() throws Exception {
		String resetTo = "resetTo";
		ResetCommand resetCommand = mock(ResetCommand.class);
		Repository repository = mock(Repository.class);
		StoredConfig storedConfig = mock(StoredConfig.class);

		when(gitMock.getRepository()).thenReturn(repository);
		when(repository.getConfig()).thenReturn(storedConfig);
		when(storedConfig.getString("remote", "origin", "url")).thenReturn("http://example/git");

		when(gitMock.reset()).thenReturn(resetCommand);
		when(resetCommand.call()).thenThrow(new CanceledException("canceled"));

		//test
		Ref result = jGitCommandExecutor.safeHardReset(gitMock, LABEL, resetTo);

		assertThat(result).isNull();
		verify(mockedLog).warn(anyString());
	}

	@Test
	public void shouldCheckout() throws Exception {
		CheckoutCommand checkoutCommand = mock(CheckoutCommand.class);
		Ref ref = mock(Ref.class);
		Ref branchListRef = mock(Ref.class);
		ListBranchCommand listBranchCommand = mock(ListBranchCommand.class);

		when(gitMock.checkout()).thenReturn(checkoutCommand);
		when(gitMock.branchList()).thenReturn(listBranchCommand);
		when(checkoutCommand.call()).thenReturn(ref);
		when(listBranchCommand.call()).thenReturn(singletonList(branchListRef));
		when(branchListRef.getName()).thenReturn("/abs");

		//test
		Ref result = jGitCommandExecutor.checkout(gitMock, LABEL);

		assertThat(result).isSameAs(ref);
		verify(listBranchCommand).setListMode(ListBranchCommand.ListMode.ALL);
		verify(checkoutCommand).setName(LABEL);
		verify(checkoutCommand).call();
		verifyNoMoreInteractions(checkoutCommand);
	}

	@Test
	public void shouldCheckoutAndNotTrachInBranchIsLocalTrack() throws Exception {
		CheckoutCommand checkoutCommand = mock(CheckoutCommand.class);
		Ref ref = mock(Ref.class);
		Ref branchListRef = mock(Ref.class);
		Ref localBranchListRef = mock(Ref.class);
		ListBranchCommand listBranchCommand = mock(ListBranchCommand.class);
		ListBranchCommand localListBranchCommand = mock(ListBranchCommand.class);

		when(gitMock.checkout()).thenReturn(checkoutCommand);
		when(gitMock.branchList()).thenReturn(listBranchCommand).thenReturn(localListBranchCommand);
		when(checkoutCommand.call()).thenReturn(ref);
		when(listBranchCommand.call()).thenReturn(singletonList(branchListRef));
		when(branchListRef.getName()).thenReturn("/" + LABEL);
		when(localListBranchCommand.call()).thenReturn(singletonList(localBranchListRef));
		when(localBranchListRef.getName()).thenReturn("/" + LABEL);

		//test
		Ref result = jGitCommandExecutor.checkout(gitMock, LABEL);

		assertThat(result).isSameAs(ref);
		verify(listBranchCommand).setListMode(ListBranchCommand.ListMode.ALL);
		verify(checkoutCommand).setName(LABEL);
		verify(checkoutCommand).call();
		verifyNoMoreInteractions(checkoutCommand);
	}

	@Test
	public void shouldCheckoutAndTrack() throws Exception {
		CheckoutCommand checkoutCommand = mock(CheckoutCommand.class);
		Ref ref = mock(Ref.class);
		Ref branchListRef = mock(Ref.class);
		Ref localBranchListRef = mock(Ref.class);
		ListBranchCommand listBranchCommand = mock(ListBranchCommand.class);
		ListBranchCommand localListBranchCommand = mock(ListBranchCommand.class);

		when(gitMock.checkout()).thenReturn(checkoutCommand);
		when(gitMock.branchList()).thenReturn(listBranchCommand).thenReturn(localListBranchCommand);
		when(checkoutCommand.call()).thenReturn(ref);
		when(listBranchCommand.call()).thenReturn(singletonList(branchListRef));
		when(branchListRef.getName()).thenReturn("/" + LABEL);
		when(localListBranchCommand.call()).thenReturn(singletonList(localBranchListRef));
		when(localBranchListRef.getName()).thenReturn("/abs");

		//test
		Ref result = jGitCommandExecutor.checkout(gitMock, LABEL);

		assertThat(result).isSameAs(ref);
		verify(listBranchCommand).setListMode(ListBranchCommand.ListMode.ALL);
		verify(checkoutCommand).setCreateBranch(true);
		verify(checkoutCommand).setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK);
		verify(checkoutCommand).setStartPoint("origin/" + LABEL);
		verify(checkoutCommand).setName(LABEL);
		verify(checkoutCommand).call();
		verifyNoMoreInteractions(checkoutCommand);
	}

	@Test
	public void safeDeleteBranches() throws Exception {
		//checkout first
		CheckoutCommand checkoutCommand = mock(CheckoutCommand.class);
		Ref ref = mock(Ref.class);
		Ref branchListRef = mock(Ref.class);
		ListBranchCommand listBranchCommand = mock(ListBranchCommand.class);

		when(gitMock.checkout()).thenReturn(checkoutCommand);
		when(gitMock.branchList()).thenReturn(listBranchCommand);
		when(checkoutCommand.call()).thenReturn(ref);
		when(listBranchCommand.call()).thenReturn(singletonList(branchListRef));
		when(branchListRef.getName()).thenReturn("/abs");

		//delete branch
		String branchToDelete = "deleteBranch";
		List<String> branchesToDelete = singletonList(branchToDelete);
		DeleteBranchCommand deleteBranchCommand = mock(DeleteBranchCommand.class);

		when(gitMock.branchDelete()).thenReturn(deleteBranchCommand);
		when(deleteBranchCommand.call()).thenReturn(branchesToDelete);

		//test
		List<String> result = jGitCommandExecutor.safeDeleteBranches(gitMock, branchesToDelete, LABEL);

		assertThat(result).isSameAs(branchesToDelete);

		verify(checkoutCommand).call();
		verify(deleteBranchCommand).setBranchNames(branchToDelete);
	}

	@Test
	public void shouldNotFailOnDeleteBranches() {
		when(gitMock.checkout()).thenThrow(new RuntimeException("cancelled"));

		//test
		List<String> result = jGitCommandExecutor.safeDeleteBranches(gitMock, singletonList("delete"), LABEL);

		assertThat(result).isEmpty();

		verify(mockedLog).warn(anyString());
	}

	@Test
	public void status() throws Exception {
		StatusCommand statusCommand = mock(StatusCommand.class);
		Status status = mock(Status.class);

		when(gitMock.status()).thenReturn(statusCommand);
		when(statusCommand.call()).thenReturn(status);

		//test
		Status result = jGitCommandExecutor.status(gitMock);

		assertThat(result).isSameAs(status);
	}

	@Test
	public void shouldPrintStacktraceIfDebugEnabled() {
		when(mockedLog.isDebugEnabled()).thenReturn(true);

		jGitCommandExecutor.warn("", new RuntimeException());

		verify(mockedLog).warn(eq(""));
		verify(mockedLog).debug(eq("Stacktrace for: "), any(RuntimeException.class));
	}

	@Test
	public void shouldExecuteFetchCommand() throws Exception {
		Git mockGit = mock(Git.class);
		FetchCommand fetchCommand = mock(FetchCommand.class);

		when(mockGit.fetch()).thenReturn(fetchCommand);
		FetchResult fetchResult = mock(FetchResult.class);
		when(fetchCommand.call()).thenReturn(fetchResult);

		FetchResult result = jGitCommandExecutor.safeFetch(mockGit, "master", true);

		verify(fetchCommand).setRemote("origin");
		verify(fetchCommand).setTagOpt(TagOpt.FETCH_TAGS);
		verify(fetchCommand).setRemoveDeletedRefs(true);
		verify(fetchCommand).call();
		verify(jGitCommandConfigurer).configureCommand(fetchCommand);

		assertThat(result).isSameAs(fetchResult);
	}

	@Test
	public void shouldNotFailOnFailedFetch() throws Exception {
		Git gitMock = mock(Git.class);
		Repository repositoryMock = mock(Repository.class);
		StoredConfig configMock = mock(StoredConfig.class);
		FetchCommand fetchCommand = mock(FetchCommand.class);

		when(gitMock.fetch()).thenReturn(fetchCommand);
		when(gitMock.getRepository()).thenReturn(repositoryMock);
		when(repositoryMock.getConfig()).thenReturn(configMock);
		when(fetchCommand.call()).thenThrow(CanceledException.class);

		FetchResult result = jGitCommandExecutor.safeFetch(gitMock, "master", false);

		verify(fetchCommand, times(1)).call();
		verify(mockedLog).warn(anyString());

		assertThat(result).isNull();
	}
}