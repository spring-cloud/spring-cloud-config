/*
 * Copyright 2015 the original author or authors.
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
package org.springframework.cloud.config.server.ssh;


import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.transport.URIish;
import org.springframework.cloud.config.server.ssh.SshUriProperties.SshUriNestedRepoProperties;

import static org.springframework.cloud.config.server.ssh.SshPropertyValidator.isSshUri;

/**
 * Check if Git repo properties refer to an SSH based transport then filter and extract the properties
 * @author William Tran
 * @author Ollie Hughes
 */
public class SshUriPropertyProcessor {

	private final SshUriProperties sshUriProperties;

	public SshUriPropertyProcessor(SshUriProperties sshUriProperties) {
		this.sshUriProperties = sshUriProperties;
	}

	public Map<String, SshUri> getSshKeysByHostname() {
		return extractNestedProperties(sshUriProperties);
	}

	private Map<String, SshUri> extractNestedProperties(SshUriProperties uriProperties) {
		Map<String, SshUri> sshUriPropertyMap = new HashMap<>();
		String parentUri = uriProperties.getUri();
		if (isSshUri(parentUri) && getHostname(parentUri) != null) {
			sshUriPropertyMap.put(getHostname(parentUri), uriProperties);
		}
		Map<String, SshUriNestedRepoProperties> repos = uriProperties.getRepos();
		if(repos != null) {
			for (SshUriNestedRepoProperties repoProperties : repos.values()) {
				String repoUri = repoProperties.getUri();
				if (isSshUri(repoUri) && getHostname(repoUri) != null) {
					sshUriPropertyMap.put(getHostname(repoUri), repoProperties);
				}
			}
		}
		return sshUriPropertyMap;
	}

	protected static String getHostname(String uri) {
		try {
			URIish urIish = new URIish(uri);
			return urIish.getHost();
		} catch (URISyntaxException e) {
			return null;
		}
	}
}
