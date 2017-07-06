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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Data container for property based SSH config
 *
 * @author Ollie Hughes
 */
@ConfigurationProperties("spring.cloud.config.server.git")
public class SshUriProperties {
	private String uri;
	private String hostKeyAlgorithm;
	private String hostKey;
	private String privateKey;
	private String username;
	private String password;
	private boolean ignoreLocalSshSettings;
	private boolean strictHostKeyChecking = true;

	private Map<String, SshUriProperties> repos = new HashMap<>();

	public SshUriProperties(String uri, String hostKeyAlgorithm, String hostKey, String privateKey, String username, String password, boolean ignoreLocalSshSettings, boolean strictHostKeyChecking, Map<String, SshUriProperties> repos) {
		this.uri = uri;
		this.hostKeyAlgorithm = hostKeyAlgorithm;
		this.hostKey = hostKey;
		this.privateKey = privateKey;
		this.username = username;
		this.password = password;
		this.ignoreLocalSshSettings = ignoreLocalSshSettings;
		this.strictHostKeyChecking = strictHostKeyChecking;
		this.repos = repos;
	}

	public SshUriProperties() {
	}

	public static SshUriPropertiesBuilder builder() {
		return new SshUriPropertiesBuilder();
	}

	public boolean isSshUri() {
		return uri != null && !uri.startsWith("http");
	}

	public String getHostname() {
		if (getUri() == null) {
			return null;
		}

		if (getUri().matches("^[a-z]+://.*")) {
			return UriComponentsBuilder.fromUriString(uri).build().getHost();
		}
		else if (getUri().indexOf('@') < getUri().indexOf(':')) {
			return getUri().substring(getUri().indexOf('@') + 1, uri.indexOf(':'));
		}
		else if (getUri().startsWith("ssh:") && getUri().indexOf('@') > 0) {
			String postAt = getUri().substring(getUri().indexOf('@') + 1);
			return postAt.substring(0, postAt.indexOf(":"));
		}
		else return null;
	}

	public String getUri() {
		return this.uri;
	}

	public String getHostKeyAlgorithm() {
		return this.hostKeyAlgorithm;
	}

	public String getHostKey() {
		return this.hostKey;
	}

	public String getPrivateKey() {
		return this.privateKey;
	}

	public String getUsername() {
		return this.username;
	}

	public String getPassword() {
		return this.password;
	}

	public boolean isIgnoreLocalSshSettings() {
		return this.ignoreLocalSshSettings;
	}

	public boolean isStrictHostKeyChecking() {
		return this.strictHostKeyChecking;
	}

	public Map<String, SshUriProperties> getRepos() {
		return this.repos;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public void setHostKeyAlgorithm(String hostKeyAlgorithm) {
		this.hostKeyAlgorithm = hostKeyAlgorithm;
	}

	public void setHostKey(String hostKey) {
		this.hostKey = hostKey;
	}

	public void setPrivateKey(String privateKey) {
		this.privateKey = privateKey;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public void setIgnoreLocalSshSettings(boolean ignoreLocalSshSettings) {
		this.ignoreLocalSshSettings = ignoreLocalSshSettings;
	}

	public void setStrictHostKeyChecking(boolean strictHostKeyChecking) {
		this.strictHostKeyChecking = strictHostKeyChecking;
	}

	public void setRepos(Map<String, SshUriProperties> repos) {
		this.repos = repos;
	}

	@Override
	public int hashCode() {
		return Objects.hash(uri, hostKeyAlgorithm, hostKey, privateKey, username, password, ignoreLocalSshSettings, strictHostKeyChecking);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		final SshUriProperties other = (SshUriProperties) obj;
		return Objects.equals(this.uri, other.uri)
				&& Objects.equals(this.hostKeyAlgorithm, other.hostKeyAlgorithm)
				&& Objects.equals(this.hostKey, other.hostKey)
				&& Objects.equals(this.privateKey, other.privateKey)
				&& Objects.equals(this.username, other.username)
				&& Objects.equals(this.password, other.password)
				&& Objects.equals(this.ignoreLocalSshSettings, other.ignoreLocalSshSettings)
				&& Objects.equals(this.strictHostKeyChecking, other.strictHostKeyChecking);
	}

	public String toString() {
		return "org.springframework.cloud.config.server.ssh.SshUriProperties(uri=" + this.getUri() + " hostKeyAlgorithm=" + this.getHostKeyAlgorithm() + ", hostKey=" + this.getHostKey() + ", privateKey=" + this.getPrivateKey() + ", username=" + this.getUsername() + ", password=" + this.getPassword() + ", ignoreLocalSshSettings=" + this.isIgnoreLocalSshSettings() + ", strictHostKeyChecking=" + this.isStrictHostKeyChecking() + ", repos=" + this.getRepos() + ")";
	}

	public static class SshUriPropertiesBuilder {
		private String uri;
		private String hostKeyAlgorithm;
		private String hostKey;
		private String privateKey;
		private String username;
		private String password;
		private boolean ignoreLocalSshSettings;
		private boolean strictHostKeyChecking = true;
		private Map<String, SshUriProperties> repos;

		SshUriPropertiesBuilder() {
		}

		public SshUriProperties.SshUriPropertiesBuilder uri(String uri) {
			this.uri = uri;
			return this;
		}

		public SshUriProperties.SshUriPropertiesBuilder hostKeyAlgorithm(String hostKeyAlgorithm) {
			this.hostKeyAlgorithm = hostKeyAlgorithm;
			return this;
		}

		public SshUriProperties.SshUriPropertiesBuilder hostKey(String hostKey) {
			this.hostKey = hostKey;
			return this;
		}

		public SshUriProperties.SshUriPropertiesBuilder privateKey(String privateKey) {
			this.privateKey = privateKey;
			return this;
		}

		public SshUriProperties.SshUriPropertiesBuilder username(String username) {
			this.username = username;
			return this;
		}

		public SshUriProperties.SshUriPropertiesBuilder password(String password) {
			this.password = password;
			return this;
		}

		public SshUriProperties.SshUriPropertiesBuilder ignoreLocalSshSettings(boolean ignoreLocalSshSettings) {
			this.ignoreLocalSshSettings = ignoreLocalSshSettings;
			return this;
		}

		public SshUriProperties.SshUriPropertiesBuilder strictHostKeyChecking(boolean strictHostKeyChecking) {
			this.strictHostKeyChecking = strictHostKeyChecking;
			return this;
		}

		public SshUriProperties.SshUriPropertiesBuilder repos(Map<String, SshUriProperties> repos) {
			this.repos = repos;
			return this;
		}

		public SshUriProperties build() {
			return new SshUriProperties(uri, hostKeyAlgorithm, hostKey, privateKey, username, password, ignoreLocalSshSettings, strictHostKeyChecking, repos);
		}

		public String toString() {
			return "org.springframework.cloud.config.server.ssh.SshUriProperties.SshUriPropertiesBuilder(uri=" + this.uri + "hostKeyAlgorithm=" + this.hostKeyAlgorithm + ", hostKey=" + this.hostKey + ", privateKey=" + this.privateKey + ", username=" + this.username + ", password=" + this.password + ", ignoreLocalSshSettings=" + this.ignoreLocalSshSettings + ", strictHostKeyChecking=" + this.strictHostKeyChecking + ", repos=" + this.repos + ")";
		}
	}
}
