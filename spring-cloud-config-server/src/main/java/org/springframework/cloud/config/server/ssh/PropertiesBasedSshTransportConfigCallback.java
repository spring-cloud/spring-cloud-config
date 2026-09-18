/*
 * Copyright 2018-present the original author or authors.
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

import java.util.Map;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;

import org.springframework.cloud.config.server.environment.JGitEnvironmentProperties;

/**
 * Configure JGit transport command to use a SSH session factory that is configured using
 * properties defined in {@link JGitEnvironmentProperties}.
 *
 * @author Dylan Roberts
 */
public class PropertiesBasedSshTransportConfigCallback implements TransportConfigCallback {

	private final JGitEnvironmentProperties sshUriProperties;

	private final PropertyBasedSshSessionFactory sshdSessionFactory;

	public PropertiesBasedSshTransportConfigCallback(JGitEnvironmentProperties sshUriProperties) {
		this.sshUriProperties = sshUriProperties;

		Map<String, JGitEnvironmentProperties> sshKeysByHostname = new SshUriPropertyProcessor(this.sshUriProperties)
			.getSshKeysByHostname();

		if (sshKeysByHostname.isEmpty()) {
			this.sshdSessionFactory = null;
		}
		else {
			this.sshdSessionFactory = new PropertyBasedSshSessionFactory(sshKeysByHostname);
		}
	}

	public JGitEnvironmentProperties getSshUriProperties() {
		return this.sshUriProperties;
	}

	@Override
	public void configure(Transport transport) {
		if (this.sshdSessionFactory != null && transport instanceof SshTransport) {
			((SshTransport) transport).setSshSessionFactory(this.sshdSessionFactory);
		}
	}

}
