/*
 * Copyright 2015-2019 the original author or authors.
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

package org.springframework.cloud.config.server.ssh;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.transport.URIish;

import org.springframework.cloud.config.server.environment.JGitEnvironmentProperties;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentProperties;

import static org.springframework.cloud.config.server.ssh.SshPropertyValidator.isSshUri;

/**
 * Check if Git repo properties refer to an SSH based transport then filter and extract
 * the properties.
 *
 * @author William Tran
 * @author Ollie Hughes
 */
public class SshUriPropertyProcessor {

	private final MultipleJGitEnvironmentProperties sshUriProperties;

	public SshUriPropertyProcessor(MultipleJGitEnvironmentProperties sshUriProperties) {
		this.sshUriProperties = sshUriProperties;
	}

	protected static String getHostname(String uri) {
		try {
			URIish urIish = new URIish(uri);
			return urIish.getHost();
		}
		catch (URISyntaxException e) {
			return null;
		}
	}

	public Map<String, JGitEnvironmentProperties> getSshKeysByHostname() {
		return extractNestedProperties(this.sshUriProperties);
	}

	private Map<String, JGitEnvironmentProperties> extractNestedProperties(
			MultipleJGitEnvironmentProperties uriProperties) {
		Map<String, JGitEnvironmentProperties> sshUriPropertyMap = new HashMap<>();
		String parentUri = uriProperties.getUri();
		if (isSshUri(parentUri) && getHostname(parentUri) != null) {
			sshUriPropertyMap.put(getHostname(parentUri), uriProperties);
		}
		Map<String, MultipleJGitEnvironmentProperties.PatternMatchingJGitEnvironmentProperties> repos = uriProperties
				.getRepos();
		if (repos != null) {
			for (MultipleJGitEnvironmentProperties.PatternMatchingJGitEnvironmentProperties repoProperties : repos
					.values()) {
				String repoUri = repoProperties.getUri();
				if (isSshUri(repoUri) && getHostname(repoUri) != null) {
					sshUriPropertyMap.put(getHostname(repoUri), repoProperties);
				}
			}
		}
		return sshUriPropertyMap;
	}

}
