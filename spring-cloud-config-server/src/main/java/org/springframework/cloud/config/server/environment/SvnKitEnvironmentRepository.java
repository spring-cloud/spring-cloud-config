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
import java.net.URI;

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

	public SvnKitEnvironmentRepository(ConfigurableEnvironment environment,
			SvnKitEnvironmentProperties properties) {
		super(environment, properties);
		this.defaultLabel = properties.getDefaultLabel();
	}

	public String getDefaultLabel() {
		return this.defaultLabel;
	}

	public void setDefaultLabel(String defaultLabel) {
		this.defaultLabel = defaultLabel;
	}

	@Override
	public synchronized Locations getLocations(String application, String profile,
			String label) {
		if (label == null) {
			label = this.defaultLabel;
		}
		SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
		if (hasText(getUsername())) {
			svnOperationFactory
					.setAuthenticationManager(new DefaultSVNAuthenticationManager(null,
							false, getUsername(), getPassword()));
		}
		try {
			String version;
			if (new File(getWorkingDirectory(), ".svn").exists()) {
				version = update(svnOperationFactory, label);
			}
			else {
				version = checkout(svnOperationFactory);
			}
			return new Locations(application, profile, label, version,
					getPaths(application, profile, label));
		}
		catch (SVNException e) {
			throw new IllegalStateException("Cannot checkout repository", e);
		}
		finally {
			svnOperationFactory.dispose();
		}
	}

	private String[] getPaths(String application, String profile, String label) {
		String[] locations = getSearchLocations(getSvnPath(getWorkingDirectory(), label),
				application, profile, label);
		boolean exists = false;
		for (String location : locations) {
			location = StringUtils.cleanPath(location);
			URI locationUri = URI.create(location);
			if (new File(locationUri).exists()) {
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
		logger.debug("Checking out " + getUri() + " to: "
				+ getWorkingDirectory().getAbsolutePath());
		final SvnCheckout checkout = svnOperationFactory.createCheckout();
		checkout.setSource(SvnTarget.fromURL(SVNURL.parseURIEncoded(getUri())));
		checkout.setSingleTarget(SvnTarget.fromFile(getWorkingDirectory()));
		Long id = checkout.run();
		if (id == null) {
			return null;
		}
		return id.toString();
	}

	private String update(SvnOperationFactory svnOperationFactory, String label)
			throws SVNException {
		logger.debug("Repo already checked out - updating instead.");

		try {
			final SvnUpdate update = svnOperationFactory.createUpdate();
			update.setSingleTarget(SvnTarget.fromFile(getWorkingDirectory()));
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
			String message = "Could not update remote for " + label + " (current local="
					+ getWorkingDirectory().getPath() + "), remote: " + this.getUri()
					+ ")";
			if (logger.isDebugEnabled()) {
				logger.debug(message, e);
			}
			else if (logger.isWarnEnabled()) {
				logger.warn(message);
			}
		}

		final SVNStatus status = SVNClientManager.newInstance().getStatusClient()
				.doStatus(getWorkingDirectory(), false);
		return status != null ? status.getRevision().toString() : null;
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
		// if it doesn't exists check branches and then tags folders
		File svnPath = new File(workingDirectory, label);
		if (!svnPath.exists()) {
			svnPath = new File(workingDirectory, "branches" + File.separator + label);
			if (!svnPath.exists()) {
				svnPath = new File(workingDirectory, "tags" + File.separator + label);
				if (!svnPath.exists()) {
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
