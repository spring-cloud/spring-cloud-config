/*
 * Copyright 2018-2019 the original author or authors.
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

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.config.server.ssh.HostKeyAlgoSupported;
import org.springframework.cloud.config.server.ssh.HostKeyAndAlgoBothExist;
import org.springframework.cloud.config.server.ssh.KnownHostsFileIsValid;
import org.springframework.cloud.config.server.ssh.PrivateKeyIsValid;
import org.springframework.validation.annotation.Validated;

/**
 * @author Dylan Roberts
 */
@ConfigurationProperties("spring.cloud.config.server.git")
@Validated
@PrivateKeyIsValid
@HostKeyAndAlgoBothExist
@HostKeyAlgoSupported
@KnownHostsFileIsValid
public class MultipleJGitEnvironmentProperties extends JGitEnvironmentProperties {

	/**
	 * Map of repository identifier to location and other properties.
	 */
	private Map<String, PatternMatchingJGitEnvironmentProperties> repos = new LinkedHashMap<>();

	public Map<String, PatternMatchingJGitEnvironmentProperties> getRepos() {
		return this.repos;
	}

	public void setRepos(Map<String, PatternMatchingJGitEnvironmentProperties> repos) {
		this.repos = repos;
	}

	/**
	 * A {@link JGitEnvironmentProperties} that matches patterns.
	 */
	public static class PatternMatchingJGitEnvironmentProperties
			extends JGitEnvironmentProperties {

		/**
		 * Pattern to match on application name and profiles.
		 */
		private String[] pattern = new String[0];

		/**
		 * Name of repository (same as map key by default).
		 */
		private String name;

		public PatternMatchingJGitEnvironmentProperties() {
		}

		public PatternMatchingJGitEnvironmentProperties(String uri) {
			setUri(uri);
		}

		public String[] getPattern() {
			return this.pattern;
		}

		public void setPattern(String[] pattern) {
			this.pattern = pattern;
		}

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

	}

}
