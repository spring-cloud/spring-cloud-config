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

import java.io.File;
import java.util.Map;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.transport.ssh.OpenSshConfigFile;
import org.eclipse.jgit.transport.SshConfigStore;
import org.eclipse.jgit.transport.SshConstants;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;

import org.springframework.cloud.config.server.environment.JGitEnvironmentProperties;

public class FileBasedSshSessionFactory extends SshdSessionFactory {

	private final Map<String, JGitEnvironmentProperties> sshKeysByHostname;

	public FileBasedSshSessionFactory(Map<String, JGitEnvironmentProperties> sshKeysByHostname) {
		this.sshKeysByHostname = sshKeysByHostname;
		assert this.sshKeysByHostname.entrySet().size() > 0;
	}

	@Override
	protected SshConfigStore createSshConfigStore(File homeDir, File configFile, String localUserName) {
		return configFile == null ? null : new OpenSshConfigFile(homeDir, configFile, localUserName) {

			@Override
			public HostEntry lookup(@NonNull String hostName, int port, String userName) {
				HostEntry hostEntry = super.lookup(hostName, port, userName);

				JGitEnvironmentProperties sshProperties = sshKeysByHostname.get(hostName);
				if (sshProperties == null) {
					return hostEntry;
				}

				hostEntry.setValue(SshConstants.STRICT_HOST_KEY_CHECKING,
						sshProperties.isStrictHostKeyChecking() ? SshConstants.YES : SshConstants.NO);

				hostEntry.setValue(SshConstants.CONNECT_TIMEOUT, String.valueOf(sshProperties.getTimeout()));

				return hostEntry;
			}
		};
	}

}
