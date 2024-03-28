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

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.internal.transport.ssh.OpenSshConfigFile;
import org.eclipse.jgit.internal.transport.sshd.OpenSshServerKeyDatabase;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshConfigStore;
import org.eclipse.jgit.transport.SshConstants;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.ProxyData;
import org.eclipse.jgit.transport.sshd.ProxyDataFactory;
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;

import org.springframework.cloud.config.server.environment.JGitEnvironmentProperties;
import org.springframework.cloud.config.server.proxy.ProxyHostProperties;
import org.springframework.util.StringUtils;

/**
 * In a cloud environment local SSH config files such as `.known_hosts` may not be
 * suitable for providing configuration settings due to ephemeral filesystems. This flag
 * enables SSH config to be provided as application properties
 *
 * @author William Tran
 * @author Ollie Hughes
 */
public class PropertyBasedSshSessionFactory extends SshdSessionFactory {

	private final Map<String, JGitEnvironmentProperties> sshKeysByHostname;

	public PropertyBasedSshSessionFactory(Map<String, JGitEnvironmentProperties> sshKeysByHostname) {
		super(new JGitKeyCache(), new HttpProxyDataFactory(sshKeysByHostname));

		this.sshKeysByHostname = sshKeysByHostname;
		assert this.sshKeysByHostname.entrySet().size() > 0;
	}

	@Override
	protected SshConfigStore createSshConfigStore(File homeDir, File configFile, String localUserName) {
		return new SshConfigStore() {

			@Override
			public HostConfig lookup(@NonNull String hostName, int port, String userName) {
				OpenSshConfigFile.HostEntry hostEntry = new OpenSshConfigFile.HostEntry();

				return updateIfNeeded(hostEntry, hostName);
			}

			@Override
			public HostConfig lookupDefault(String hostName, int port, String userName) {
				OpenSshConfigFile.HostEntry hostEntry = new OpenSshConfigFile.HostEntry();

				hostEntry.setValue(SshConstants.HOST_NAME, hostName);
				hostEntry.setValue(SshConstants.PORT,
						Integer.toString(port > 0 ? port : SshConstants.SSH_DEFAULT_PORT));
				hostEntry.setValue(SshConstants.USER, userName);
				hostEntry.setValue(SshConstants.CONNECTION_ATTEMPTS, "1");

				return updateIfNeeded(hostEntry, hostName);
			}

			private OpenSshConfigFile.HostEntry updateIfNeeded(OpenSshConfigFile.HostEntry hostEntry, String hostName) {
				JGitEnvironmentProperties sshProperties = sshKeysByHostname.get(hostName);

				hostEntry.setValue(SshConstants.CONNECT_TIMEOUT, String.valueOf(sshProperties.getTimeout()));

				if (sshProperties.getHostKey() == null || !sshProperties.isStrictHostKeyChecking()) {
					hostEntry.setValue(SshConstants.STRICT_HOST_KEY_CHECKING, SshConstants.NO);
				}
				else {
					hostEntry.setValue(SshConstants.STRICT_HOST_KEY_CHECKING, SshConstants.YES);
				}

				String preferredAuthentications = sshProperties.getPreferredAuthentications();
				if (preferredAuthentications != null) {
					hostEntry.setValue(SshConstants.PREFERRED_AUTHENTICATIONS, preferredAuthentications);
				}
				return hostEntry;
			}
		};
	}

	@Override
	protected File getSshConfig(File dir) {
		// Do not use a config file.
		return null;
	}

