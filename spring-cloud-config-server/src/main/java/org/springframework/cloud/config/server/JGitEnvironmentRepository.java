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

package org.springframework.cloud.config.server;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.jcraft.jsch.Session;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FileUtils;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.UrlResource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import static org.springframework.util.StringUtils.hasText;

/**
 * An {@link EnvironmentRepository} backed by a single git repository.
 * 
 * @author Dave Syer
 * @author Roy Clarkson
 */
public class JGitEnvironmentRepository extends AbstractScmEnvironmentRepository {

	private static Log logger = LogFactory.getLog(JGitEnvironmentRepository.class);

	private static final String DEFAULT_LABEL = "master";

	private boolean initialized;

	public JGitEnvironmentRepository(ConfigurableEnvironment environment) {
		super(environment);
	}

	@Override
	public String getDefaultLabel() {
		return DEFAULT_LABEL;
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		initialize();
		Git git = null;
		try {
			git = createGitClient();
			return loadEnvironment(git, application, profile, label);
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
					git.getRepository().close();
				}
			}
			catch (Exception e) {
				logger.warn("Could not close git repository", e);
			}
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		Assert.state(getUri() != null,
				"You need to configure a uri for the git repository");
	}

	private synchronized Environment loadEnvironment(Git git, String application,
			String profile, String label) throws GitAPIException {
		NativeEnvironmentRepository environment = new NativeEnvironmentRepository(getEnvironment());
		git.getRepository().getConfig().setString("branch", label, "merge", label);
		Ref ref = checkout(git, label);
		if (shouldPull(git, ref)) {
			pull(git, label, ref);
		}
		environment.setSearchLocations(getSearchLocations(getWorkingDirectory()));
		Environment result = environment.findOne(application, profile, "");
		result.setLabel(label);
		return clean(result);
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

	private boolean shouldPull(Git git, Ref ref) throws GitAPIException {
		return git.status().call().isClean()
				&& ref != null
				&& git.getRepository().getConfig().getString("remote", "origin", "url") != null;
	}

	private boolean shouldTrack(Git git, String label) throws GitAPIException {
		return isBranch(git, label) && !isLocalBranch(git, label);
	}

	/**
	 * Assumes we are on a tracking branch (should be safe)
	 */
	private void pull(Git git, String label, Ref ref) {
		PullCommand pull = git.pull();
		try {
			if (hasText(getUsername())) {
				setCredentialsProvider(pull);
			}
			pull.call();
		}
		catch (Exception e) {
			logger.warn("Could not pull remote for "
					+ label
					+ " (current ref="
					+ ref
					+ "), remote: "
					+ git.getRepository().getConfig()
							.getString("remote", "origin", "url"));
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

	private Git copyRepository() throws IOException, GitAPIException {
		deleteBaseDirIfExists();
		Assert.state(getBasedir().mkdirs(), "Could not create basedir: " + getBasedir());
		if (getUri().startsWith("file:")) {
			return copyFromLocalRepository();
		}
		else {
			return cloneToBasedir();
		}
	}

	private Git openGitRepository() throws IOException {
		Git git = Git.open(getWorkingDirectory());
		tryFetch(git);
		return git;
	}

	private Git copyFromLocalRepository() throws IOException {
		Git git;
		File remote = new UrlResource(StringUtils.cleanPath(getUri())).getFile();
		Assert.state(remote.isDirectory(), "No directory at " + getUri());
		File gitDir = new File(remote, ".git");
		Assert.state(gitDir.exists(), "No .git at " + getUri());
		Assert.state(gitDir.isDirectory(), "No .git directory at " + getUri());
		git = Git.open(remote);
		return git;
	}

	private Git cloneToBasedir() throws GitAPIException {
		CloneCommand clone = Git.cloneRepository().setURI(getUri())
				.setDirectory(getBasedir());
		if (hasText(getUsername())) {
			setCredentialsProvider(clone);
		}
		return clone.call();
	}

	private void tryFetch(Git git) {
		try {
			FetchCommand fetch = git.fetch();
			if (hasText(getUsername())) {
				setCredentialsProvider(fetch);
			}
			fetch.call();
		}
		catch (Exception e) {
			logger.warn("Remote repository not available");
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
		if (getUri().startsWith("file:") && !initialized) {
			SshSessionFactory.setInstance(new JschConfigSessionFactory() {
				@Override
				protected void configure(Host hc, Session session) {
					session.setConfig("StrictHostKeyChecking", "no");
				}
			});
			initialized = true;
		}
	}

	private void setCredentialsProvider(TransportCommand<?, ?> cmd) {
		cmd.setCredentialsProvider(new UsernamePasswordCredentialsProvider(getUsername(),
				getPassword()));
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
}
