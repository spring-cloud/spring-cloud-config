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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUpdate;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.config.environment.Environment;
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
@ConfigurationProperties("spring.cloud.config.server.svn")
public class SvnKitEnvironmentRepository extends AbstractScmEnvironmentRepository {

	private static Log logger = LogFactory.getLog(SvnKitEnvironmentRepository.class);

	private static final String DEFAULT_LABEL = "trunk";

	@Override
	public String getDefaultLabel() {
		return DEFAULT_LABEL;
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
		if (hasText(getUsername())) {
			svnOperationFactory
					.setAuthenticationManager(new DefaultSVNAuthenticationManager(null,
							false, getUsername(), getPassword()));
		}
		try {
			if (new File(getWorkingDirectory(), ".svn").exists()) {
				update(svnOperationFactory);
			}
			else {
				checkout(svnOperationFactory);
			}
			return clean(loadEnvironment(application, profile, label));
		}
		catch (SVNException e) {
			throw new IllegalStateException("Cannot checkout repository", e);
		}
		finally {
			svnOperationFactory.dispose();
		}
	}

	private synchronized Environment loadEnvironment(String application, String profile, String label) {
		final NativeEnvironmentRepository environmentRepository = new NativeEnvironmentRepository(
				getEnvironment());
		String[] locations = getSearchLocations(getSvnPath(
				getWorkingDirectory(), label));
		boolean exists = false;
		for (String location : locations) {
			location = location.startsWith("file:") ? location.substring("file:".length()) : location;
			location = StringUtils.cleanPath(location);
			if (new File(location).exists()) {
				exists = true;
				break;
			}
		}
		if (!exists) {
			throw new NoSuchLabelException("No label found for: " + label);
		}
		environmentRepository.setSearchLocations(locations);
		return environmentRepository.findOne(application, profile, label);
	}

	private void checkout(SvnOperationFactory svnOperationFactory) throws SVNException {
		logger.debug("Checking out " + getUri() + " to: "
				+ getWorkingDirectory().getAbsolutePath());
		final SvnCheckout checkout = svnOperationFactory.createCheckout();
		checkout.setSource(SvnTarget.fromURL(SVNURL.parseURIEncoded(getUri())));
		checkout.setSingleTarget(SvnTarget.fromFile(getWorkingDirectory()));
		checkout.run();
	}

	private void update(SvnOperationFactory svnOperationFactory) throws SVNException {
		logger.debug("Repo already checked out - updating instead.");
		final SvnUpdate update = svnOperationFactory.createUpdate();
		update.setSingleTarget(SvnTarget.fromFile(getWorkingDirectory()));
		update.run();
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		Assert.state(
				getUri() != null,
				"You need to configure a uri for the subversion repository (e.g. 'http://example.com/svn/')");
		resolveRelativeFileUri();
	}

	private void resolveRelativeFileUri() {
		if (getUri().startsWith("file:///./")) {
			String path = getUri().substring(8);
			String absolutePath = new File(path).getAbsolutePath();
			setUri("file:///" + StringUtils.cleanPath(absolutePath));
		}

	}

	public SvnKitEnvironmentRepository(ConfigurableEnvironment environment) {
		super(environment);
	}

	@Override
	protected File getWorkingDirectory() {
		return this.getBasedir();
	}

	private File getSvnPath(File workingDirectory, String label) {
		// use label as path relative to repository root
		return new File(workingDirectory, label);
	}

}
