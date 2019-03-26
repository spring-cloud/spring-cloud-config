/*
 * Copyright 2017 the original author or authors.
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

import org.springframework.cloud.config.server.ssh.SshUriProperties.SshUriNestedRepoProperties;

import javax.validation.constraints.Pattern;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class that contains configuration properties for Git SSH properties
 *
 * @author Ollie Hughes
 */
public abstract class SshUri {
	private String privateKey;
	private String uri;
	private String hostKeyAlgorithm;
	private String hostKey;
	private String knownHostsFile;
	@Pattern(regexp = "([\\w -]+,)*([\\w -]+)")
	private String preferredAuthentications;
	private boolean ignoreLocalSshSettings;
	private boolean strictHostKeyChecking = true;
	private String proxyHost;
	private Integer proxyPort;

	public static SshUriPropertiesBuilder builder() {
		return new SshUriPropertiesBuilder();
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

	public String getKnownHostsFile() {
		return this.knownHostsFile;
	}

	public String getPreferredAuthentications() {
		return this.preferredAuthentications;
	}

	public String getPrivateKey() {
		return this.privateKey;
	}

	public boolean isIgnoreLocalSshSettings() {
		return this.ignoreLocalSshSettings;
	}

	public boolean isStrictHostKeyChecking() {
		return this.strictHostKeyChecking;
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

	public void setKnownHostsFile(String knownHostsFile) {
		this.knownHostsFile = knownHostsFile;
	}

	public void setPreferredAuthentications(String preferredAuthentications) {
		this.preferredAuthentications = preferredAuthentications;
	}

	public void setPrivateKey(String privateKey) {
		this.privateKey = privateKey;
	}

	public void setIgnoreLocalSshSettings(boolean ignoreLocalSshSettings) {
		this.ignoreLocalSshSettings = ignoreLocalSshSettings;
	}

	public void setStrictHostKeyChecking(boolean strictHostKeyChecking) {
		this.strictHostKeyChecking = strictHostKeyChecking;
	}

	public String getProxyHost() {
		return proxyHost;
	}

	public void setProxyHost(String proxyHost) {
		this.proxyHost = proxyHost;
	}

	public Integer getProxyPort() {
		return proxyPort;
	}

	public void setProxyPort(Integer proxyPort) {
		this.proxyPort = proxyPort;
	}

	public String toString() {
		return "org.springframework.cloud.config.server.ssh.SshUriProperties(uri=" + this.getUri()
				+ " hostKeyAlgorithm=" + this.getHostKeyAlgorithm()
				+ ", hostKey=" + this.getHostKey()
				+ ", privateKey=" + this.getPrivateKey()
				+ ", ignoreLocalSshSettings=" + this.isIgnoreLocalSshSettings()
				+ ", knownHostsFile=" + this.getKnownHostsFile()
				+ ", preferredAuthentications=" + this.getPreferredAuthentications()
				+ ", strictHostKeyChecking=" + this.isStrictHostKeyChecking()
				+ ", proxyHost=" + this.getProxyHost()
				+ ", proxyPort=" + this.getProxyPort() + ",)";
	}

	public static class SshUriPropertiesBuilder {
		private String uri;
		private String hostKeyAlgorithm;
		private String hostKey;
		private String privateKey;
		private String knownHostsFile;
		private String preferredAuthentications;
		private boolean ignoreLocalSshSettings;
		private boolean strictHostKeyChecking = true;
		private String proxyHost;
		private Integer proxyPort;
		private Map<String, SshUriNestedRepoProperties> repos = new LinkedHashMap<>();

		SshUriPropertiesBuilder() {
		}

		public SshUri.SshUriPropertiesBuilder uri(String uri) {
			this.uri = uri;
			return this;
		}

		public SshUri.SshUriPropertiesBuilder hostKeyAlgorithm(String hostKeyAlgorithm) {
			this.hostKeyAlgorithm = hostKeyAlgorithm;
			return this;
		}

		public SshUri.SshUriPropertiesBuilder hostKey(String hostKey) {
			this.hostKey = hostKey;
			return this;
		}

		public SshUri.SshUriPropertiesBuilder privateKey(String privateKey) {
			this.privateKey = privateKey;
			return this;
		}

		public SshUri.SshUriPropertiesBuilder knownHostsFile(String knownHostsFile) {
			this.knownHostsFile = knownHostsFile;
			return this;
		}

		public SshUri.SshUriPropertiesBuilder preferredAuthentications(String preferredAuthentications) {
			this.preferredAuthentications = preferredAuthentications;
			return this;
		}

		public SshUri.SshUriPropertiesBuilder ignoreLocalSshSettings(boolean ignoreLocalSshSettings) {
			this.ignoreLocalSshSettings = ignoreLocalSshSettings;
			return this;
		}

		public SshUri.SshUriPropertiesBuilder strictHostKeyChecking(boolean strictHostKeyChecking) {
			this.strictHostKeyChecking = strictHostKeyChecking;
			return this;
		}

		public SshUri.SshUriPropertiesBuilder proxyHost(String proxyHost) {
			this.proxyHost = proxyHost;
			return this;
		}

		public SshUri.SshUriPropertiesBuilder proxyPort(Integer proxyPort) {
			this.proxyPort = proxyPort;
			return this;
		}

		public SshUri.SshUriPropertiesBuilder repos(Map<String, SshUriNestedRepoProperties> repos) {
			this.repos = repos;
			return this;
		}

		public SshUriProperties build() {
			SshUriProperties sshUriProperties = new SshUriProperties();
			sshUriProperties.setRepos(repos);
			build(sshUriProperties);
			return sshUriProperties;
		}

		public SshUriNestedRepoProperties buildAsNestedRepo() {
			SshUriNestedRepoProperties sshUriNestedRepoProperties = new SshUriNestedRepoProperties();
			build(sshUriNestedRepoProperties);
			return sshUriNestedRepoProperties;
		}

		private void build(SshUri sshUriNestedRepoProperties) {
			sshUriNestedRepoProperties.setUri(uri);
			sshUriNestedRepoProperties.setHostKeyAlgorithm(hostKeyAlgorithm);
			sshUriNestedRepoProperties.setHostKey(hostKey);
			sshUriNestedRepoProperties.setPrivateKey(privateKey);
			sshUriNestedRepoProperties.setKnownHostsFile(knownHostsFile);
			sshUriNestedRepoProperties.setPreferredAuthentications(preferredAuthentications);
			sshUriNestedRepoProperties.setIgnoreLocalSshSettings(ignoreLocalSshSettings);
			sshUriNestedRepoProperties.setStrictHostKeyChecking(strictHostKeyChecking);
			sshUriNestedRepoProperties.setProxyHost(proxyHost);
			sshUriNestedRepoProperties.setProxyPort(proxyPort);
		}

		public String toString() {
			return "org.springframework.cloud.config.server.ssh.SshUriProperties.SshUriPropertiesBuilder(uri=" + this.uri
					+ "hostKeyAlgorithm=" + this.hostKeyAlgorithm
					+ ", hostKey=" + this.hostKey
					+ ", privateKey=" + this.privateKey
					+ ", knownHostsFile=" + this.knownHostsFile
					+ ", preferredAuthentications=" + this.preferredAuthentications
					+ ", ignoreLocalSshSettings=" + this.ignoreLocalSshSettings
					+ ", strictHostKeyChecking=" + this.strictHostKeyChecking
					+ ", proxyHost=" + this.proxyHost
					+ ", proxyPort=" + this.proxyPort
					+ ", repos=" + this.repos + ")";
		}
	}
}
