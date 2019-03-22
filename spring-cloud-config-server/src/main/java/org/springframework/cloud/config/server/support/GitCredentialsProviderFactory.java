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

package org.springframework.cloud.config.server.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import org.springframework.util.ClassUtils;

import static org.springframework.util.StringUtils.hasText;

/**
 * A CredentialsProvider factory for Git repositories. Can handle AWS CodeCommit
 * repositories and other repositories with username/password.
 *
 * @author Don Laidlaw
 * @author Gareth Clay
 *
 */
public class GitCredentialsProviderFactory {

	protected Log logger = LogFactory.getLog(getClass());

	/**
	 * Enable the AWS Code Commit credentials provider for Git URI's that match the AWS
	 * Code Commit pattern of
	 * https://git-codecommit.${AWS_REGION}.amazonaws.com/${repoPath}. Enabled by default.
	 */
	protected boolean awsCodeCommitEnabled = true;

	/**
	 * Search for a credential provider that will handle the specified URI. If not found,
	 * and the username or passphrase has text, then create a default using the provided
	 * username and password or passphrase. Otherwise null.
	 * @param uri the URI of the repository (cannot be null)
	 * @param username the username provided for the repository (may be null)
	 * @param password the password provided for the repository (may be null)
	 * @param passphrase the passphrase to unlock the ssh private key (may be null)
	 * @return the first matched credentials provider or the default or null.
	 * @deprecated in favour of
	 * {@link #createFor(String, String, String, String, boolean)}
	 */
	@Deprecated
	public CredentialsProvider createFor(String uri, String username, String password,
			String passphrase) {
		return createFor(uri, username, password, passphrase, false);
	}

	/**
	 * Search for a credential provider that will handle the specified URI. If not found,
	 * and the username or passphrase has text, then create a default using the provided
	 * username and password or passphrase.
	 *
	 * If skipSslValidation is true and the URI has an https scheme, the default
	 * credential provider's behaviour is modified to suppress any SSL validation errors
	 * that occur when communicating via the URI.
	 *
	 * Otherwise null.
	 * @param uri the URI of the repository (cannot be null)
	 * @param username the username provided for the repository (may be null)
	 * @param password the password provided for the repository (may be null)
	 * @param passphrase the passphrase to unlock the ssh private key (may be null)
	 * @param skipSslValidation whether to skip SSL validation when connecting via HTTPS
	 * @return the first matched credentials provider or the default or null.
	 */
	public CredentialsProvider createFor(String uri, String username, String password,
			String passphrase, boolean skipSslValidation) {
		CredentialsProvider provider = null;
		if (awsAvailable() && AwsCodeCommitCredentialProvider.canHandle(uri)) {
			this.logger
					.debug("Constructing AwsCodeCommitCredentialProvider for URI " + uri);
			AwsCodeCommitCredentialProvider aws = new AwsCodeCommitCredentialProvider();
			aws.setUsername(username);
			aws.setPassword(password);
			provider = aws;
		}
		else if (hasText(username)) {
			this.logger.debug(
					"Constructing UsernamePasswordCredentialsProvider for URI " + uri);
			provider = new UsernamePasswordCredentialsProvider(username,
					password.toCharArray());
		}
		else if (hasText(passphrase)) {
			this.logger
					.debug("Constructing PassphraseCredentialsProvider for URI " + uri);
			provider = new PassphraseCredentialsProvider(passphrase);
		}

		if (skipSslValidation && GitSkipSslValidationCredentialsProvider.canHandle(uri)) {
			this.logger
					.debug("Constructing GitSkipSslValidationCredentialsProvider for URI "
							+ uri);
			provider = new GitSkipSslValidationCredentialsProvider(provider);
		}

		if (provider == null) {
			this.logger.debug("No credentials provider required for URI " + uri);
		}

		return provider;
	}

	/**
	 * Check to see if the AWS Authentication API is available.
	 * @return true if the com.amazonaws.auth.DefaultAWSCredentialsProviderChain is
	 * present, false otherwise.
	 */
	private boolean awsAvailable() {
		return this.awsCodeCommitEnabled && ClassUtils
				.isPresent("com.amazonaws.auth.DefaultAWSCredentialsProviderChain", null);
	}

	/**
	 * @return the awsCodeCommitEnabled
	 */
	public boolean isAwsCodeCommitEnabled() {
		return this.awsCodeCommitEnabled;
	}

	/**
	 * @param awsCodeCommitEnabled the awsCodeCommitEnabled to set
	 */
	public void setAwsCodeCommitEnabled(boolean awsCodeCommitEnabled) {
		this.awsCodeCommitEnabled = awsCodeCommitEnabled;
	}

}
