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

import java.util.HashMap;
import java.util.Map;

import javax.validation.constraints.Pattern;

import org.springframework.cloud.config.server.proxy.ProxyHostProperties;
import org.springframework.cloud.config.server.support.AbstractScmAccessorProperties;
import org.springframework.cloud.config.server.support.HttpEnvironmentRepositoryProperties;

/**
 * @author Dylan Roberts
 * @author Gareth Clay
 */
public class JGitEnvironmentProperties extends AbstractScmAccessorProperties
		implements HttpEnvironmentRepositoryProperties {

	private static final String DEFAULT_LABEL = "master";

	/**
	 * Flag to indicate that the repository should be cloned on startup (not on demand).
	 * Generally leads to slower startup but faster first query.
	 */
	private boolean cloneOnStart = false;

	/**
	 * Flag to indicate that the repository should force pull. If true discard any local
	 * changes and take from remote repository.
	 */
	private boolean forcePull;

	/**
	 * Timeout (in seconds) for obtaining HTTP or SSH connection (if applicable), defaults
	 * to 5 seconds.
	 */
	private int timeout = 5;

	/**
	 * Flag to indicate that the branch should be deleted locally if it's origin tracked
	 * branch was removed.
	 */
	private boolean deleteUntrackedBranches = false;

	/**
	 * Flag to indicate that SSL certificate validation should be bypassed when
	 * communicating with a repository served over an HTTPS connection.
	 */
	private boolean skipSslValidation = false;

	/**
	 * Time (in seconds) between refresh of the git repository.
	 */
	private int refreshRate = 0;

	/**
	 * Valid SSH private key. Must be set if ignoreLocalSshSettings is true and Git URI is
	 * SSH format.
	 */
	private String privateKey;

	/**
	 * One of ssh-dss, ssh-rsa, ecdsa-sha2-nistp256, ecdsa-sha2-nistp384, or
	 * ecdsa-sha2-nistp521. Must be set if hostKey is also set.
	 */
	private String hostKeyAlgorithm;

	/**
	 * Valid SSH host key. Must be set if hostKeyAlgorithm is also set.
	 */
	private String hostKey;

	/**
	 * Location of custom .known_hosts file.
	 */
	private String knownHostsFile;

	/**
	 * Override server authentication method order. This should allow for evading login
	 * prompts if server has keyboard-interactive authentication before the publickey
	 * method.
	 */
	@Pattern(regexp = "([\\w -]+,)*([\\w -]+)")
	private String preferredAuthentications;

	/**
	 * If true, use property-based instead of file-based SSH config.
	 */
	private boolean ignoreLocalSshSettings;

	/**
	 * If false, ignore errors with host key.
	 */
	private boolean strictHostKeyChecking = true;

	/**
	 * HTTP proxy configuration.
	 */
	private Map<ProxyHostProperties.ProxyForScheme, ProxyHostProperties> proxy = new HashMap<>();

	public JGitEnvironmentProperties() {
		super();
		setDefaultLabel(DEFAULT_LABEL);
	}

	public boolean isCloneOnStart() {
		return this.cloneOnStart;
	}

	public void setCloneOnStart(boolean cloneOnStart) {
		this.cloneOnStart = cloneOnStart;
	}

	public boolean isForcePull() {
		return this.forcePull;
	}

	public void setForcePull(boolean forcePull) {
		this.forcePull = forcePull;
	}

	@Override
	public int getTimeout() {
		return this.timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public boolean isDeleteUntrackedBranches() {
		return this.deleteUntrackedBranches;
	}

	public void setDeleteUntrackedBranches(boolean deleteUntrackedBranches) {
		this.deleteUntrackedBranches = deleteUntrackedBranches;
	}

	public int getRefreshRate() {
		return this.refreshRate;
	}

	public void setRefreshRate(int refreshRate) {
		this.refreshRate = refreshRate;
	}

	public String getPrivateKey() {
		return this.privateKey;
	}

	public void setPrivateKey(String privateKey) {
		this.privateKey = privateKey;
	}

	public String getHostKeyAlgorithm() {
		return this.hostKeyAlgorithm;
	}

	public void setHostKeyAlgorithm(String hostKeyAlgorithm) {
		this.hostKeyAlgorithm = hostKeyAlgorithm;
	}

	public String getHostKey() {
		return this.hostKey;
	}

	public void setHostKey(String hostKey) {
		this.hostKey = hostKey;
	}

	public String getKnownHostsFile() {
		return this.knownHostsFile;
	}

	public void setKnownHostsFile(String knownHostsFile) {
		this.knownHostsFile = knownHostsFile;
	}

	public String getPreferredAuthentications() {
		return this.preferredAuthentications;
	}

	public void setPreferredAuthentications(String preferredAuthentications) {
		this.preferredAuthentications = preferredAuthentications;
	}

	public boolean isIgnoreLocalSshSettings() {
		return this.ignoreLocalSshSettings;
	}

	public void setIgnoreLocalSshSettings(boolean ignoreLocalSshSettings) {
		this.ignoreLocalSshSettings = ignoreLocalSshSettings;
	}

	@Override
	public boolean isStrictHostKeyChecking() {
		return this.strictHostKeyChecking;
	}

	@Override
	public void setStrictHostKeyChecking(boolean strictHostKeyChecking) {
		this.strictHostKeyChecking = strictHostKeyChecking;
	}

	@Override
	public boolean isSkipSslValidation() {
		return this.skipSslValidation;
	}

	public void setSkipSslValidation(boolean skipSslValidation) {
		this.skipSslValidation = skipSslValidation;
	}

	@Override
	public Map<ProxyHostProperties.ProxyForScheme, ProxyHostProperties> getProxy() {
		return this.proxy;
	}

	public void setProxy(
			Map<ProxyHostProperties.ProxyForScheme, ProxyHostProperties> proxy) {
		this.proxy = proxy;
	}

}
