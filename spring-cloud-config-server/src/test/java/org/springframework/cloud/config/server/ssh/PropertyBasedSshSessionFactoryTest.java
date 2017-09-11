/*
 * Copyright 2015 - 2017 the original author or authors.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for property based SSH config processor
 * @author William Tran
 * @author Ollie Hughes
 */
@RunWith(MockitoJUnitRunner.class)
public class PropertyBasedSshSessionFactoryTest {

	private static final String HOST_KEY = "AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBMzCa0AcNbahUFjFYJHIilhJOhKFHuDOOuY+/HqV9kALftitwNYo6dQ+tC9IK5JVZCZfqKfDWVMxspcPDf9eMoE=";
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
	
	@Test
	public void strictHostKeyCheckingIsOptional() {
		SshUri sshKey = new SshUriProperties.SshUriPropertiesBuilder()
				.uri("ssh://gitlab.example.local:3322/somerepo.git")
				.privateKey(PRIVATE_KEY)
				.build();
		setupSessionFactory(sshKey);

		factory.configure(hc, session);
		
		verify(session).setConfig("StrictHostKeyChecking", "no");
		verifyNoMoreInteractions(session);
	}
	
	@Test
	public void strictHostKeyCheckingIsUsed() {
		SshUri sshKey = new SshUriProperties.SshUriPropertiesBuilder()
				.uri("ssh://gitlab.example.local:3322/somerepo.git")
				.hostKey(HOST_KEY)
				.privateKey(PRIVATE_KEY)
				.build();
		setupSessionFactory(sshKey);

		factory.configure(hc, session);
		
		verify(session).setConfig("StrictHostKeyChecking", "yes");
		verifyNoMoreInteractions(session);
	}
	
	@Test
	public void hostKeyAlgorithmIsSpecified() {
		SshUri sshKey = new SshUriProperties.SshUriPropertiesBuilder()
				.uri("ssh://gitlab.example.local:3322/somerepo.git")
				.hostKeyAlgorithm(HOST_KEY_ALGORITHM)
				.hostKey(HOST_KEY)
				.privateKey(PRIVATE_KEY)
				.build();
		setupSessionFactory(sshKey);

		factory.configure(hc, session);
		verify(session).setConfig("server_host_key", HOST_KEY_ALGORITHM);
		verify(session).setConfig("StrictHostKeyChecking", "yes");
		verifyNoMoreInteractions(session);
	}
	
	@Test
	public void privateKeyIsUsed() throws Exception {
		SshUri sshKey = new SshUriProperties.SshUriPropertiesBuilder()
				.uri("git@gitlab.example.local:someorg/somerepo.git")
				.privateKey(PRIVATE_KEY)
				.build();
		setupSessionFactory(sshKey);

		factory.createSession(hc, null, SshUriPropertyProcessor.getHostname(sshKey.getUri()), 22, null);
		verify(jSch).addIdentity("gitlab.example.local", PRIVATE_KEY.getBytes(), null, null);
	}

	@Test
	public void hostKeyIsUsed() throws Exception {
		SshUri sshKey = new SshUriProperties.SshUriPropertiesBuilder()
				.uri("git@gitlab.example.local:someorg/somerepo.git")
				.hostKey(HOST_KEY)
				.privateKey(PRIVATE_KEY)
				.build();
		setupSessionFactory(sshKey);

		factory.createSession(hc, null, SshUriPropertyProcessor.getHostname(sshKey.getUri()), 22, null);
		ArgumentCaptor<HostKey> captor = ArgumentCaptor.forClass(HostKey.class);
		verify(hostKeyRepository).add(captor.capture(), isNull(UserInfo.class));
		HostKey hostKey = captor.getValue();
		Assert.assertEquals("gitlab.example.local", hostKey.getHost());
		Assert.assertEquals(HOST_KEY, hostKey.getKey());
	}

	@Test
	public void preferredAuthenticationsIsSpecified() {
		SshUri sshKey = new SshUriProperties.SshUriPropertiesBuilder()
				.uri("ssh://gitlab.example.local:3322/somerepo.git")
				.privateKey(PRIVATE_KEY)
				.preferredAuthentications("password,keyboard-interactive")
				.build();
		setupSessionFactory(sshKey);

		factory.configure(hc, session);
		verify(session).setConfig("PreferredAuthentications", "password,keyboard-interactive");
		verify(session).setConfig("StrictHostKeyChecking", "no");
		verifyNoMoreInteractions(session);
	}

	@Test
	public void customKnownHostsFileIsUsed() throws Exception {
		SshUri sshKey = new SshUriProperties.SshUriPropertiesBuilder()
				.uri("git@gitlab.example.local:someorg/somerepo.git")
				.privateKey(PRIVATE_KEY)
				.knownHostsFile("/ssh/known_hosts")
				.build();
		setupSessionFactory(sshKey);

		factory.createSession(hc, null, SshUriPropertyProcessor.getHostname(sshKey.getUri()), 22, null);
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

		verify(jSch).setKnownHosts(captor.capture());
		Assert.assertEquals("/ssh/known_hosts", captor.getValue());
	}

	private void setupSessionFactory(SshUri sshKey) {
		Map<String, SshUri> sshKeysByHostname = new HashMap<>();
		sshKeysByHostname.put(SshUriPropertyProcessor.getHostname(sshKey.getUri()), sshKey);
		factory = new PropertyBasedSshSessionFactory(sshKeysByHostname, jSch) ;
		when(hc.getHostName()).thenReturn(SshUriPropertyProcessor.getHostname(sshKey.getUri()));
		when(jSch.getHostKeyRepository()).thenReturn(hostKeyRepository);
	}

	public static String getResourceAsString(String path) {
		try {
			Resource resource = new ClassPathResource(path);
			try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
				StringBuilder builder = new StringBuilder();
				String line = "";
				while ((line = br.readLine()) != null) {
					builder.append(line).append('\n');
				}
				return builder.toString();
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
