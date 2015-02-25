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

package org.springframework.cloud.configure.server;

import java.io.File;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.configure.Environment;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUpdate;

import static org.springframework.util.StringUtils.hasText;

/**
 * Subversion-backed {@link EnvironmentRepository}.
 * 
 * @author Michael Prankl
 */
@ConfigurationProperties("spring.cloud.config.server.svn")
public class SVNKitEnvironmentRepository extends AbstractSCMEnvironmentRepository {

	private static Log logger = LogFactory.getLog(SVNKitEnvironmentRepository.class);

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

	private Environment loadEnvironment(String application, String profile, String label) {
		final SpringApplicationEnvironmentRepository environmentRepository = new SpringApplicationEnvironmentRepository();
		environmentRepository.setSearchLocations(getSearchLocations(getSvnPath(
				getWorkingDirectory(), label)));
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

	public SVNKitEnvironmentRepository(ConfigurableEnvironment environment) {
		super(environment);
	}

	@Override
	protected File getWorkingDirectory() {
		return this.basedir;
	}

	private File getSvnPath(File workingDirectory, String label) {
		// use label as path relative to repository root
		return new File(workingDirectory, label);
	}

}
