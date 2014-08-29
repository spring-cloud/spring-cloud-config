/*
 * Copyright 2013-2014 the original author or authors.
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
package org.springframework.platform.config.server;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.security.rsa.crypto.KeyStoreKeyFactory;
import org.springframework.security.rsa.crypto.RsaSecretEncryptor;

/**
 * @author Dave Syer
 *
 */
@Configuration
@ComponentScan
public class ConfigServerConfiguration {

	@Configuration
	@ConfigurationProperties("encrypt")
	protected static class KeyConfiguration {
		@Autowired
		private EncryptionController controller;

		private String key;

		private KeyStore keyStore = new KeyStore();

		public String getKey() {
			return key;
		}

		public void setKey(String key) {
			this.key = key;
		}

		public KeyStore getKeyStore() {
			return keyStore;
		}

		public void setKeyStore(KeyStore keyStore) {
			this.keyStore = keyStore;
		}

		@PostConstruct
		public void init() {
			if (keyStore.getLocation() != null) {
				controller.setEncryptor(new RsaSecretEncryptor(new KeyStoreKeyFactory(
						keyStore.getLocation(), keyStore.getPassword().toCharArray())
						.getKeyPair(keyStore.getAlias())));
			}
			if (key != null) {
				controller.uploadKey(key);
			}
		}

		public static class KeyStore {

			private Resource location;
			private String password;
			private String alias;

			public String getAlias() {
				return alias;
			}

			public void setAlias(String alias) {
				this.alias = alias;
			}

			public Resource getLocation() {
				return location;
			}

			public void setLocation(Resource location) {
				this.location = location;
			}

			public String getPassword() {
				return password;
			}

			public void setPassword(String password) {
				this.password = password;
			}

		}
	}

	@Configuration
	@Profile("native")
	protected static class NativeRepositoryConfiguration {
		@Autowired
		private ConfigurableEnvironment environment;

		@Bean
		public NativeEnvironmentRepository repository() {
			return new NativeEnvironmentRepository(environment);
		}
	}

	@Configuration
	@Profile("!native")
	protected static class GitRepositoryConfiguration {
		@Autowired
		private ConfigurableEnvironment environment;

		@Bean
		@ConfigurationProperties("spring.platform.config.server")
		public JGitEnvironmentRepository repository() {
			return new JGitEnvironmentRepository(environment);
		}
	}
}