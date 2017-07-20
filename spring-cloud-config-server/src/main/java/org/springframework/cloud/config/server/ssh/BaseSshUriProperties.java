/*
 * Copyright 2017 the original author or authors.
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

import org.springframework.validation.annotation.Validated;

/**
 * @author Dave Syer
 *
 */
@Validated
@PrivateKeyIsValid
@HostKeyAndAlgoBothExist
@HostKeyAlgoSupported
public class BaseSshUriProperties {

	private String privateKey;
	private String uri;
	private String hostKeyAlgorithm;
	private String hostKey;
	private boolean ignoreLocalSshSettings;
	private boolean strictHostKeyChecking = true;

	public BaseSshUriProperties(String uri, String hostKeyAlgorithm, String hostKey, String privateKey, boolean ignoreLocalSshSettings, boolean strictHostKeyChecking) {
		this.uri = uri;
		this.hostKeyAlgorithm = hostKeyAlgorithm;
		this.hostKey = hostKey;
		this.privateKey = privateKey;
		this.ignoreLocalSshSettings = ignoreLocalSshSettings;
		this.strictHostKeyChecking = strictHostKeyChecking;
	}

	public BaseSshUriProperties() {
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

	public void setPrivateKey(String privateKey) {
		this.privateKey = privateKey;
	}

	public void setIgnoreLocalSshSettings(boolean ignoreLocalSshSettings) {
		this.ignoreLocalSshSettings = ignoreLocalSshSettings;
	}

	public void setStrictHostKeyChecking(boolean strictHostKeyChecking) {
		this.strictHostKeyChecking = strictHostKeyChecking;
	}

	public String toString() {
		return "org.springframework.cloud.config.server.ssh.SshUriProperties(uri=" + this.getUri() + " hostKeyAlgorithm=" + this.getHostKeyAlgorithm() + ", hostKey=" + this.getHostKey() + ", privateKey=" + this.getPrivateKey() + ", ignoreLocalSshSettings=" + this.isIgnoreLocalSshSettings() + ", strictHostKeyChecking=" + this.isStrictHostKeyChecking() + ")";
	}
	
}
