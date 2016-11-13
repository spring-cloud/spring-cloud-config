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

package org.springframework.cloud.config.server.credentials;

import static org.springframework.util.StringUtils.hasText;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.util.ClassUtils;

/**
 * A CredentialsProvider factory for Git repositories. Can handle AWS CodeCommit 
 * repositories and other repositories with username/password.
 * 
 * @author Don Laidlaw
 *
 */
public class GitCredentialsProviderFactory {
	protected Log logger = LogFactory.getLog(getClass());

	/**
	 * Enable the AWS Code Commit credentials provider for Git URI's
	 * that match the AWS Code Commit pattern of
	 * https://git-codecommit.${AWS_REGION}.amazonaws.com/${repoPath}.
	 * Enabled by default.
	 */
	protected boolean awsCodeCommitEnabled = true;
	
	/**
	 * Search for a credential provider that will handle the specified URI. If
	 * not found, and the username has text, then create a default using the
	 * provided username and password. Otherwise null.
	 * @param uri the URI of the repository (cannot be null)
	 * @param username the username provided for the repository (may be null)
	 * @param password the password provided for the repository (may be null)
	 * @return the first matched credentials provider or the default or null.
	 */
	public CredentialsProvider createFor(String uri, String username, String password) {
		CredentialsProvider provider = null;
		if (awsAvailable() && AwsCodeCommitCredentialProvider.canHandle(uri)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Creating AwsCodeCommitCredentialsProvider for git uri "
						+ uri);
			}
			AwsCodeCommitCredentialProvider aws = new AwsCodeCommitCredentialProvider();
			aws.setUsername(username);
			aws.setPassword(password);
			provider = aws;
		} else if (hasText(username)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Creating UsernamePasswordCredentialsProvider for git uri "
						+ uri);
			}
			provider = new UsernamePasswordCredentialsProvider(username, password.toCharArray());
		} else {
			if (logger.isDebugEnabled()) {
				logger.debug("Not Creating CredentialsProvider for git uri "
						+ uri);
			}
		}
		
		return provider;
	}
	
	/**
	 * Check to see if the AWS Authentication API is available.
	 * @return true if the com.amazonaws.auth.DefaultAWSCredentialsProviderChain is present,
	 * 		false otherwise.
	 */
	private boolean awsAvailable() {
		boolean available = false;
		if (awsCodeCommitEnabled) {
			available = ClassUtils.isPresent("com.amazonaws.auth.DefaultAWSCredentialsProviderChain", null);
			if (available && logger.isDebugEnabled()) {
				logger.debug(
						"com.amazonaws.auth.DefaultAWSCredentialsProviderChain is available, "
						+ "enabling AwsCodeCommitCredentialProvider");
			} else if (logger.isDebugEnabled()) {
				logger.debug("com.amazonaws.auth.DefaultAWSCredentialsProviderChain is not available, "
						+ "disabling AwsCodeCommitCredentialProvider");
			}
		} else if (logger.isDebugEnabled()) {
			logger.debug("AWS Code Commit credentials provider is disabled");
		}
		return available;
	}

	/**
	 * @return the awsCodeCommitEnabled
	 */
	public boolean isAwsCodeCommitEnabled() {
		return awsCodeCommitEnabled;
	}

	/**
	 * @param awsCodeCommitEnabled the awsCodeCommitEnabled to set
	 */
	public void setAwsCodeCommitEnabled(boolean awsCodeCommitEnabled) {
		this.awsCodeCommitEnabled = awsCodeCommitEnabled;
	}
	
}
