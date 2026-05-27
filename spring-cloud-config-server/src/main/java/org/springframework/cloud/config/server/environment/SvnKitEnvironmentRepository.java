/*
 * Copyright 2013-present the original author or authors.
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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;

import io.micrometer.observation.ObservationRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUpdate;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.config.server.support.ScmFileUtils.assertDirectoryStillResolvesTo;
import static org.springframework.cloud.config.server.support.ScmFileUtils.assertNoSymlinkInPath;
import static org.springframework.cloud.config.server.support.ScmFileUtils.recreateSecureDirectory;
import static org.springframework.util.StringUtils.hasText;

/**
 * Subversion-backed {@link EnvironmentRepository}.
 *
 * @author Michael Prankl
 * @author Roy Clarkson
 */
public class SvnKitEnvironmentRepository extends AbstractScmEnvironmentRepository
		implements EnvironmentRepository, InitializingBean {

	private static Log logger = LogFactory.getLog(SvnKitEnvironmentRepository.class);

	/**
	 * The default label for environment properties requests.
	 */
	private String defaultLabel;

	public SvnKitEnvironmentRepository(ConfigurableEnvironment environment, SvnKitEnvironmentProperties properties,
			ObservationRegistry observationRegistry) {
		super(environment, properties, observationRegistry);
		this.defaultLabel = properties.getDefaultLabel();
	}

	public String getDefaultLabel() {
		return this.defaultLabel;
	}

	public void setDefaultLabel(String defaultLabel) {
		this.defaultLabel = defaultLabel;
	}

	@Override
	public synchronized Locations getLocations(String application, String profile, String label) {
		if (label == null) {
			label = this.defaultLabel;
		}
		if (getUri().toLowerCase(Locale.ROOT).startsWith("file:")) {
			validateLocalFileUri();
		}
		SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
		if (hasText(getUsername())) {
			svnOperationFactory.setAuthenticationManager(
					new DefaultSVNAuthenticationManager(null, false, getUsername(), getPassword()));
		}
		try {
			String version;
			if (isSvnWorkingCopyPresent()) {
				version = update(svnOperationFactory, label);
			}
			else {
				version = checkout(svnOperationFactory);
			}
			return new Locations(application, profile, label, version, getPaths(application, profile, label));
		}
		catch (SVNException e) {
			throw new IllegalStateException("Cannot checkout repository", e);
		}
		finally {
			svnOperationFactory.dispose();
		}
	}

	private String[] getPaths(String application, String profile, String label) {
		String[] locations = getSearchLocations(getSvnPath(getWorkingDirectory(), label), application, profile, label);
		boolean exists = false;
		for (String location : locations) {
			location = StringUtils.cleanPath(location);
			URI locationUri = URI.create(location);
			// TODO document that symlinks are no longer allowed for SVN repos
			if (Files.isDirectory(Path.of(locationUri), LinkOption.NOFOLLOW_LINKS)) {
				exists = true;
				break;
			}
		}
		if (!exists) {
			throw new NoSuchLabelException("No label found for: " + label);
		}
		return locations;
	}

	private String checkout(SvnOperationFactory svnOperationFactory) throws SVNException {
		logger.debug("Checking out " + getUri() + " to: " + getWorkingDirectory().getAbsolutePath());
		Path workdir;
		try {
			workdir = recreateSecureDirectory(getWorkingDirectory());
			// Verify path didn't change between prepare and checkout to narrow the TOCTOU
			// window.
			assertDirectoryStillResolvesTo(getWorkingDirectory(), workdir);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not prepare working directory for SVN checkout", ex);
		}
		final SvnCheckout checkout = svnOperationFactory.createCheckout();
		checkout.setSource(SvnTarget.fromURL(SVNURL.parseURIEncoded(getUri())));
		checkout.setSingleTarget(SvnTarget.fromFile(workdir.toFile()));
		Long id = checkout.run();
		if (id == null) {
			return null;
		}
		return id.toString();
	}

	private String update(SvnOperationFactory svnOperationFactory, String label) throws SVNException {
		logger.debug("Repo already checked out - updating instead.");

		// Re-verify immediately before invoking SVNKit to narrow the TOCTOU window
		// between the isSvnWorkingCopyPresent() check and the actual SVN operation.
		// Files.isDirectory with NOFOLLOW_LINKS returns false for symlinks, so a
		// concurrent swap of the working directory to a symlink is caught here.
		Path workdir = getWorkingDirectory().toPath().toAbsolutePath().normalize();
		if (!Files.isDirectory(workdir, LinkOption.NOFOLLOW_LINKS)) {
			throw new IllegalStateException("SVN working directory is no longer a real directory before update"
					+ " (possible symlink substitution): " + workdir);
		}

		try {
			final SvnUpdate update = svnOperationFactory.createUpdate();
			update.setSingleTarget(SvnTarget.fromFile(workdir.toFile()));
			long[] ids = update.run();
			StringBuilder version = new StringBuilder();
			for (long id : ids) {
				if (version.length() > 0) {
					version.append(",");
				}
				version.append(id);
			}
			return version.toString();
		}
		catch (Exception e) {
			String message = "Could not update remote for " + label + " (current local=" + workdir + ", remote: "
					+ this.getUri() + ")";
			if (logger.isDebugEnabled()) {
				logger.debug(message, e);
			}
			else if (logger.isWarnEnabled()) {
				logger.warn(message);
			}
		}

		final SVNStatus status = SVNClientManager.newInstance().getStatusClient().doStatus(workdir.toFile(), false);
		return status != null ? status.getRevision().toString() : null;
	}

	/**
	 * Returns {@code true} when a real (non-symlink) SVN working copy is present in
	 * {@link #getWorkingDirectory()}. Rejects a symbolic link at the working-directory
	 * root or at the {@code .svn} metadata entry to prevent path-substitution attacks
	 * where an external actor swaps the directory between the existence check and the
	 * subsequent SVNKit operation.
	 */
	private boolean isSvnWorkingCopyPresent() {
		Path workdir = getWorkingDirectory().toPath().toAbsolutePath().normalize();
		if (Files.isSymbolicLink(workdir)) {
			throw new IllegalStateException("SVN working directory must not be a symbolic link: " + workdir);
		}
		if (!Files.isDirectory(workdir, LinkOption.NOFOLLOW_LINKS)) {
			return false;
		}
		Path svnMetaDir = workdir.resolve(".svn");
		if (Files.isSymbolicLink(svnMetaDir)) {
			throw new IllegalStateException(
					"SVN working directory .svn entry must not be a symbolic link: " + svnMetaDir);
		}
		return Files.isDirectory(svnMetaDir, LinkOption.NOFOLLOW_LINKS);
	}

	/**
	 * Validates a {@code file:} SVN source-repository URI: rejects a symbolic link at the
	 * repository root so SVNKit cannot be redirected to an unexpected local repository
	 * via a symlink substitution.
	 */
	private void validateLocalFileUri() {
		Path repoPath;
		try {
			repoPath = Path.of(URI.create(StringUtils.cleanPath(getUri()))).toAbsolutePath().normalize();
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException("Cannot resolve local SVN repository URI: " + getUri(), ex);
		}
		// If the path does not exist (e.g. repository was deleted after the initial
		// checkout), skip the symlink checks and let SVNKit report the missing repo.
		if (!Files.exists(repoPath, LinkOption.NOFOLLOW_LINKS)) {
			return;
		}
		// Walk every component from root to leaf. Files.isSymbolicLink only checks the
		// leaf, and toRealPath(NOFOLLOW_LINKS) silently follows intermediate symlinks on
		// Linux/macOS rather than failing, so component-by-component checking is needed.
		assertNoSymlinkInPath(repoPath);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		Assert.state(getUri() != null,
				"You need to configure a uri for the subversion repository (e.g. 'https://example.com/svn/')");
		resolveRelativeFileUri();
	}

	private void resolveRelativeFileUri() {
		if (getUri().startsWith("file:///./")) {
			String path = getUri().substring(8);
			String absolutePath = new File(path).getAbsolutePath();
			setUri("file:///" + StringUtils.cleanPath(absolutePath));
		}

	}

	@Override
	protected File getWorkingDirectory() {
		return this.getBasedir();
	}

	private File getSvnPath(File workingDirectory, String label) {
		// use label as path relative to repository root
		// if it doesn't exist check branches and then tags folders
		File svnPath = new File(workingDirectory, label);
		if (!Files.isDirectory(svnPath.toPath(), LinkOption.NOFOLLOW_LINKS)) {
			svnPath = new File(workingDirectory, "branches" + File.separator + label);
			if (!Files.isDirectory(svnPath.toPath(), LinkOption.NOFOLLOW_LINKS)) {
				svnPath = new File(workingDirectory, "tags" + File.separator + label);
				if (!Files.isDirectory(svnPath.toPath(), LinkOption.NOFOLLOW_LINKS)) {
					throw new NoSuchLabelException("No label found for: " + label);
				}
			}
		}
		return svnPath;
	}

	@Override
	public void setOrder(int order) {
		super.setOrder(order);
	}

}
