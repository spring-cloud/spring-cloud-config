/*
 * Copyright 2013-2014 the original author or authors.
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

import static org.springframework.util.StringUtils.hasText;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.springframework.cloud.config.Environment;
import org.springframework.cloud.config.PropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.UrlResource;
import org.springframework.util.Assert;
import org.springframework.util.FileSystemUtils;

import com.jcraft.jsch.Session;

/**
 * @author Dave Syer
 *
 */
public class JGitEnvironmentRepository implements EnvironmentRepository {

	public static final String DEFAULT_URI = "https://github.com/spring-cloud-samples/config-repo";

	private static Log logger = LogFactory.getLog(JGitEnvironmentRepository.class);

	private File basedir;

	private String uri = DEFAULT_URI;

	private ConfigurableEnvironment environment;

	private String username;

	private String password;

	private boolean initialized;

	private String[] searchPaths = new String[0];

	public JGitEnvironmentRepository(ConfigurableEnvironment environment) {
		this.environment = environment;
		try {
			final File basedir = Files.createTempDirectory("config-repo-").toFile();
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					try {
						FileUtils.delete(basedir, FileUtils.RECURSIVE);
					}
					catch (IOException e) {
						logger.warn("Failed to delete temporary directory on exit: " + e);
					}
				}
			});
			this.basedir = basedir;
		}
		catch (IOException e) {
			throw new IllegalStateException("Cannot create temp dir", e);
		}
	}

	public void setUri(String uri) {
		while (uri.endsWith("/")) {
			uri = uri.substring(0, uri.length() - 1);
		}
		this.uri = uri;
	}

	public String getUri() {
		return uri;
	}

	public void setBasedir(File basedir) {
		this.basedir = basedir.getAbsoluteFile();
	}

	public File getBasedir() {
		return basedir;
	}
	
	public void setSearchPaths(String... searchPaths) {
		this.searchPaths = searchPaths;
	}
	
	public String[] getSearchPaths() {
		return searchPaths;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		initialize();
		try {
			final Git git = createGitClient();
			return loadEnvironment(git, application, profile, label);
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot clone repository", e);
		}
	}

	private synchronized Environment loadEnvironment(Git git, String application, String profile, String label) throws GitAPIException {
		SpringApplicationEnvironmentRepository environment = new SpringApplicationEnvironmentRepository();
		git.getRepository().getConfig().setString("branch", label, "merge", label);
		Ref ref = checkout(git, label);
		if (shouldPull(git, ref)) {
			pull(git, label, ref);
		}
		environment.setSearchLocations(getSearchLocations(basedir));
		return clean(environment.findOne(application, profile, label));
	}

	private String[] getSearchLocations(File dir) {
		List<String> locations = new ArrayList<String>();
		locations.add(dir.toURI().toString());
		for (String path : searchPaths) {
			File file = new File(basedir, path);
			if (file.isDirectory()) {
				locations.add(file.toURI().toString());
			}
		}
		return locations.toArray(new String[0]);
	}

	private Ref checkout(Git git, String label) throws GitAPIException {
		CheckoutCommand checkout = git.checkout();
		if (shouldTrack(git, label)) {
			trackBranch(git, checkout, label);
		} else {
			// works for tags and local branches
			checkout.setName(label);
		}
		return checkout.call();
	}

	private boolean shouldPull(Git git, Ref ref) throws GitAPIException {
		return git.status().call().isClean() && ref != null;
	}

	private boolean shouldTrack(Git git, String label) throws GitAPIException {
		return isBranch(git, label) && !isLocalBranch(git, label);
	}

	/**
	 * Assumes we are on a tracking branch (should be safe)
	 */
	private void pull(Git git, String label, Ref ref) {
		try {
			PullCommand pull = git.pull();
			if (hasText(username)) {
				setCredentialsProvider(pull);
			}
			pull.call();
		}
		catch (Exception e) {
			logger.warn("Could not pull remote for " + label
					+ " (current ref=" + ref + ")");
		}
	}

	private Git createGitClient() throws IOException, GitAPIException {
		if (new File(basedir, ".git").exists()) {
			return openGitRepository();
		} else {
			return copyRepository();
		}
	}

	private Git copyRepository() throws IOException, GitAPIException {
		deleteBaseDirIfExists();
		Assert.state(basedir.mkdirs(), "Could not create basedir: " + basedir);
		if (uri.startsWith("file:")) {
			return copyFromLocalRepository();
		}
		else {
			return cloneToBasedir();
		}
	}

	private Git openGitRepository() throws IOException {
		Git git = Git.open(basedir);
		tryFetch(git);
		return git;
	}

	private Git copyFromLocalRepository() throws IOException {
		Git git;
		FileSystemUtils.copyRecursively(new UrlResource(uri).getFile(),
				basedir);
		git = Git.open(basedir);
		return git;
	}

	private Git cloneToBasedir() throws GitAPIException {
		CloneCommand clone = Git.cloneRepository().setURI(uri)
				.setDirectory(basedir);
		if (hasText(username)) {
			setCredentialsProvider(clone);
		}
		return clone.call();
	}

	private void tryFetch(Git git) {
		try {
			FetchCommand fetch = git.fetch();
			if (hasText(username)) {
				setCredentialsProvider(fetch);
			}
			fetch.call();
		}
		catch (Exception e) {
			logger.warn("Remote repository not available");
		}
	}

	private void deleteBaseDirIfExists() {
		if (basedir.exists()) {
			try {
				FileUtils.delete(basedir, FileUtils.RECURSIVE);
			}
			catch (IOException e) {
				throw new IllegalStateException(
						"Failed to initialize base directory", e);
			}
		}
	}

	private void initialize() {
		if (uri.startsWith("file:") && !initialized) {
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
		cmd.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username,
				password));
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

	private Environment clean(Environment value) {
		Environment result = new Environment(value.getName(), value.getLabel());
		for (PropertySource source : value.getPropertySources()) {
			String name = source.getName().replace(basedir.toURI().toString(), "");
			if (name.contains(("classpath:/"))) {
				continue;
			}
			if (environment.getPropertySources().contains(name)) {
				continue;
			}
			name = name.replace("applicationConfig: [", "");
			name = uri + "/" + name.replace("]", "");
			result.add(new PropertySource(name, source.getSource()));
		}
		return result;
	}
}
