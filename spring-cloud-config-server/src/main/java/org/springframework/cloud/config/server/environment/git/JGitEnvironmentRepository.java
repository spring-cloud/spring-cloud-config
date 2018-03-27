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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;

import com.jcraft.jsch.Session;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FileUtils;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.config.server.environment.AbstractScmEnvironmentRepository;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.environment.NoSuchLabelException;
import org.springframework.cloud.config.server.environment.NoSuchRepositoryException;
import org.springframework.cloud.config.server.environment.SearchPathLocator;
import org.springframework.cloud.config.server.environment.git.command.JGitCommandConfigurer;
import org.springframework.cloud.config.server.environment.git.command.JGitCommandExecutor;
import org.springframework.cloud.config.server.environment.git.command.JGitFactory;
import org.springframework.cloud.config.server.support.PassphraseCredentialsProvider;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.UrlResource;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import static java.lang.String.format;
import static java.util.stream.Collectors.toSet;
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

	protected JGitCommandExecutor jGitCommandExecutor;

	protected JGitCommandConfigurer jGitCommandConfigurer;

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

	private JGitFactory gitFactory = new JGitFactory();

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

	public CredentialsProvider getGitCredentialsProvider() {
		return gitCredentialsProvider;
	}

	public void setGitCredentialsProvider(CredentialsProvider gitCredentialsProvider) {
		this.gitCredentialsProvider = gitCredentialsProvider;
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
		Assert.state(getUri() != null, "You need to configure a uri for the git repository");
		initHelpers();
		initialize();
		if (this.cloneOnStart) {
			initClonedRepository();
		}
	}

	public void initHelpers() {
		jGitCommandConfigurer = new JGitCommandConfigurer(timeout, transportConfigCallback, getCredentialsProvider());
		jGitCommandExecutor = new JGitCommandExecutor(jGitCommandConfigurer);
	}

	/**
	 * Get the working directory ready.
	 */
	private String refresh(String label) {
		try (Git git = createGitClient()) {
			boolean pulled = pullFromRemote(label, git);
			if (!pulled) {
				//if case if remote pull failed or disabled
				jGitCommandExecutor.checkout(git, label);
			}
			// always return what is currently HEAD as the version
			return git.getRepository().findRef("HEAD").getObjectId().getName();
		} catch (RefNotFoundException e) {
			throw new NoSuchLabelException("No such label: " + label, e);
		} catch (NoRemoteRepositoryException e) {
			throw new NoSuchRepositoryException("No such repository: " + getUri(), e);
		} catch (GitAPIException e) {
			throw new NoSuchRepositoryException(
					"Cannot clone or checkout repository: " + getUri(), e);
		} catch (Exception e) {
			throw new IllegalStateException("Cannot load environment", e);
		}
	}

	private boolean pullFromRemote(String label, Git git) throws GitAPIException {
		if (shouldPull(git)) {
			FetchResult fetchStatus = jGitCommandExecutor.safeFetch(git, label, deleteUntrackedBranches);
			if (deleteUntrackedBranches) {
				deleteUntrackedLocalBranches(fetchStatus.getTrackingRefUpdates(), git);
			}
			// checkout after fetch so we can get any new branches, tags, ect.
			jGitCommandExecutor.checkout(git, label);
			if (jGitCommandExecutor.isBranch(git, label)) {
				// merge results from fetch
				jGitCommandExecutor.safeMerge(git, label);
				if (!jGitCommandExecutor.isClean(git, label)) {
					logger.warn("The local repository is dirty or ahead of origin. Resetting"
							+ " it to origin/" + label + ".");
					jGitCommandExecutor.safeHardReset(git, label, LOCAL_BRANCH_REF_PREFIX + label);
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * Clones the remote repository and then opens a connection to it.
	 *
	 * @throws GitAPIException
	 * @throws IOException
	 */
	private void initClonedRepository() throws GitAPIException, IOException {
		if (!getUri().startsWith(FILE_URI_PREFIX)) {
			purgeBaseDirIfExists();
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
					String localBranchName = localRefName.substring(LOCAL_BRANCH_REF_PREFIX.length());
					branchesToDelete.add(localBranchName);
				}
			}
		}

		if (CollectionUtils.isEmpty(branchesToDelete)) {
			return Collections.emptyList();
		}

		return jGitCommandExecutor.safeDeleteBranches(git, branchesToDelete, defaultLabel);
	}

	protected boolean shouldPull(Git git) throws GitAPIException {
		boolean shouldPull;
		Status gitStatus = jGitCommandExecutor.status(git);
		boolean isWorkingTreeClean = gitStatus.isClean();
		String originUrl = git.getRepository().getConfig().getString("remote", "origin", "url");

		if (this.forcePull && !isWorkingTreeClean) {
			shouldPull = true;
			logDirty(gitStatus);
		} else {
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
		Set<String> dirties = Stream.of(status.getAdded(), status.getChanged(),
				status.getRemoved(), status.getMissing(), status.getModified(),
				status.getConflicting(), status.getUntracked())
				.flatMap(Collection::stream)
				.collect(toSet());
		this.logger.warn(format("Dirty files found: %s", dirties));
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
		} else {
			return copyRepository();
		}
	}

	// Synchronize here so that multiple requests don't all try and delete the
	// base dir
	// together (this is a once only operation, so it only holds things up on
	// the first
	// request).
	private synchronized Git copyRepository() throws IOException, GitAPIException {
		purgeBaseDirIfExists();
		getBasedir().mkdirs();
		Assert.state(getBasedir().exists(), "Could not create basedir: " + getBasedir());
		if (getUri().startsWith(FILE_URI_PREFIX)) {
			return copyFromLocalRepository();
		} else {
			return cloneToBasedir();
		}
	}

	private Git openGitRepository() throws IOException {
		return this.gitFactory.getGitByOpen(getWorkingDirectory());
	}

	private Git copyFromLocalRepository() throws IOException {
		File remote = new UrlResource(StringUtils.cleanPath(getUri())).getFile();
		Assert.state(remote.isDirectory(), "No directory at " + getUri());
		File gitDir = new File(remote, ".git");
		Assert.state(gitDir.exists(), "No .git at " + getUri());
		Assert.state(gitDir.isDirectory(), "No .git directory at " + getUri());
		return gitFactory.getGitByOpen(remote);
	}

	private Git cloneToBasedir() throws GitAPIException {
		CloneCommand clone = gitFactory.getCloneCommandByCloneRepository()
				.setURI(getUri()).setDirectory(getBasedir());
		jGitCommandConfigurer.configureCommand(clone);
		try {
			return clone.call();
		} catch (GitAPIException e) {
			purgeBaseDirIfExists();
			throw e;
		}
	}

	private void purgeBaseDirIfExists() {
		if (getBasedir().exists()) {
			for (File file : getBasedir().listFiles()) {
				try {
					FileUtils.delete(file, FileUtils.RECURSIVE);
				} catch (IOException e) {
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

}
