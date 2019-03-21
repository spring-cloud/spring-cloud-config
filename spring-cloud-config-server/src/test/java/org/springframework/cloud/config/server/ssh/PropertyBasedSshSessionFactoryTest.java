/*
 * Copyright 2015-2019 the original author or authors.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.ProxyHTTP;
import com.jcraft.jsch.Session;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.cloud.config.server.environment.JGitEnvironmentProperties;
import org.springframework.cloud.config.server.proxy.ProxyHostProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for property based SSH config processor.
 *
 * @author William Tran
 * @author Ollie Hughes
 */
@RunWith(MockitoJUnitRunner.class)
public class PropertyBasedSshSessionFactoryTest {

	private static final String HOST_KEY = "AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAAB"
			+ "BBMzCa0AcNbahUFjFYJHIilhJOhKFHuDOOuY+/HqV9kALftitwNYo6dQ+tC9IK5JVZCZfqKfDWVMxspcPDf9eMoE=";

	private static final String HOST_KEY_ALGORITHM = "ecdsa-sha2-nistp256";

	private static final String PRIVATE_KEY = getResourceAsString("/ssh/key");

	private PropertyBasedSshSessionFactory factory;

	@Mock
	private Host hc;

	@Mock
	private Session session;

	@Mock
	private JSch jSch;

	@Mock
	private HostKeyRepository hostKeyRepository;

	@Mock
	private ProxyHTTP proxyMock;

