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

package org.springframework.cloud.config.server.ssh;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;

import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentProperties;

/**
 * Configure JGit transport command to use a default SSH session factory based on local
 * machines SSH config. Allow strict host key checking to be set.
 *
 * @author Dylan Roberts
 */
public class FileBasedSshTransportConfigCallback implements TransportConfigCallback {

	private final MultipleJGitEnvironmentProperties sshUriProperties;

	public FileBasedSshTransportConfigCallback(MultipleJGitEnvironmentProperties sshUriProperties) {
		this.sshUriProperties = sshUriProperties;
	}

	public MultipleJGitEnvironmentProperties getSshUriProperties() {
		return this.sshUriProperties;
	}

	@Override
	public void configure(Transport transport) {
		if (transport instanceof SshTransport) {
			((SshTransport) transport).setSshSessionFactory(new FileBasedSshSessionFactory(
					new SshUriPropertyProcessor(this.sshUriProperties).getSshKeysByHostname()));
		}
	}

}
