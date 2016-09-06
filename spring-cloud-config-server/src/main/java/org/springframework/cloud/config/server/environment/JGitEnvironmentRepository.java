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

import static org.springframework.util.StringUtils.hasText;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FileUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.UrlResource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.jcraft.jsch.Session;

/**
 * An {@link EnvironmentRepository} backed by a single git repository.
 *
 * @author Dave Syer
 * @author Roy Clarkson
 * @author Marcos Barbero
 * @author Daniel Lavoie
 */
public class JGitEnvironmentRepository extends AbstractScmEnvironmentRepository
		implements EnvironmentRepository, SearchPathLocator, InitializingBean {

	private static final String DEFAULT_LABEL = "master";
	private static final String FILE_URI_PREFIX = "file:";

	/**
	 * Timeout (in seconds) for obtaining HTTP or SSH connection (if applicable). Default
	 * 5 seconds.
	 */
	private int timeout = 5;

	private boolean initialized;

	/**
	 * Flag to indicate that the repository should be cloned on startup (not on demand).
	 * Generally leads to slower startup but faster first query.
	 */
	private boolean cloneOnStart = false;

	private JGitEnvironmentRepository.JGitFactory gitFactory = new JGitEnvironmentRepository.JGitFactory();

	private String defaultLabel = DEFAULT_LABEL;

	/**
	 * Flag to indicate that the repository should force pull. If true discard any local
	 * changes and take from remote repository.
	 */
	private boolean forcePull;

	public JGitEnvironmentRepository(ConfigurableEnvironment environment) {
		super(environment);
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

	@Override
	public synchronized Locations getLocations(String application, String profile,
			String label) {
		if (label == null) {
			label = this.defaultLabel;
		}
		Ref ref = refresh(application, label);
		String version = null;
		if (ref != null) {
			version = ref.getObjectId().getName();
		}
		return new Locations(application, profile, label, version,
				getSearchLocations(getWorkingDirectory(), application, profile, label));
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		Assert.state(getUri() != null,
				"You need to configure a uri for the git repository");
		if (this.cloneOnStart) {
			initClonedRepository();
		}
	}

	/**
	 * Get the working directory ready.
	 */
	private Ref refresh(String application, String label) {
		initialize();
		Git git = null;
		try {
			git = createGitClient();
			git.getRepository().getConfig().setString("branch", label, "merge", label);
			Ref ref = checkout(git, label);
			if (shouldPull(git, ref)) {
				pull(git, label, ref);

				if (!isClean(git)) {
					logger.warn("The local repository is dirty. Reseting it to origin/"
							+ label + ".");

					fetch(git, label, "origin");
					resetHard(git, label, "refs/remotes/origin/" + label);
				}
			}
			return ref;
		}
		catch (RefNotFoundException e) {
			throw new NoSuchLabelException("No such label: " + label);
		}
		catch (GitAPIException e) {
			throw new IllegalStateException("Cannot clone or checkout repository", e);
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

	/* for testing */ boolean shouldPull(Git git, Ref ref) throws GitAPIException {
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
			shouldPull = isWorkingTreeClean && ref != null && originUrl != null;
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
		this.logger.warn(String.format("Dirty files found: %s", dirties));
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

	private void fetch(Git git, String label, String remote) {
		FetchCommand fetch = git.fetch().setRemote(remote);
		setTimeout(fetch);
		try {
			if (hasText(getUsername())) {
				setCredentialsProvider(fetch);
			}

			fetch.call();
		}
		catch (Exception ex) {
			this.logger.warn("Could not fetch remote for " + label + " remote: " + git
					.getRepository().getConfig().getString("remote", "origin", "url"));
		}
	}

	private void resetHard(Git git, String label, String ref) {
		ResetCommand reset = git.reset();
		reset.setRef(ref);
		reset.setMode(ResetType.HARD);
		try {
			reset.call();
		}
		catch (Exception ex) {
			this.logger.warn("Could not reset to remote for " + label + " (current ref="
					+ ref + "), remote: " + git.getRepository().getConfig()
							.getString("remote", "origin", "url"));
		}
	}

	/**
	 * Assumes we are on a tracking branch (should be safe)
	 */
	private void pull(Git git, String label, Ref ref) {
		PullCommand pull = git.pull();
		setTimeout(pull);
		try {
			if (hasText(getUsername())) {
				setCredentialsProvider(pull);
			}
			pull.call();
		}
		catch (Exception e) {
			this.logger
					.warn("Could not pull remote for " + label + " (current ref=" + ref
							+ "), remote: "
							+ git.getRepository().getConfig().getString("remote",
									"origin", "url")
							+ ", cause: (" + e.getClass().getSimpleName() + ") "
							+ e.getMessage());
		}
	}

	private Git createGitClient() throws IOException, GitAPIException {
		if (new File(getBasedir(), ".git").exists()) {
			return openGitRepository();
		}
		else {
			return copyRepository();
		}
	}

	// Synchronize here so that multiple requests don't all try and delete the base dir
	// together (this is a once only operation, so it only holds things up on the first
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
		setTimeout(clone);
		if (hasText(getUsername())) {
			setCredentialsProvider(clone);
		}
		return clone.call();
	}

	private void deleteBaseDirIfExists() {
		if (getBasedir().exists()) {
			try {
				FileUtils.delete(getBasedir(), FileUtils.RECURSIVE);
			}
			catch (IOException e) {
				throw new IllegalStateException("Failed to initialize base directory", e);
			}
		}
	}

	private void initialize() {
		if (getUri().startsWith("file:") && !this.initialized) {
			SshSessionFactory.setInstance(new JschConfigSessionFactory() {
				@Override
				protected void configure(Host hc, Session session) {
					session.setConfig("StrictHostKeyChecking", "no");
				}
			});
			this.initialized = true;
		}
	}

	private void setCredentialsProvider(TransportCommand<?, ?> cmd) {
		cmd.setCredentialsProvider(
				new UsernamePasswordCredentialsProvider(getUsername(), getPassword()));
	}

	private void setTimeout(TransportCommand<?, ?> pull) {
		pull.setTimeout(this.timeout);
	}

	private boolean isClean(Git git) {
		StatusCommand status = git.status();
		try {
			return status.call().isClean();
		}
		catch (Exception e) {
			this.logger
					.warn("Could not execute status command on local repository. Cause: ("
							+ e.getClass().getSimpleName() + ") " + e.getMessage());

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
}
