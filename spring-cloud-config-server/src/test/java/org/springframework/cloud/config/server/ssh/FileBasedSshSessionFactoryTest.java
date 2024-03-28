/*
 * Copyright 2015-2022 the original author or authors.
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
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.transport.SshConfigStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.cloud.config.server.environment.JGitEnvironmentProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for file based SSH config processor.
 */
@ExtendWith(MockitoExtension.class)
public class FileBasedSshSessionFactoryTest {

	private FileBasedSshSessionFactory factory;

	@Test
	public void strictHostKeyCheckingIsOptional() {
		JGitEnvironmentProperties sshKey = new JGitEnvironmentProperties();
		sshKey.setUri("ssh://gitlab.example.local:3322/somerepo.git");
		sshKey.setStrictHostKeyChecking(false);
		setupSessionFactory(sshKey);

		SshConfigStore.HostConfig sshConfig = getSshHostConfig("gitlab.example.local");

		assertThat(sshConfig.getValue("StrictHostKeyChecking")).isEqualTo("no");
	}

	@Test
	public void strictHostKeyCheckingIsUsed() {
		JGitEnvironmentProperties sshKey = new JGitEnvironmentProperties();
		sshKey.setUri("ssh://gitlab.example.local:3322/somerepo.git");
		setupSessionFactory(sshKey);

		SshConfigStore.HostConfig sshConfig = getSshHostConfig("gitlab.example.local");

		assertThat(sshConfig.getValue("StrictHostKeyChecking")).isEqualTo("yes");
	}

	@Test
	public void sshConfigurationIsDoneForRelevantHostOnly() {
		JGitEnvironmentProperties sshKey = new JGitEnvironmentProperties();
		sshKey.setUri("ssh://gitlab.example.local:3322/somerepo.git");
		setupSessionFactory(sshKey);

		SshConfigStore.HostConfig sshConfig = getSshHostConfig("another.host");

		assertThat(sshConfig.getValue("StrictHostKeyChecking")).isNull();
	}

	@Test
	public void handlesNullConfigFile() {
		JGitEnvironmentProperties sshKey = new JGitEnvironmentProperties();
		sshKey.setUri("ssh://gitlab.example.local:3322/somerepo.git");
		setupSessionFactory(sshKey);

		SshConfigStore configStore = factory.createSshConfigStore(new File("dummy"), null, "localUserName");

		assertThat(configStore).isNull();
	}

	@Test
	public void connectTimeoutIsUsed() {
		JGitEnvironmentProperties sshKey = new JGitEnvironmentProperties();
		sshKey.setUri("ssh://gitlab.example.local:3322/somerepo.git");
		setupSessionFactory(sshKey);

		SshConfigStore.HostConfig sshConfig = getSshHostConfig("gitlab.example.local");

		assertThat(sshConfig.getValue("ConnectTimeout")).isEqualTo("5");
	}

	private SshConfigStore.HostConfig getSshHostConfig(String hostName) {
		return factory.createSshConfigStore(new File("dummy"), new File("dummy"), "localUserName").lookup(hostName, 22,
				"userName");
	}

	private void setupSessionFactory(JGitEnvironmentProperties sshKey) {
		Map<String, JGitEnvironmentProperties> sshKeysByHostname = new HashMap<>();
		sshKeysByHostname.put(SshUriPropertyProcessor.getHostname(sshKey.getUri()), sshKey);
		this.factory = new FileBasedSshSessionFactory(sshKeysByHostname);
	}

}
