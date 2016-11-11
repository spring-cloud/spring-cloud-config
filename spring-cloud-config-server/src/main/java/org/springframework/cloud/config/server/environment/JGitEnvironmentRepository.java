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
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FileUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.config.server.support.PassphraseCredentialsProvider;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.UrlResource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.jcraft.jsch.Session;

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
	 * The credentials provider to use to connect to the Git repository.
	 */
	private CredentialsProvider gitCredentialsProvider;

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
	private String refresh(String label) {
		initialize();
		Git git = null;
		try {
			git = createGitClient();
			if (shouldPull(git)) {
				fetch(git, label);
				//checkout after fetch so we can get any new branches, tags, ect.
				checkout(git, label);
				if(isBranch(git, label)) {
					//merge results from fetch
					merge(git, label);
					if (!isClean(git)) {
						logger.warn("The local repository is dirty. Resetting it to origin/"
								+ label + ".");
						resetHard(git, label, "refs/remotes/origin/" + label);
					}
				}
			}
			else{
				//nothing to update so just checkout
				checkout(git, label);
			}
			//always return what is currently HEAD as the version
			return git.getRepository().getRef("HEAD").getObjectId().getName();
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


	public /*public for testing*/ boolean shouldPull(Git git) throws GitAPIException {
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

	private FetchResult fetch(Git git, String label) {
		FetchCommand fetch = git.fetch();
		fetch.setRemote("origin");
		fetch.setTagOpt(TagOpt.FETCH_TAGS);

		setTimeout(fetch);
		try {
			setCredentialsProvider(fetch);
			FetchResult result = fetch.call();
			if(result.getTrackingRefUpdates() != null && result.getTrackingRefUpdates().size() > 0) {
				this.logger.info("Fetched for remote " + label + " and found " + result.getTrackingRefUpdates().size()
					+ " updates");
			}
			return result;
		}
		catch (Exception ex) {
			this.logger.warn("Could not fetch remote for " + label + " remote: " + git
					.getRepository().getConfig().getString("remote", "origin", "url"));
			return null;
		}
	}

	private MergeResult merge(Git git, String label) {
		try {
			MergeCommand merge = git.merge();
			merge.include(git.getRepository().getRef("origin/" + label));
			MergeResult result = merge.call();
			if(!result.getMergeStatus().isSuccessful()) {
				this.logger.warn("Merged from remote " + label + " with result " + result.getMergeStatus());
			}
			return result;
		}
		catch (Exception ex) {
			this.logger.warn("Could not merge remote for " + label + " remote: " + git
					.getRepository().getConfig().getString("remote", "origin", "url"));
			return null;
		}
	}

	private Ref resetHard(Git git, String label, String ref) {
		ResetCommand reset = git.reset();
		reset.setRef(ref);
		reset.setMode(ResetType.HARD);
		try {
			Ref resetRef = reset.call();
			if(resetRef != null) {
				this.logger.info("Reset label " + label + " to version " + resetRef.getObjectId());
			}
			return resetRef;
		}
		catch (Exception ex) {
			this.logger.warn("Could not reset to remote for " + label + " (current ref="
					+ ref + "), remote: " + git.getRepository().getConfig()
							.getString("remote", "origin", "url"));
			return null;
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
		setCredentialsProvider(clone);
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
			try {
				FileUtils.delete(getBasedir(), FileUtils.RECURSIVE);
			}
			catch (IOException e) {
				throw new IllegalStateException("Failed to initialize base directory", e);
			}
		}
	}

	private void initialize() {
		if (!this.initialized) {
			SshSessionFactory.setInstance(new JschConfigSessionFactory() {
				@Override
				protected void configure(Host hc, Session session) {
					session.setConfig("StrictHostKeyChecking", isStrictHostKeyChecking() ? "yes" : "no");
				}
			});
			this.initialized = true;
		}
	}

	private void setCredentialsProvider(TransportCommand<?, ?> cmd) {
		if (gitCredentialsProvider != null) {
			cmd.setCredentialsProvider(gitCredentialsProvider);
		} else if (hasText(getUsername())) {
			cmd.setCredentialsProvider(
					new UsernamePasswordCredentialsProvider(getUsername(), getPassword()));
		} else if (hasText(getPassphrase())) {
			cmd.setCredentialsProvider(
					new PassphraseCredentialsProvider(getPassphrase()));
		}
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
