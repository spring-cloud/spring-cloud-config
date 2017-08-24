/*
 * Copyright 2015 - 2017 the original author or authors.
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

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Data container for property based SSH config
 *
 * @author Ollie Hughes
 */
@ConfigurationProperties("spring.cloud.config.server.git")
@Validated
@PrivateKeyIsValid
@HostKeyAndAlgoBothExist
@HostKeyAlgoSupported
@KnownHostsFileIsValid
public class SshUriProperties extends SshUri {

	private Map<String, SshUriProperties.SshUriNestedRepoProperties> repos = new LinkedHashMap<>();

	public Map<String, SshUriProperties.SshUriNestedRepoProperties> getRepos() {
		return this.repos;
	}

	public void setRepos(Map<String, SshUriNestedRepoProperties> repos) {
		this.repos = repos;
	}

	public void addRepo(String repoName, SshUriProperties.SshUriNestedRepoProperties properties) {
		this.repos.put(repoName, properties);
	}

	@Override
	public String toString() {
		return super.toString() + "{repos=" + repos + "}";
	}

	/**
	 * Differentiate between sets of properties that are defined in nested Git repos.
	 * This is to prevent boot from guarding against a potential infinite deserialization of nested properties.
	 * This sub class differentiates from {@link SshUriProperties} as it does not contain the self mao
	 */
	public static class SshUriNestedRepoProperties extends SshUri {

	}
}
