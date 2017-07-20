/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.cloud.config.server.config;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.config.server.ssh.PropertyBasedSshSessionFactory;
import org.springframework.cloud.config.server.ssh.SshUriProperties;
import org.springframework.cloud.config.server.ssh.SshUriPropertyProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configure a callback to set up a property based SSH settings before running a transport command (such as clone or fetch)
 *
 * @author Ollie Hughes
 */
@Configuration
@EnableConfigurationProperties(SshUriProperties.class)
public class TransportConfiguration {

	@ConditionalOnMissingBean(TransportConfigCallback.class)
	@Bean
	public TransportConfigCallback propertiesBasedSshTransportCallback(final SshUriProperties sshUriProperties) {
		if(sshUriProperties.isIgnoreLocalSshSettings()) {
			return new PropertiesBasedSshTransportConfigCallback(sshUriProperties);
		}
		else return new FileBasedSshTransportConfigCallback(sshUriProperties);
	}

	/**
	 * Configure JGit transport command to use a SSH session factory that is configured using properties defined
	 * in {@link SshUriProperties}
	 */
	public static class PropertiesBasedSshTransportConfigCallback implements TransportConfigCallback {

		private SshUriProperties sshUriProperties;

		public PropertiesBasedSshTransportConfigCallback(SshUriProperties sshUriProperties) {
			this.sshUriProperties = sshUriProperties;
		}

		public SshUriProperties getSshUriProperties() {
			return sshUriProperties;
		}

		@Override
		public void configure(Transport transport) {
			if (transport instanceof SshTransport) {
				SshTransport sshTransport = (SshTransport) transport;
				sshTransport.setSshSessionFactory(
						new PropertyBasedSshSessionFactory(
								new SshUriPropertyProcessor(sshUriProperties).getSshKeysByHostname(), new JSch()));
			}
		}
	}

	/**
	 * Configure JGit transport command to use a default SSH session factory based on local machines SSH config.
	 * Allow strict host key checking to be set.
	 */
	public static class FileBasedSshTransportConfigCallback implements TransportConfigCallback {

		private SshUriProperties sshUriProperties;

		public FileBasedSshTransportConfigCallback(SshUriProperties sshUriProperties) {
			this.sshUriProperties = sshUriProperties;
		}

		public SshUriProperties getSshUriProperties() {
			return sshUriProperties;
		}

		@Override
		public void configure(Transport transport) {
			SshSessionFactory.setInstance(new JschConfigSessionFactory() {
				@Override
				protected void configure(OpenSshConfig.Host hc, Session session) {
					session.setConfig("StrictHostKeyChecking",
							sshUriProperties.isStrictHostKeyChecking() ? "yes" : "no");
				}
			});
		}
	}
}
