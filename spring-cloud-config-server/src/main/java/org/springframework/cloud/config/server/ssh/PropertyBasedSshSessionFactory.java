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

import java.util.Map;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.util.Base64;
import org.eclipse.jgit.util.FS;

/**
 * In a cloud environment local SSH config files such as `.known_hosts` may not be suitable for providing
 * configuration settings due to ephemeral filesystems. This flag enables SSH config to be provided as application
 * properties
 * @author William Tran
 * @author Ollie Hughes
 */
public class PropertyBasedSshSessionFactory extends JschConfigSessionFactory {

	private static final String STRICT_HOST_KEY_CHECKING = "StrictHostKeyChecking";
	private static final String PREFERRED_AUTHENTICATIONS = "PreferredAuthentications";
	private static final String YES_OPTION = "yes";
	private static final String NO_OPTION = "no";
	private static final String SERVER_HOST_KEY = "server_host_key";
	private final Map<String, SshUri> sshKeysByHostname;
	private final JSch jSch;

	public PropertyBasedSshSessionFactory(Map<String, SshUri> sshKeysByHostname, JSch jSch) {
		this.sshKeysByHostname = sshKeysByHostname;
		this.jSch = jSch;
	}

	@Override
	protected void configure(Host hc, Session session) {
		SshUri sshProperties = sshKeysByHostname.get(hc.getHostName());
		String hostKeyAlgorithm = sshProperties.getHostKeyAlgorithm();
		if (hostKeyAlgorithm != null) {
			session.setConfig(SERVER_HOST_KEY, hostKeyAlgorithm);
		}
		if (sshProperties.getHostKey() == null || !sshProperties.isStrictHostKeyChecking()) {
			session.setConfig(STRICT_HOST_KEY_CHECKING, NO_OPTION);
		} else {
			session.setConfig(STRICT_HOST_KEY_CHECKING, YES_OPTION);
		}
		String preferredAuthentications = sshProperties.getPreferredAuthentications();
		if (preferredAuthentications != null) {
			session.setConfig(PREFERRED_AUTHENTICATIONS, preferredAuthentications);
		}
	}

	@Override
	protected Session createSession(Host hc, String user, String host, int port, FS fs) throws JSchException {
		if (sshKeysByHostname.containsKey(host)) {
			SshUri sshUriProperties = sshKeysByHostname.get(host);
			jSch.addIdentity(host, sshUriProperties.getPrivateKey().getBytes(), null, null);
			if (sshUriProperties.getKnownHostsFile() != null) {
				jSch.setKnownHosts(sshUriProperties.getKnownHostsFile());
			}
			if (sshUriProperties.getHostKey() != null) {
				HostKey hostkey = new HostKey(host, Base64.decode(sshUriProperties.getHostKey()));
				jSch.getHostKeyRepository().add(hostkey, null);
			}
			return jSch.getSession(user, host, port);
		}
		throw new JSchException("no keys configured for hostname " + host);
	}

}
