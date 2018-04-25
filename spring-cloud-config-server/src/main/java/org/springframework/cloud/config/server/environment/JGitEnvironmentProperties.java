/*
 * Copyright 2018 the original author or authors.
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
package org.springframework.cloud.config.server.environment;

import javax.validation.constraints.Pattern;

import org.springframework.cloud.config.server.support.AbstractScmAccessorProperties;

/**
 * @author Dylan Roberts
 * @author Gareth Clay
 */
public class JGitEnvironmentProperties extends AbstractScmAccessorProperties {
    private static final String DEFAULT_LABEL = "master";

    /** Flag to indicate that the repository should be cloned on startup (not on demand). Generally leads to slower startup but faster first query. */
    private boolean cloneOnStart = false;

    /** Flag to indicate that the repository should force pull. If true discard any local changes and take from remote repository. */
    private boolean forcePull;

    /** Timeout (in seconds) for obtaining HTTP or SSH connection (if applicable), defaults to 5 seconds. */
    private int timeout = 5;

    /** Flag to indicate that the branch should be deleted locally if it's origin tracked branch was removed. */
    private boolean deleteUntrackedBranches = false;

	/**
	 * Flag to indicate that SSL certificate validation should be bypassed when
	 * communicating with a repository served over an HTTPS connection.
	 */
	private boolean skipSslValidation = false;

    /**
     * Time (in seconds) between refresh of the git repository
     */
    private int refreshRate = 0;

    /**
     * Valid SSH private key. Must be set if ignoreLocalSshSettings is true and Git URI is SSH format.
     */
    private String privateKey;

    /**
     * One of ssh-dss, ssh-rsa, ecdsa-sha2-nistp256, ecdsa-sha2-nistp384, or ecdsa-sha2-nistp521. Must be set if hostKey is also set.
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
     * Override server authentication method order. This should allow for evading login prompts if server has keyboard-interactive authentication before the publickey method.
     */
    @Pattern(regexp = "([\\w -]+,)*([\\w -]+)")
    private String preferredAuthentications;

    /**
     * If true, use property-based instead of file-based SSH config.
     */
    private boolean ignoreLocalSshSettings;

    /**
     * If false, ignore errors with host key
     */
    private boolean strictHostKeyChecking = true;

    public JGitEnvironmentProperties() {
        super();
        setDefaultLabel(DEFAULT_LABEL);
    }

    public boolean isCloneOnStart() {
        return cloneOnStart;
    }

    public void setCloneOnStart(boolean cloneOnStart) {
        this.cloneOnStart = cloneOnStart;
    }

    public boolean isForcePull() {
        return forcePull;
    }

    public void setForcePull(boolean forcePull) {
        this.forcePull = forcePull;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public boolean isDeleteUntrackedBranches() {
        return deleteUntrackedBranches;
    }

    public void setDeleteUntrackedBranches(boolean deleteUntrackedBranches) {
        this.deleteUntrackedBranches = deleteUntrackedBranches;
    }

    public int getRefreshRate() {
        return refreshRate;
    }

    public void setRefreshRate(int refreshRate) {
        this.refreshRate = refreshRate;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getHostKeyAlgorithm() {
        return hostKeyAlgorithm;
    }

    public void setHostKeyAlgorithm(String hostKeyAlgorithm) {
        this.hostKeyAlgorithm = hostKeyAlgorithm;
    }

    public String getHostKey() {
        return hostKey;
    }

    public void setHostKey(String hostKey) {
        this.hostKey = hostKey;
    }

    public String getKnownHostsFile() {
        return knownHostsFile;
    }

    public void setKnownHostsFile(String knownHostsFile) {
        this.knownHostsFile = knownHostsFile;
    }

    public String getPreferredAuthentications() {
        return preferredAuthentications;
    }

    public void setPreferredAuthentications(String preferredAuthentications) {
        this.preferredAuthentications = preferredAuthentications;
    }

    public boolean isIgnoreLocalSshSettings() {
        return ignoreLocalSshSettings;
    }

    public void setIgnoreLocalSshSettings(boolean ignoreLocalSshSettings) {
        this.ignoreLocalSshSettings = ignoreLocalSshSettings;
    }

    @Override
    public boolean isStrictHostKeyChecking() {
        return strictHostKeyChecking;
    }

    @Override
    public void setStrictHostKeyChecking(boolean strictHostKeyChecking) {
        this.strictHostKeyChecking = strictHostKeyChecking;
    }

    public boolean isSkipSslValidation() {
        return skipSslValidation;
    }

    public void setSkipSslValidation(boolean skipSslValidation) {
        this.skipSslValidation = skipSslValidation;
    }
}