	@Override
	protected ServerKeyDatabase getServerKeyDatabase(File homeDir, File dir) {
		return new ServerKeyDatabase() {
			@Override
			public List<PublicKey> lookup(String connectAddress, InetSocketAddress remoteAddress,
					Configuration config) {

				JGitEnvironmentProperties sshProperties = findEnvironmentProperties(sshKeysByHostname, remoteAddress);

				List<Path> knownHostFiles = getKnownHostFiles(sshProperties);
				List<PublicKey> publicKeys = new OpenSshServerKeyDatabase(false, knownHostFiles).lookup(connectAddress,
						remoteAddress, config);

				PublicKey publicKey = getHostKey(sshProperties);
				if (publicKey != null) {
					publicKeys.add(publicKey);
				}

				return publicKeys;
			}

			@Override
			public boolean accept(String connectAddress, InetSocketAddress remoteAddress, PublicKey serverKey,
					Configuration config, CredentialsProvider provider) {

				if (config.getStrictHostKeyChecking() == Configuration.StrictHostKeyChecking.ACCEPT_ANY) {
					return true;
				}

				List<PublicKey> knownServerKeys = lookup(connectAddress, remoteAddress, config);

				return KeyUtils.findMatchingKey(serverKey, knownServerKeys) != null;
			}

			private PublicKey getHostKey(JGitEnvironmentProperties sshProperties) {
				String hostKey = sshProperties.getHostKey();
				String hostKeyAlgorithm = sshProperties.getHostKeyAlgorithm();
				if (!StringUtils.hasText(hostKey) || !StringUtils.hasText(hostKeyAlgorithm)) {
					return null;
				}

				try {
					return AuthorizedKeyEntry.parseAuthorizedKeyEntry(hostKeyAlgorithm + " " + hostKey)
							.resolvePublicKey(null, null);
				}
				catch (IOException | GeneralSecurityException e) {
					throw new RuntimeException(e);
				}
			}

			private List<Path> getKnownHostFiles(JGitEnvironmentProperties sshProperties) {
				if (sshProperties.getKnownHostsFile() == null) {
					return Collections.emptyList();
				}
				else {
					return Collections.singletonList(Paths.get(sshProperties.getKnownHostsFile()));
				}
			}

		};
	}

	@Override
	protected Iterable<KeyPair> getDefaultKeys(File dir) {
		return new SingleKeyIdentityProvider(sshKeysByHostname);
	}

	private static JGitEnvironmentProperties findEnvironmentProperties(
			Map<String, JGitEnvironmentProperties> sshKeysByHostname, InetSocketAddress socketAddress) {

		JGitEnvironmentProperties sshProperties = sshKeysByHostname.get(socketAddress.getHostString());

		if (sshProperties == null && socketAddress.getAddress() != null) {
			sshProperties = sshKeysByHostname.get(socketAddress.getAddress().getHostAddress());
		}

		return sshProperties;
	}

	private final static class SingleKeyIdentityProvider implements KeyIdentityProvider, Iterable<KeyPair> {

		private final Map<String, JGitEnvironmentProperties> sshKeysByHostname;

		private SingleKeyIdentityProvider(Map<String, JGitEnvironmentProperties> sshKeysByHostname) {
			this.sshKeysByHostname = sshKeysByHostname;
		}

		@Override
		public Iterator<KeyPair> iterator() {
			throw new UnsupportedOperationException("Should not be called");
		}

		@Override
		public Iterable<KeyPair> loadKeys(SessionContext session) throws IOException, GeneralSecurityException {
			InetSocketAddress remoteAddress = SshdSocketAddress.toInetSocketAddress(session.getRemoteAddress());
			JGitEnvironmentProperties sshProperties = findEnvironmentProperties(sshKeysByHostname, remoteAddress);

			return KeyPairUtils.load(session, sshProperties.getPrivateKey(), sshProperties.getPassphrase());
		}

	}

	private final static class HttpProxyDataFactory implements ProxyDataFactory {

		private final Map<String, JGitEnvironmentProperties> sshKeysByHostname;

		private HttpProxyDataFactory(Map<String, JGitEnvironmentProperties> sshKeysByHostname) {
			this.sshKeysByHostname = sshKeysByHostname;
		}

		@Override
		public ProxyData get(InetSocketAddress remoteAddress) {
			JGitEnvironmentProperties sshProperties = findEnvironmentProperties(sshKeysByHostname, remoteAddress);

			ProxyHostProperties proxyHostProperties = sshProperties.getProxy()
					.get(ProxyHostProperties.ProxyForScheme.HTTP);

			if (proxyHostProperties == null || !proxyHostProperties.connectionInformationProvided()) {
				return null;
			}

			Proxy proxy = new Proxy(Proxy.Type.HTTP,
					new InetSocketAddress(proxyHostProperties.getHost(), proxyHostProperties.getPort()));

			char[] proxyPassword = proxyHostProperties.getPassword() != null
					? proxyHostProperties.getPassword().toCharArray() : null;
			return new ProxyData(proxy, proxyHostProperties.getUsername(), proxyPassword);
		}

	}

}
