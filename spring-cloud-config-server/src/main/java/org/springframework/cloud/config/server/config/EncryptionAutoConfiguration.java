/*
 * Copyright 2002-2015 the original author or authors.
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.bootstrap.encrypt.KeyProperties;
import org.springframework.cloud.bootstrap.encrypt.KeyProperties.KeyStore;
import org.springframework.cloud.config.server.encryption.CipherEnvironmentEncryptor;
import org.springframework.cloud.config.server.encryption.EnvironmentEncryptor;
import org.springframework.cloud.config.server.encryption.KeyStoreTextEncryptorLocator;
import org.springframework.cloud.config.server.encryption.TextEncryptorLocator;
import org.springframework.cloud.context.encrypt.EncryptorFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.rsa.crypto.KeyStoreKeyFactory;
import org.springframework.security.rsa.crypto.RsaSecretEncryptor;
import org.springframework.util.StringUtils;

/**
 * Auto configuration for text encryptors and environment encryptors (non-web stuff).
 * Users can provide beans of the same type as any or all of the beans defined here in
 * application code to override the default behaviour.
 *
 * @author Bartosz Wojtkiewicz
 * @author Rafal Zukowski
 * @author Dave Syer
 *
 */
@Configuration
@EnableConfigurationProperties(KeyProperties.class)
public class EncryptionAutoConfiguration {

	@ConditionalOnMissingBean(TextEncryptor.class)
	protected static class DefaultTextEncryptorConfiguration {

		@Autowired
		private KeyProperties key;

		@Bean
		public TextEncryptor nullTextEncryptor() {
			if (StringUtils.hasText(this.key.getKey())) {
				return new EncryptorFactory().create(this.key.getKey());
			}
			return Encryptors.noOpText();
		}

	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(value = "spring.cloud.config.server.encrypt.enabled", matchIfMissing = true)
	public EnvironmentEncryptor environmentEncryptor(
			TextEncryptorLocator textEncryptorLocator) {
		return new CipherEnvironmentEncryptor(textEncryptorLocator);
	}

	@Configuration
	@ConditionalOnClass(RsaSecretEncryptor.class)
	@ConditionalOnProperty(value = "encrypt.keyStore.location", matchIfMissing = false)
	@EnableConfigurationProperties(KeyProperties.class)
	protected static class KeyStoreConfiguration {

		@Autowired
		private KeyProperties key;

		@Bean
		@ConditionalOnMissingBean
		public TextEncryptorLocator textEncryptorLocator() {
			KeyStore keyStore = this.key.getKeyStore();
			KeyStoreTextEncryptorLocator locator = new KeyStoreTextEncryptorLocator(new KeyStoreKeyFactory(
					keyStore.getLocation(), keyStore.getPassword().toCharArray()),
					keyStore.getSecret(), keyStore.getAlias());
			locator.setRsaAlgorithm(this.key.getRsa().getAlgorithm());
			locator.setSalt(this.key.getRsa().getSalt());
			locator.setStrong(this.key.getRsa().isStrong());
			return locator;
		}

	}

}
