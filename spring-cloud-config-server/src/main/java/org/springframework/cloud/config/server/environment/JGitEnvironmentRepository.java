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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.jcraft.jsch.Session;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.DeleteBranchCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.eclipse.jgit.util.FileUtils;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.config.server.support.GitCredentialsProviderFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.UrlResource;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static java.lang.String.format;
import static org.eclipse.jgit.transport.ReceiveCommand.Type.DELETE;

/**
 * An {@link EnvironmentRepository} backed by a single git repository.
 *
 * @author Dave Syer
 * @author Roy Clarkson
 * @author Marcos Barbero
 * @author Daniel Lavoie
 * @author Ryan Lynch
 * @author Gareth Clay
 */
public class JGitEnvironmentRepository extends AbstractScmEnvironmentRepository
		implements EnvironmentRepository, SearchPathLocator, InitializingBean {

	/**
	 * Error message for URI for git repo.
	 */
	public static final String MESSAGE = "You need to configure a uri for the git repository.";

	private static final String FILE_URI_PREFIX = "file:";

	private static final String LOCAL_BRANCH_REF_PREFIX = "refs/remotes/origin/";

	/**
	 * Timeout (in seconds) for obtaining HTTP or SSH connection (if applicable). Default
	 * 5 seconds.
	 */
	private int timeout;

	/**
	 * Time (in seconds) between refresh of the git repository.
	 */
	private int refreshRate = 0;

	/**
	 * Time of the last refresh of the git repository.
	 */
	private long lastRefresh;

	/**
	 * Flag to indicate that the repository should be cloned on startup (not on demand).
	 * Generally leads to slower startup but faster first query.
	 */
	private boolean cloneOnStart;

	private JGitEnvironmentRepository.JGitFactory gitFactory = new JGitEnvironmentRepository.JGitFactory();

	private String defaultLabel;

	/**
	 * Factory used to create the credentials provider to use to connect to the Git
	 * repository.
	 */
	private GitCredentialsProviderFactory gitCredentialsProviderFactory = new GitCredentialsProviderFactory();

	/**
	 * Transport configuration callback for JGit commands.
	 */
	private TransportConfigCallback transportConfigCallback;

	/**
	 * Flag to indicate that the repository should force pull. If true discard any local
	 * changes and take from remote repository.
	 */
	private boolean forcePull;

	private boolean initialized;

	/**
	 * Flag to indicate that the branch should be deleted locally if it's origin tracked
	 * branch was removed.
	 */
	private boolean deleteUntrackedBranches;

	/**
	 * Flag to indicate that SSL certificate validation should be bypassed when
	 * communicating with a repository served over an HTTPS connection.
	 */
	private boolean skipSslValidation;

	public JGitEnvironmentRepository(ConfigurableEnvironment environment,
			JGitEnvironmentProperties properties) {
		super(environment, properties);
		this.cloneOnStart = properties.isCloneOnStart();
		this.defaultLabel = properties.getDefaultLabel();
		this.forcePull = properties.isForcePull();
		this.timeout = properties.getTimeout();
		this.deleteUntrackedBranches = properties.isDeleteUntrackedBranches();
		this.refreshRate = properties.getRefreshRate();
		this.skipSslValidation = properties.isSkipSslValidation();
	}

	public boolean isCloneOnStart() {
		return this.cloneOnStart;
	}

	public void setCloneOnStart(boolean cloneOnStart) {
		this.cloneOnStart = cloneOnStart;
	}

	public int getTimeout() {
		return this.timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public int getRefreshRate() {
		return this.refreshRate;
	}

	public void setRefreshRate(int refreshRate) {
		this.refreshRate = refreshRate;
	}

	public TransportConfigCallback getTransportConfigCallback() {
		return this.transportConfigCallback;
	}

	public void setTransportConfigCallback(
			TransportConfigCallback transportConfigCallback) {
		this.transportConfigCallback = transportConfigCallback;
	}

	public JGitFactory getGitFactory() {
		return this.gitFactory;
	}

	public void setGitFactory(JGitFactory gitFactory) {
		this.gitFactory = gitFactory;
	}

	public void setGitCredentialsProviderFactory(
			GitCredentialsProviderFactory gitCredentialsProviderFactory) {
		this.gitCredentialsProviderFactory = gitCredentialsProviderFactory;
	}

	public String getDefaultLabel() {
		return this.defaultLabel;
	}

	public void setDefaultLabel(String defaultLabel) {
		this.defaultLabel = defaultLabel;
	}

	public boolean isForcePull() {
		return this.forcePull;
	}

	public void setForcePull(boolean forcePull) {
		this.forcePull = forcePull;
	}

	public boolean isDeleteUntrackedBranches() {
		return this.deleteUntrackedBranches;
	}

	public void setDeleteUntrackedBranches(boolean deleteUntrackedBranches) {
		this.deleteUntrackedBranches = deleteUntrackedBranches;
	}

	public boolean isSkipSslValidation() {
		return this.skipSslValidation;
	}

	public void setSkipSslValidation(boolean skipSslValidation) {
		this.skipSslValidation = skipSslValidation;
	}

	@Override
	public synchronized Locations getLocations(String application, String profile,
			String label) {
		if (label == null) {
			label = this.defaultLabel;
		}
		String version = refresh(label);
		return new Locations(application, profile, label, version,
				getSearchLocations(getWorkingDirectory(), application, profile, label));
	}

	@Override
	public synchronized void afterPropertiesSet() throws Exception {
		Assert.state(getUri() != null, MESSAGE);
		initialize();
		if (this.cloneOnStart) {
			initClonedRepository();
		}
	}

	/**
	 * Get the working directory ready.
	 * @param label label to refresh
	 * @return head id
	 */
	public String refresh(String label) {
		Git git = null;
		try {
			git = createGitClient();
			if (shouldPull(git)) {
				FetchResult fetchStatus = fetch(git, label);
				if (this.deleteUntrackedBranches && fetchStatus != null) {
					deleteUntrackedLocalBranches(fetchStatus.getTrackingRefUpdates(),
							git);
				}
				// checkout after fetch so we can get any new branches, tags, ect.
				checkout(git, label);
				tryMerge(git, label);
			}
			else {
				// nothing to update so just checkout and merge.
				// Merge because remote branch could have been updated before
				checkout(git, label);
				tryMerge(git, label);
			}
			// always return what is currently HEAD as the version
			return git.getRepository().findRef("HEAD").getObjectId().getName();
		}
		catch (RefNotFoundException e) {
			throw new NoSuchLabelException("No such label: " + label, e);
		}
		catch (NoRemoteRepositoryException e) {
			throw new NoSuchRepositoryException("No such repository: " + getUri(), e);
		}
		catch (GitAPIException e) {
			throw new NoSuchRepositoryException(
					"Cannot clone or checkout repository: " + getUri(), e);
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot load environment", e);
		}
		finally {
			try {
				if (git != null) {
					git.close();
				}
			}
			catch (Exception e) {
				this.logger.warn("Could not close git repository", e);
			}
		}
	}

	private void tryMerge(Git git, String label) {
		try {
			if (isBranch(git, label)) {
				// merge results from fetch
				merge(git, label);
				if (!isClean(git, label)) {
					this.logger.warn(
							"The local repository is dirty or ahead of origin. Resetting"
									+ " it to origin/" + label + ".");
					resetHard(git, label, LOCAL_BRANCH_REF_PREFIX + label);
				}
			}
		}
		catch (GitAPIException e) {
			throw new NoSuchRepositoryException(
					"Cannot clone or checkout repository: " + getUri(), e);
		}
	}

	/**
	 * Clones the remote repository and then opens a connection to it.
	 * @throws GitAPIException when cloning fails
	 * @throws IOException when repo opening fails
	 */
	private void initClonedRepository() throws GitAPIException, IOException {
		if (!getUri().startsWith(FILE_URI_PREFIX)) {
			deleteBaseDirIfExists();
			Git git = cloneToBasedir();
			if (git != null) {
				git.close();
			}
			git = openGitRepository();
			if (git != null) {
				git.close();
			}
		}

	}

	/**
	 * Deletes local branches if corresponding remote branch was removed.
	 * @param trackingRefUpdates list of tracking ref updates
	 * @param git git instance
	 * @return list of deleted branches
	 */
	private Collection<String> deleteUntrackedLocalBranches(
			Collection<TrackingRefUpdate> trackingRefUpdates, Git git) {
		if (CollectionUtils.isEmpty(trackingRefUpdates)) {
			return Collections.emptyList();
		}

		Collection<String> branchesToDelete = new ArrayList<>();
		for (TrackingRefUpdate trackingRefUpdate : trackingRefUpdates) {
			ReceiveCommand receiveCommand = trackingRefUpdate.asReceiveCommand();
			if (receiveCommand.getType() == DELETE) {
				String localRefName = trackingRefUpdate.getLocalName();
				if (StringUtils.startsWithIgnoreCase(localRefName,
						LOCAL_BRANCH_REF_PREFIX)) {
					String localBranchName = localRefName.substring(
							LOCAL_BRANCH_REF_PREFIX.length(), localRefName.length());
					branchesToDelete.add(localBranchName);
				}
			}
		}

		if (CollectionUtils.isEmpty(branchesToDelete)) {
			return Collections.emptyList();
		}

		try {
			// make sure that deleted branch not a current one
			checkout(git, this.defaultLabel);
			return deleteBranches(git, branchesToDelete);
		}
		catch (Exception ex) {
			String message = format("Failed to delete %s branches.", branchesToDelete);
			warn(message, ex);
			return Collections.emptyList();
		}
	}

	private List<String> deleteBranches(Git git, Collection<String> branchesToDelete)
			throws GitAPIException {
		DeleteBranchCommand deleteBranchCommand = git.branchDelete()
				.setBranchNames(branchesToDelete.toArray(new String[0]))
				// local branch can contain data which is not merged to HEAD - force
				// delete it anyway, since local copy should be R/O
				.setForce(true);
		List<String> resultList = deleteBranchCommand.call();
		this.logger.info(format("Deleted %s branches from %s branches to delete.",
				resultList, branchesToDelete));
		return resultList;
	}

	private Ref checkout(Git git, String label) throws GitAPIException {
		CheckoutCommand checkout = git.checkout();
		if (shouldTrack(git, label)) {
			trackBranch(git, checkout, label);
		}
		else {
			// works for tags and local branches
			checkout.setName(label);
		}
		return checkout.call();
	}

	protected boolean shouldPull(Git git) throws GitAPIException {
		boolean shouldPull;

		if (this.refreshRate > 0 && System.currentTimeMillis()
				- this.lastRefresh < (this.refreshRate * 1000)) {
			return false;
		}

		Status gitStatus = git.status().call();
		boolean isWorkingTreeClean = gitStatus.isClean();
		String originUrl = git.getRepository().getConfig().getString("remote", "origin",
				"url");

		if (this.forcePull && !isWorkingTreeClean) {
			shouldPull = true;
			logDirty(gitStatus);
		}
		else {
			shouldPull = isWorkingTreeClean && originUrl != null;
		}
		if (!isWorkingTreeClean && !this.forcePull) {
			this.logger.info("Cannot pull from remote " + originUrl
					+ ", the working tree is not clean.");
		}
		return shouldPull;
	}

	@SuppressWarnings("unchecked")
	private void logDirty(Status status) {
		Set<String> dirties = dirties(status.getAdded(), status.getChanged(),
				status.getRemoved(), status.getMissing(), status.getModified(),
				status.getConflicting(), status.getUntracked());
		this.logger.warn(format("Dirty files found: %s", dirties));
	}

	@SuppressWarnings("unchecked")
	private Set<String> dirties(Set<String>... changes) {
		Set<String> dirties = new HashSet<>();
		for (Set<String> files : changes) {
			dirties.addAll(files);
		}
		return dirties;
	}

	private boolean shouldTrack(Git git, String label) throws GitAPIException {
		return isBranch(git, label) && !isLocalBranch(git, label);
	}

	protected FetchResult fetch(Git git, String label) {
		FetchCommand fetch = git.fetch();
		fetch.setRemote("origin");
		fetch.setTagOpt(TagOpt.FETCH_TAGS);
		fetch.setRemoveDeletedRefs(this.deleteUntrackedBranches);
		if (this.refreshRate > 0) {
			this.setLastRefresh(System.currentTimeMillis());
		}

		configureCommand(fetch);
		try {
			FetchResult result = fetch.call();
			if (result.getTrackingRefUpdates() != null
					&& result.getTrackingRefUpdates().size() > 0) {
				this.logger.info("Fetched for remote " + label + " and found "
						+ result.getTrackingRefUpdates().size() + " updates");
			}
			return result;
		}
		catch (Exception ex) {
			String message = "Could not fetch remote for " + label + " remote: " + git
					.getRepository().getConfig().getString("remote", "origin", "url");
			warn(message, ex);
			return null;
		}
	}

	private MergeResult merge(Git git, String label) {
		try {
			MergeCommand merge = git.merge();
			merge.include(git.getRepository().findRef("origin/" + label));
			MergeResult result = merge.call();
			if (!result.getMergeStatus().isSuccessful()) {
				this.logger.warn("Merged from remote " + label + " with result "
						+ result.getMergeStatus());
			}
			return result;
		}
		catch (Exception ex) {
			String message = "Could not merge remote for " + label + " remote: " + git
					.getRepository().getConfig().getString("remote", "origin", "url");
			warn(message, ex);
			return null;
		}
	}

	private Ref resetHard(Git git, String label, String ref) {
		ResetCommand reset = git.reset();
		reset.setRef(ref);
		reset.setMode(ResetType.HARD);
		try {
			Ref resetRef = reset.call();
			if (resetRef != null) {
				this.logger.info(
						"Reset label " + label + " to version " + resetRef.getObjectId());
			}
			return resetRef;
		}
		catch (Exception ex) {
			String message = "Could not reset to remote for " + label + " (current ref="
					+ ref + "), remote: " + git.getRepository().getConfig()
							.getString("remote", "origin", "url");
			warn(message, ex);
			return null;
		}
	}

	private Git createGitClient() throws IOException, GitAPIException {
		File lock = new File(getWorkingDirectory(), ".git/index.lock");
		if (lock.exists()) {
			// The only way this can happen is if another JVM (e.g. one that
			// crashed earlier) created the lock. We can attempt to recover by
			// wiping the slate clean.
			this.logger.info("Deleting stale JGit lock file at " + lock);
			lock.delete();
		}
		if (new File(getWorkingDirectory(), ".git").exists()) {
			return openGitRepository();
		}
		else {
			return copyRepository();
		}
	}

	// Synchronize here so that multiple requests don't all try and delete the
	// base dir
	// together (this is a once only operation, so it only holds things up on
	// the first
	// request).
	private synchronized Git copyRepository() throws IOException, GitAPIException {
		deleteBaseDirIfExists();
		getBasedir().mkdirs();
		Assert.state(getBasedir().exists(), "Could not create basedir: " + getBasedir());
		if (getUri().startsWith(FILE_URI_PREFIX)) {
			return copyFromLocalRepository();
		}
		else {
			return cloneToBasedir();
		}
	}

	private Git openGitRepository() throws IOException {
		Git git = this.gitFactory.getGitByOpen(getWorkingDirectory());
		return git;
	}

	private Git copyFromLocalRepository() throws IOException {
		Git git;
		File remote = new UrlResource(StringUtils.cleanPath(getUri())).getFile();
		Assert.state(remote.isDirectory(), "No directory at " + getUri());
		File gitDir = new File(remote, ".git");
		Assert.state(gitDir.exists(), "No .git at " + getUri());
		Assert.state(gitDir.isDirectory(), "No .git directory at " + getUri());
		git = this.gitFactory.getGitByOpen(remote);
		return git;
	}

	private Git cloneToBasedir() throws GitAPIException {
		CloneCommand clone = this.gitFactory.getCloneCommandByCloneRepository()
				.setURI(getUri()).setDirectory(getBasedir());
		configureCommand(clone);
		try {
			return clone.call();
		}
		catch (GitAPIException e) {
			this.logger.warn("Error occured cloning to base directory.", e);
			deleteBaseDirIfExists();
			throw e;
		}
	}

	private void deleteBaseDirIfExists() {
		if (getBasedir().exists()) {
			for (File file : getBasedir().listFiles()) {
				try {
					FileUtils.delete(file, FileUtils.RECURSIVE);
				}
				catch (IOException e) {
					throw new IllegalStateException("Failed to initialize base directory",
							e);
				}
			}
		}
	}

	private void initialize() {
		if (!this.initialized) {
			SshSessionFactory.setInstance(new JschConfigSessionFactory() {
				@Override
				protected void configure(Host hc, Session session) {
					session.setConfig("StrictHostKeyChecking",
							isStrictHostKeyChecking() ? "yes" : "no");
				}
			});
			this.initialized = true;
		}
	}

	private void configureCommand(TransportCommand<?, ?> command) {
		command.setTimeout(this.timeout);
		if (this.transportConfigCallback != null) {
			command.setTransportConfigCallback(this.transportConfigCallback);
		}
		CredentialsProvider credentialsProvider = getCredentialsProvider();
		if (credentialsProvider != null) {
			command.setCredentialsProvider(credentialsProvider);
		}
	}

	private CredentialsProvider getCredentialsProvider() {
		return this.gitCredentialsProviderFactory.createFor(this.getUri(), getUsername(),
				getPassword(), getPassphrase(), isSkipSslValidation());
	}

	private boolean isClean(Git git, String label) {
		StatusCommand status = git.status();
		try {
			BranchTrackingStatus trackingStatus = BranchTrackingStatus
					.of(git.getRepository(), label);
			boolean isBranchAhead = trackingStatus != null
					&& trackingStatus.getAheadCount() > 0;
			return status.call().isClean() && !isBranchAhead;
		}
		catch (Exception e) {
			String message = "Could not execute status command on local repository. Cause: ("
					+ e.getClass().getSimpleName() + ") " + e.getMessage();
			warn(message, e);
			return false;
		}
	}

	private void trackBranch(Git git, CheckoutCommand checkout, String label) {
		checkout.setCreateBranch(true).setName(label)
				.setUpstreamMode(SetupUpstreamMode.TRACK)
				.setStartPoint("origin/" + label);
	}

	private boolean isBranch(Git git, String label) throws GitAPIException {
		return containsBranch(git, label, ListMode.ALL);
	}

	private boolean isLocalBranch(Git git, String label) throws GitAPIException {
		return containsBranch(git, label, null);
	}

	private boolean containsBranch(Git git, String label, ListMode listMode)
			throws GitAPIException {
		ListBranchCommand command = git.branchList();
		if (listMode != null) {
			command.setListMode(listMode);
		}
		List<Ref> branches = command.call();
		for (Ref ref : branches) {
			if (ref.getName().endsWith("/" + label)) {
				return true;
			}
		}
		return false;
	}

	protected void warn(String message, Exception ex) {
		this.logger.warn(message);
		if (this.logger.isDebugEnabled()) {
			this.logger.debug("Stacktrace for: " + message, ex);
		}
	}

	public long getLastRefresh() {
		return this.lastRefresh;
	}

	public void setLastRefresh(long lastRefresh) {
		this.lastRefresh = lastRefresh;
	}

	/**
	 * Wraps the static method calls to {@link org.eclipse.jgit.api.Git} and
	 * {@link org.eclipse.jgit.api.CloneCommand} allowing for easier unit testing.
	 */
	public static class JGitFactory {

		public Git getGitByOpen(File file) throws IOException {
			Git git = Git.open(file);
			return git;
		}

		public CloneCommand getCloneCommandByCloneRepository() {
			CloneCommand command = Git.cloneRepository();
			return command;
		}

	}

}
