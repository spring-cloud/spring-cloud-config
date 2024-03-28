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
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.sshd.common.config.keys.impl.ECDSAPublicKeyEntryDecoder;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.common.session.SessionContext;
import org.eclipse.jgit.transport.SshConfigStore;
import org.eclipse.jgit.transport.sshd.ProxyData;
import org.eclipse.jgit.transport.sshd.ProxyDataFactory;
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.springframework.cloud.config.server.environment.JGitEnvironmentProperties;
import org.springframework.cloud.config.server.proxy.ProxyHostProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for property based SSH config processor.
 *
 * @author William Tran
 * @author Ollie Hughes
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PropertyBasedSshSessionFactoryTest {

	private static final String HOST_KEY = "AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAAB"
			+ "BBMzCa0AcNbahUFjFYJHIilhJOhKFHuDOOuY+/HqV9kALftitwNYo6dQ+tC9IK5JVZCZfqKfDWVMxspcPDf9eMoE=";

	private static final String HOST_KEY_ALGORITHM = "ecdsa-sha2-nistp256";

	private static final String PRIVATE_KEY = getResourceAsString("/ssh/key");

	private PropertyBasedSshSessionFactory factory;

	public static String getResourceAsString(String path) {
		try {
			Resource resource = new ClassPathResource(path);
			try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
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

		SshConfigStore.HostConfig sshConfig = getSshHostConfig("gitlab.example.local");

		assertThat(sshConfig.getValue("StrictHostKeyChecking")).isEqualTo("no");
	}

	@Test
	public void strictHostKeyCheckingIsUsed() {
		JGitEnvironmentProperties sshKey = new JGitEnvironmentProperties();
		sshKey.setUri("ssh://gitlab.example.local:3322/somerepo.git");
		sshKey.setHostKey(HOST_KEY);
		sshKey.setPrivateKey(PRIVATE_KEY);
		setupSessionFactory(sshKey);

		SshConfigStore.HostConfig sshConfig = getSshHostConfig("gitlab.example.local");

		assertThat(sshConfig.getValue("StrictHostKeyChecking")).isEqualTo("yes");
	}

	@Test
	public void sshConfigIsUsedForRelevantHostOnly() {
		JGitEnvironmentProperties sshKey = new JGitEnvironmentProperties();
		sshKey.setUri("ssh://gitlab.example.local:3322/somerepo.git");
		sshKey.setPrivateKey(PRIVATE_KEY);
		setupSessionFactory(sshKey);

		assertThatThrownBy(() -> getSshHostConfig("another.host")).isInstanceOf(NullPointerException.class);
	}

	@Test
	public void hostKeyAlgorithmIsSpecified() {
		JGitEnvironmentProperties sshKey = new JGitEnvironmentProperties();
		sshKey.setUri("ssh://gitlab.example.local:3322/somerepo.git");
		sshKey.setHostKeyAlgorithm(HOST_KEY_ALGORITHM);
		sshKey.setHostKey(HOST_KEY);
		sshKey.setPrivateKey(PRIVATE_KEY);
		setupSessionFactory(sshKey);

		PublicKey hostKey = getSshHostKey("gitlab.example.local");

		assertThat(hostKey).isNotNull();
		assertThat(hostKey.getAlgorithm()).isEqualTo(toPublicKey(HOST_KEY, HOST_KEY_ALGORITHM).getAlgorithm());
	}

	@Test
	public void privateKeyIsUsed() {
		JGitEnvironmentProperties sshKey = new JGitEnvironmentProperties();
		sshKey.setUri("git@gitlab.example.local:someorg/somerepo.git");
		sshKey.setPrivateKey(PRIVATE_KEY);
		setupSessionFactory(sshKey);

		PrivateKey privateKey = getSshPrivateKey("gitlab.example.local");

		assertThat(privateKey).isNotNull();
		assertThat(privateKey).isEqualTo(toPrivateKey(PRIVATE_KEY, null));
	}

	@Test
	public void privateKeyIsUsedWithRepoIp() {
		JGitEnvironmentProperties sshKey = new JGitEnvironmentProperties();
		sshKey.setUri("git@127.0.0.1:someorg/somerepo.git");
		sshKey.setPrivateKey(PRIVATE_KEY);
		setupSessionFactory(sshKey);

		PrivateKey privateKey = getSshPrivateKey("gitlab.example.local");

		assertThat(privateKey).isNotNull();
		assertThat(privateKey).isEqualTo(toPrivateKey(PRIVATE_KEY, null));
	}

	@Test
	public void privateKeyWithPassphraseIsUsed() {
		String keyWithPassphrase = getResourceAsString("/ssh/key-with-passphrase");

		JGitEnvironmentProperties sshKey = new JGitEnvironmentProperties();
		sshKey.setUri("git@gitlab.example.local:someorg/somerepo.git");
		sshKey.setPrivateKey(keyWithPassphrase);
		sshKey.setPassphrase("secret");
		setupSessionFactory(sshKey);

		PrivateKey privateKey = getSshPrivateKey("gitlab.example.local");

		assertThat(privateKey).isNotNull();
		assertThat(privateKey).isEqualTo(toPrivateKey(keyWithPassphrase, "secret"));
	}

	@Test
	public void hostKeyIsUsed() {
		JGitEnvironmentProperties sshKey = new JGitEnvironmentProperties();
		sshKey.setUri("git@gitlab.example.local:someorg/somerepo.git");
		sshKey.setHostKeyAlgorithm(HOST_KEY_ALGORITHM);
		sshKey.setHostKey(HOST_KEY);
		sshKey.setStrictHostKeyChecking(true);
		sshKey.setPrivateKey(PRIVATE_KEY);
		setupSessionFactory(sshKey);
		PublicKey configuredKey = toPublicKey(HOST_KEY, HOST_KEY_ALGORITHM);

		PublicKey knownHostKey = getSshHostKey("gitlab.example.local");

		assertThat(knownHostKey).isNotNull();
		assertThat(knownHostKey).isEqualTo(configuredKey);
		assertThat(isKnownKeyForHost(configuredKey, "gitlab.example.local")).isTrue();
	}

	@Test
	public void hostKeyIsUsedWithRepoIp() {
		JGitEnvironmentProperties sshKey = new JGitEnvironmentProperties();
		sshKey.setUri("git@127.0.0.1:someorg/somerepo.git");
		sshKey.setHostKeyAlgorithm(HOST_KEY_ALGORITHM);
		sshKey.setHostKey(HOST_KEY);
		sshKey.setPrivateKey(PRIVATE_KEY);
		setupSessionFactory(sshKey);
		PublicKey configuredKey = toPublicKey(HOST_KEY, HOST_KEY_ALGORITHM);

		PublicKey knownHostKey = getSshHostKey("gitlab.example.local");

		assertThat(knownHostKey).isNotNull();
		assertThat(knownHostKey).isEqualTo(configuredKey);
		assertThat(isKnownKeyForHost(configuredKey, "gitlab.example.local")).isTrue();
	}

	@Test
	public void preferredAuthenticationsIsSpecified() {
		JGitEnvironmentProperties sshKey = new JGitEnvironmentProperties();
		sshKey.setUri("ssh://gitlab.example.local:3322/somerepo.git");
		sshKey.setPrivateKey(PRIVATE_KEY);
		sshKey.setPreferredAuthentications("password,keyboard-interactive");
		setupSessionFactory(sshKey);

		SshConfigStore.HostConfig sshConfig = getSshHostConfig("gitlab.example.local");

		assertThat(sshConfig.getValue("PreferredAuthentications")).isEqualTo("password,keyboard-interactive");
		assertThat(sshConfig.getValue("StrictHostKeyChecking")).isEqualTo("no");
	}

	@Test
	public void customKnownHostsFileIsUsed() throws IOException {
		JGitEnvironmentProperties sshKey = new JGitEnvironmentProperties();
		sshKey.setUri("git@gitlab.example.local:someorg/somerepo.git");
		sshKey.setPrivateKey(PRIVATE_KEY);
		sshKey.setKnownHostsFile(new ClassPathResource("/ssh/known_hosts").getFile().getPath());
		setupSessionFactory(sshKey);
		PublicKey configuredKey = toPublicKey(HOST_KEY, HOST_KEY_ALGORITHM);

		PublicKey knownHostKey = getSshHostKey("gitlab.example.local");

		assertThat(knownHostKey).isNotNull();
		assertThat(knownHostKey).isEqualTo(configuredKey);
		assertThat(isKnownKeyForHost(configuredKey, "gitlab.example.local")).isTrue();
	}

	@Test
	public void proxySettingsIsUsed() {
		JGitEnvironmentProperties sshProperties = new JGitEnvironmentProperties();
		sshProperties.setUri("ssh://gitlab.example.local:3322/somerepo.git");
		sshProperties.setPrivateKey(PRIVATE_KEY);
		Map<ProxyHostProperties.ProxyForScheme, ProxyHostProperties> map = new HashMap<>();
		ProxyHostProperties proxyHostProperties = new ProxyHostProperties();
		proxyHostProperties.setHost("host.domain");
		proxyHostProperties.setPort(8080);
		proxyHostProperties.setUsername("user");
		proxyHostProperties.setPassword("password");
		map.put(ProxyHostProperties.ProxyForScheme.HTTP, proxyHostProperties);
		sshProperties.setProxy(map);
		setupSessionFactory(sshProperties);

		ProxyData proxyData = getSshProxyData("gitlab.example.local");

		assertThat(proxyData).isNotNull();
		assertThat(proxyData.getUser()).isEqualTo("user");
		assertThat(new String(proxyData.getPassword())).isEqualTo("password");
		assertThat(proxyData.getProxy().type().toString()).isEqualTo("HTTP");
		assertThat(proxyData.getProxy().address().toString()).containsPattern("host\\.domain.*:8080");
	}

	@Test
	public void defaultSshConfigIsSet() {
		JGitEnvironmentProperties sshProperties = new JGitEnvironmentProperties();
		sshProperties.setUri("ssh://gitlab.example.local:3322/somerepo.git");
		setupSessionFactory(sshProperties);

		SshConfigStore.HostConfig sshConfig = getDefaultSshHostConfig("gitlab.example.local", 123, "user.name");

		assertThat(sshConfig.getValue("HostName")).isEqualTo("gitlab.example.local");
		assertThat(sshConfig.getValue("Port")).isEqualTo("123");
		assertThat(sshConfig.getValue("User")).isEqualTo("user.name");
		assertThat(sshConfig.getValue("ConnectionAttempts")).isEqualTo("1");
	}

	@Test
	public void proxySettingsIsUsedWithRepoIp() {
		JGitEnvironmentProperties sshProperties = new JGitEnvironmentProperties();
		sshProperties.setUri("ssh://127.0.0.1:3322/somerepo.git");
		sshProperties.setPrivateKey(PRIVATE_KEY);
		Map<ProxyHostProperties.ProxyForScheme, ProxyHostProperties> map = new HashMap<>();
		ProxyHostProperties proxyHostProperties = new ProxyHostProperties();
		proxyHostProperties.setHost("host.domain");
		proxyHostProperties.setPort(8080);
		map.put(ProxyHostProperties.ProxyForScheme.HTTP, proxyHostProperties);
		sshProperties.setProxy(map);
		setupSessionFactory(sshProperties);

		ProxyData proxyData = getSshProxyData("gitlab.example.local");

		assertThat(proxyData).isNotNull();
		assertThat(proxyData.getProxy().type().toString()).isEqualTo("HTTP");
		assertThat(proxyData.getProxy().address().toString()).containsPattern("host\\.domain.*:8080");
	}

	@Test
	public void connectTimeoutIsUsed() {
		JGitEnvironmentProperties sshKey = new JGitEnvironmentProperties();
		sshKey.setUri("ssh://gitlab.example.local:3322/somerepo.git");
		setupSessionFactory(sshKey);

		SshConfigStore.HostConfig sshConfig = getSshHostConfig("gitlab.example.local");

		assertThat(sshConfig.getValue("ConnectTimeout")).isEqualTo("5");
	}

	@Test
	public void sshConfigFileIsNotUsed() {
		setupSessionFactory(new JGitEnvironmentProperties());

		assertThat(factory.getSshConfig(new File("."))).isNull();
	}

	private ProxyData getSshProxyData(String hostname) {
		try {
			Field proxies = SshdSessionFactory.class.getDeclaredField("proxies");
			proxies.setAccessible(true);
			ProxyDataFactory proxyDataFactory = (ProxyDataFactory) proxies.get(factory);
			proxies.setAccessible(false);
			return proxyDataFactory.get(setupSocketAddress(hostname));
		}
		catch (NoSuchFieldException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	private PublicKey toPublicKey(String key, String algorithm) {
		try {
			return new ECDSAPublicKeyEntryDecoder().decodePublicKey(null, algorithm, Base64.getDecoder().decode(key),
					Collections.emptyMap());
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private PrivateKey toPrivateKey(String key, String passphrase) {
		try {
			Collection<KeyPair> keyPairs = KeyPairUtils.load(null, key, passphrase);

			return keyPairs.isEmpty() ? null : keyPairs.iterator().next().getPrivate();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private PrivateKey getSshPrivateKey(String hostname) {
		InetSocketAddress address = setupSocketAddress(hostname);
		SessionContext session = mock(SessionContext.class);
		when(session.getRemoteAddress()).thenReturn(address);

		List<KeyPair> kayPairs;
		try {
			Spliterator<KeyPair> spliterator = ((KeyIdentityProvider) factory.getDefaultKeys(new File(".")))
					.loadKeys(session).spliterator();

			kayPairs = StreamSupport.stream(spliterator, false).collect(Collectors.toList());
		}
		catch (IOException | GeneralSecurityException e) {
			throw new RuntimeException(e);
		}

		return kayPairs.isEmpty() ? null : kayPairs.get(0).getPrivate();
	}

	private PublicKey getSshHostKey(String hostname) {
		InetSocketAddress address = setupSocketAddress(hostname);
		List<PublicKey> publicKeys = factory.getServerKeyDatabase(null, null).lookup("address", address,
				mock(ServerKeyDatabase.Configuration.class));

		return publicKeys.isEmpty() ? null : publicKeys.get(0);
	}

	private boolean isKnownKeyForHost(PublicKey publicKey, String hostname) {
		InetSocketAddress address = setupSocketAddress(hostname);
		return factory.getServerKeyDatabase(null, null).accept("address", address, publicKey,
				mock(ServerKeyDatabase.Configuration.class), null);
	}

	private SshConfigStore.HostConfig getSshHostConfig(String hostname) {
		return factory.createSshConfigStore(new File("dummy"), new File("dummy"), "localUserName").lookup(hostname, 22,
				"userName");
	}

	private SshConfigStore.HostConfig getDefaultSshHostConfig(String hostName, int port, String username) {
		return factory.createSshConfigStore(new File("dummy"), new File("dummy"), "localUserName")
				.lookupDefault(hostName, port, username);
	}

	private void setupSessionFactory(JGitEnvironmentProperties sshKey) {
		Map<String, JGitEnvironmentProperties> sshKeysByHostname = new HashMap<>();
		sshKeysByHostname.put(SshUriPropertyProcessor.getHostname(sshKey.getUri()), sshKey);
		this.factory = new PropertyBasedSshSessionFactory(sshKeysByHostname);
	}

	private InetSocketAddress setupSocketAddress(String hostname) {
		InetAddress address = mock(InetAddress.class);
		when(address.getHostAddress()).thenReturn("127.0.0.1");

		InetSocketAddress socketAddress = mock(InetSocketAddress.class);
		when(socketAddress.getAddress()).thenReturn(address);
		when(socketAddress.getHostString()).thenReturn(hostname);
		when(socketAddress.getHostName()).thenReturn(hostname);
		when(socketAddress.getPort()).thenReturn(22);

		return socketAddress;
	}

}
