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

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

/**
 * 
 * @author Don Laidlaw
 *
 */
public class GitCredentialsProviderFactory {
	
	/**
	 * Search for a credential provider that will handle the specified URI. If
	 * not found, and the username has text, then create a default using the
	 * provided username and password. Otherwise null.
	 * @param uri the URI of the repository (cannot be null)
	 * @param username the username provided for the repository (may be null)
	 * @param password the password provided for the repository (may be null)
	 * @return the first matched credentials provider or the default or null.
	 */
	public static CredentialsProvider createFor(String uri, String username, String password) {
		CredentialsProvider provider = null;
		if (AwsCodeCommitCredentialProvider.canHandle(uri)) {
			AwsCodeCommitCredentialProvider aws = new AwsCodeCommitCredentialProvider();
			aws.setUsername(username);
			aws.setPassword(password);
			provider = aws;
		} else if (hasText(username)) {
			provider = new UsernamePasswordCredentialsProvider(username, password.toCharArray());
		}
		
		return provider;
	}
	
}