	public static String getResourceAsString(String path) {
		try {
			Resource resource = new ClassPathResource(path);
			try (BufferedReader br = new BufferedReader(
					new InputStreamReader(resource.getInputStream()))) {
				StringBuilder builder = new StringBuilder();
				String line;
				while ((line = br.readLine()) != null) {
					builder.append(line).append('\n');
				}
				return builder.toString();
			}
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	public void strictHostKeyCheckingIsOptional() {
		JGitEnvironmentProperties sshKey = new JGitEnvironmentProperties();
		sshKey.setUri("ssh://gitlab.example.local:3322/somerepo.git");
		sshKey.setPrivateKey(PRIVATE_KEY);
		setupSessionFactory(sshKey);

		this.factory.configure(this.hc, this.session);

		verify(this.session).setConfig("StrictHostKeyChecking", "no");
		verifyNoMoreInteractions(this.session);
	}

	@Test
	public void strictHostKeyCheckingIsUsed() {
		JGitEnvironmentProperties sshKey = new JGitEnvironmentProperties();
		sshKey.setUri("ssh://gitlab.example.local:3322/somerepo.git");
		sshKey.setHostKey(HOST_KEY);
		sshKey.setPrivateKey(PRIVATE_KEY);
		setupSessionFactory(sshKey);

		this.factory.configure(this.hc, this.session);

		verify(this.session).setConfig("StrictHostKeyChecking", "yes");
		verifyNoMoreInteractions(this.session);
	}

	@Test
	public void hostKeyAlgorithmIsSpecified() {
		JGitEnvironmentProperties sshKey = new JGitEnvironmentProperties();
		sshKey.setUri("ssh://gitlab.example.local:3322/somerepo.git");
		sshKey.setHostKeyAlgorithm(HOST_KEY_ALGORITHM);
		sshKey.setHostKey(HOST_KEY);
		sshKey.setPrivateKey(PRIVATE_KEY);
		setupSessionFactory(sshKey);

		this.factory.configure(this.hc, this.session);
		verify(this.session).setConfig("server_host_key", HOST_KEY_ALGORITHM);
		verify(this.session).setConfig("StrictHostKeyChecking", "yes");
		verifyNoMoreInteractions(this.session);
	}

	@Test
	public void privateKeyIsUsed() throws Exception {
		JGitEnvironmentProperties sshKey = new JGitEnvironmentProperties();
		sshKey.setUri("git@gitlab.example.local:someorg/somerepo.git");
		sshKey.setPrivateKey(PRIVATE_KEY);
		setupSessionFactory(sshKey);

		this.factory.createSession(this.hc, null,
				SshUriPropertyProcessor.getHostname(sshKey.getUri()), 22, null);
		verify(this.jSch).addIdentity("gitlab.example.local", PRIVATE_KEY.getBytes(),
				null, null);
	}

	@Test
	public void hostKeyIsUsed() throws Exception {
		JGitEnvironmentProperties sshKey = new JGitEnvironmentProperties();
		sshKey.setUri("git@gitlab.example.local:someorg/somerepo.git");
		sshKey.setHostKey(HOST_KEY);
		sshKey.setPrivateKey(PRIVATE_KEY);
		setupSessionFactory(sshKey);

		this.factory.createSession(this.hc, null,
				SshUriPropertyProcessor.getHostname(sshKey.getUri()), 22, null);
		ArgumentCaptor<HostKey> captor = ArgumentCaptor.forClass(HostKey.class);
		verify(this.hostKeyRepository).add(captor.capture(), isNull());
		HostKey hostKey = captor.getValue();
		assertThat(hostKey.getHost()).isEqualTo("gitlab.example.local");
		assertThat(hostKey.getKey()).isEqualTo(HOST_KEY);
	}

	@Test
	public void preferredAuthenticationsIsSpecified() {
		JGitEnvironmentProperties sshKey = new JGitEnvironmentProperties();
		sshKey.setUri("ssh://gitlab.example.local:3322/somerepo.git");
		sshKey.setPrivateKey(PRIVATE_KEY);
		sshKey.setPreferredAuthentications("password,keyboard-interactive");
		setupSessionFactory(sshKey);

		this.factory.configure(this.hc, this.session);
		verify(this.session).setConfig("PreferredAuthentications",
				"password,keyboard-interactive");
		verify(this.session).setConfig("StrictHostKeyChecking", "no");
		verifyNoMoreInteractions(this.session);
	}

	@Test
	public void customKnownHostsFileIsUsed() throws Exception {
		JGitEnvironmentProperties sshKey = new JGitEnvironmentProperties();
		sshKey.setUri("git@gitlab.example.local:someorg/somerepo.git");
		sshKey.setPrivateKey(PRIVATE_KEY);
		sshKey.setKnownHostsFile("/ssh/known_hosts");
		setupSessionFactory(sshKey);

		this.factory.createSession(this.hc, null,
				SshUriPropertyProcessor.getHostname(sshKey.getUri()), 22, null);
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

		verify(this.jSch).setKnownHosts(captor.capture());
		assertThat(captor.getValue()).isEqualTo("/ssh/known_hosts");
	}

	@Test
	public void proxySettingsIsUsed() {
		JGitEnvironmentProperties sshProperties = new JGitEnvironmentProperties();
		sshProperties.setPrivateKey(PRIVATE_KEY);
		Map<ProxyHostProperties.ProxyForScheme, ProxyHostProperties> map = new HashMap<>();
		ProxyHostProperties proxyHostProperties = new ProxyHostProperties();
		proxyHostProperties.setUsername("user");
		proxyHostProperties.setPassword("password");
		map.put(ProxyHostProperties.ProxyForScheme.HTTP, proxyHostProperties);

		sshProperties.setProxy(map);
		setupSessionFactory(sshProperties);

		this.factory.configure(this.hc, this.session);
		ArgumentCaptor<ProxyHTTP> captor = ArgumentCaptor.forClass(ProxyHTTP.class);

		verify(this.session).setProxy(captor.capture());
		assertThat(captor.getValue()).isNotNull();
		verify(this.proxyMock).setUserPasswd("user", "password");
	}

	private void setupSessionFactory(JGitEnvironmentProperties sshKey) {
		Map<String, JGitEnvironmentProperties> sshKeysByHostname = new HashMap<>();
		sshKeysByHostname.put(SshUriPropertyProcessor.getHostname(sshKey.getUri()),
				sshKey);
		this.factory = new PropertyBasedSshSessionFactory(sshKeysByHostname, this.jSch) {

			@Override
			protected ProxyHTTP createProxy(ProxyHostProperties proxyHostProperties) {
				return proxyMock;
			}
		};
		when(this.hc.getHostName())
				.thenReturn(SshUriPropertyProcessor.getHostname(sshKey.getUri()));
		when(this.jSch.getHostKeyRepository()).thenReturn(this.hostKeyRepository);
	}

}
