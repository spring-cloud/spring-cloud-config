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
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FileUtils;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.config.server.support.PassphraseCredentialsProvider;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.UrlResource;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import static java.lang.String.format;
import static org.eclipse.jgit.transport.ReceiveCommand.Type.DELETE;
import static org.springframework.util.StringUtils.hasText;

/**
 * An {@link EnvironmentRepository} backed by a single git repository.
 *
 * @author Dave Syer
 * @author Roy Clarkson
 * @author Marcos Barbero
 * @author Daniel Lavoie
 * @author Ryan Lynch
 */
public class JGitEnvironmentRepository extends AbstractScmEnvironmentRepository
		implements EnvironmentRepository, SearchPathLocator, InitializingBean {

	private static final String FILE_URI_PREFIX = "file:";

	private static final String LOCAL_BRANCH_REF_PREFIX = "refs/remotes/origin/";

	/**
	 * Timeout (in seconds) for obtaining HTTP or SSH connection (if applicable). Default
	 * 5 seconds.
	 */
	private int timeout;

	/**
	 * Flag to indicate that the repository should be cloned on startup (not on demand).
	 * Generally leads to slower startup but faster first query.
	 */
	private boolean cloneOnStart;

	private JGitEnvironmentRepository.JGitFactory gitFactory = new JGitEnvironmentRepository.JGitFactory();

	private String defaultLabel;

	/**
	 * The credentials provider to use to connect to the Git repository.
	 */
	private CredentialsProvider gitCredentialsProvider;

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
	 * Flag to indicate that the branch should be deleted locally if it's origin tracked branch was removed.
	 */
	private boolean deleteUntrackedBranches;

	public JGitEnvironmentRepository(ConfigurableEnvironment environment, JGitEnvironmentProperties properties) {
		super(environment, properties);
		this.cloneOnStart = properties.isCloneOnStart();
		this.defaultLabel = properties.getDefaultLabel();
		this.forcePull = properties.isForcePull();
		this.timeout = properties.getTimeout();
		this.deleteUntrackedBranches = properties.isDeleteUntrackedBranches();
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

	public TransportConfigCallback getTransportConfigCallback() {
		return transportConfigCallback;
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

	public String getDefaultLabel() {
		return this.defaultLabel;
	}

	public void setDefaultLabel(String defaultLabel) {
		this.defaultLabel = defaultLabel;
	}

	public boolean isForcePull() {
		return forcePull;
	}

	public void setForcePull(boolean forcePull) {
		this.forcePull = forcePull;
	}

	public boolean isDeleteUntrackedBranches() {
		return deleteUntrackedBranches;
	}

	public void setDeleteUntrackedBranches(boolean deleteUntrackedBranches) {
		this.deleteUntrackedBranches = deleteUntrackedBranches;
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
	public void afterPropertiesSet() throws Exception {
		Assert.state(getUri() != null,
				"You need to configure a uri for the git repository");
		initialize();
		if (this.cloneOnStart) {
			initClonedRepository();
		}
	}

	/**
	 * Get the working directory ready.
	 */
	public String refresh(String label) {
		Git git = null;
		try {
			git = createGitClient();
			if (shouldPull(git)) {
				FetchResult fetchStatus = fetch(git, label);
				if(deleteUntrackedBranches) {
					deleteUntrackedLocalBranches(fetchStatus.getTrackingRefUpdates(), git);
				}
				// checkout after fetch so we can get any new branches, tags, ect.
				checkout(git, label);
				if (isBranch(git, label)) {
					// merge results from fetch
					merge(git, label);
					if (!isClean(git, label)) {
						logger.warn("The local repository is dirty or ahead of origin. Resetting"
								+ " it to origin/" + label + ".");
						resetHard(git, label, LOCAL_BRANCH_REF_PREFIX + label);
					}
				}
			}
			else {
				// nothing to update so just checkout
				checkout(git, label);
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

	/**
	 * Clones the remote repository and then opens a connection to it.
	 * 
	 * @throws GitAPIException
	 * @throws IOException
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
	 *
	 * @param trackingRefUpdates list of tracking ref updates
	 * @param git                git instance
	 * @return list of deleted branches
	 */
	private Collection<String> deleteUntrackedLocalBranches(Collection<TrackingRefUpdate> trackingRefUpdates, Git git) {
		if (CollectionUtils.isEmpty(trackingRefUpdates)) {
			return Collections.emptyList();
		}

		Collection<String> branchesToDelete = new ArrayList<>();
		for (TrackingRefUpdate trackingRefUpdate : trackingRefUpdates) {
			ReceiveCommand receiveCommand = trackingRefUpdate.asReceiveCommand();
			if (receiveCommand.getType() == DELETE) {
				String localRefName = trackingRefUpdate.getLocalName();
				if (StringUtils.startsWithIgnoreCase(localRefName, LOCAL_BRANCH_REF_PREFIX)) {
					String localBranchName = localRefName.substring(LOCAL_BRANCH_REF_PREFIX.length(), localRefName.length());
					branchesToDelete.add(localBranchName);
				}
			}
		}

		if (CollectionUtils.isEmpty(branchesToDelete)) {
			return Collections.emptyList();
		}

		try {
			//make sure that deleted branch not a current one
			checkout(git, defaultLabel);
			return deleteBranches(git, branchesToDelete);
		} catch (Exception ex) {
			String message = format("Failed to delete %s branches.", branchesToDelete);
			warn(message, ex);
			return Collections.emptyList();
		}
	}

	private List<String> deleteBranches(Git git, Collection<String> branchesToDelete) throws GitAPIException {
		DeleteBranchCommand deleteBranchCommand = git.branchDelete()
				.setBranchNames(branchesToDelete.toArray(new String[0]))
				//local branch can contain data which is not merged to HEAD - force delete it anyway, since local copy should be R/O
				.setForce(true);
		List<String> resultList = deleteBranchCommand.call();
		logger.info(format("Deleted %s branches from %s branches to delete.", resultList, branchesToDelete));
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
		fetch.setRemoveDeletedRefs(deleteUntrackedBranches);

		configureCommand(fetch);
		try {
			FetchResult result = fetch.call();
			if (result.getTrackingRefUpdates() != null
					&& result.getTrackingRefUpdates().size() > 0) {
				logger.info("Fetched for remote " + label + " and found "
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
			logger.info("Deleting stale JGit lock file at " + lock);
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
		if (this.gitCredentialsProvider != null) {
			return this.gitCredentialsProvider;
		}

		if (hasText(getUsername()) && hasText(getPassword())) {
			return new UsernamePasswordCredentialsProvider(getUsername(), getPassword());
		}

		if (hasText(getPassphrase())) {
			return new PassphraseCredentialsProvider(getPassphrase());
		}

		return null;
	}

	private boolean isClean(Git git, String label) {
		StatusCommand status = git.status();
		try {
			BranchTrackingStatus trackingStatus = BranchTrackingStatus.of(git.getRepository(), label);
			boolean isBranchAhead = trackingStatus != null && trackingStatus.getAheadCount() > 0;
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
		logger.warn(message);
		if (logger.isDebugEnabled()) {
			logger.debug("Stacktrace for: " + message, ex);
		}
	}

	/**
	 * Wraps the static method calls to {@link org.eclipse.jgit.api.Git} and
	 * {@link org.eclipse.jgit.api.CloneCommand} allowing for easier unit testing.
	 */
	static class JGitFactory {

		public Git getGitByOpen(File file) throws IOException {
			Git git = Git.open(file);
			return git;
		}

		public CloneCommand getCloneCommandByCloneRepository() {
			CloneCommand command = Git.cloneRepository();
			return command;
		}
	}

	/**
	 * @return the gitCredentialsProvider
	 */
	public CredentialsProvider getGitCredentialsProvider() {
		return gitCredentialsProvider;
	}

	/**
	 * @param gitCredentialsProvider the gitCredentialsProvider to set
	 */
	public void setGitCredentialsProvider(CredentialsProvider gitCredentialsProvider) {
		this.gitCredentialsProvider = gitCredentialsProvider;
	}
}
