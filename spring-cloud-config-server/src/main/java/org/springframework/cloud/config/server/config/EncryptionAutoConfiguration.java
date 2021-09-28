/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.cloud.config.server.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.bootstrap.encrypt.KeyProperties;
import org.springframework.cloud.bootstrap.encrypt.KeyProperties.KeyStore;
import org.springframework.cloud.bootstrap.encrypt.RsaProperties;
import org.springframework.cloud.config.server.encryption.CipherEnvironmentEncryptor;
import org.springframework.cloud.config.server.encryption.EnvironmentEncryptor;
import org.springframework.cloud.config.server.encryption.KeyStoreTextEncryptorLocator;
import org.springframework.cloud.config.server.encryption.LocatorTextEncryptor;
import org.springframework.cloud.config.server.encryption.SingleTextEncryptorLocator;
import org.springframework.cloud.config.server.encryption.TextEncryptorLocator;
import org.springframework.cloud.context.encrypt.EncryptorFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.rsa.crypto.KeyStoreKeyFactory;
import org.springframework.security.rsa.crypto.RsaAlgorithm;
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
 * @author Olga Maciaszek-Sharma
 *
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties
@Import(SingleTextEncryptorConfiguration.class)
public class EncryptionAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public KeyProperties keyProperties() {
		return new KeyProperties();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(value = "spring.cloud.config.server.encrypt.enabled", matchIfMissing = true)
	@ConditionalOnBean(TextEncryptorLocator.class)
	public EnvironmentEncryptor environmentEncryptor(@Autowired(required = false) TextEncryptorLocator locator,
			TextEncryptor encryptor) {
		if (locator == null) {
			locator = new SingleTextEncryptorLocator(encryptor);
		}
		return new CipherEnvironmentEncryptor(locator);
	}

	@Bean
	@ConditionalOnMissingBean(TextEncryptor.class)
	public TextEncryptor defaultTextEncryptor(@Autowired(required = false) TextEncryptorLocator locator,
			KeyProperties key) {
		if (locator != null) {
			return new LocatorTextEncryptor(locator);
		}
		if (StringUtils.hasText(key.getKey())) {
			return new EncryptorFactory(key.getSalt()).create(key.getKey());
		}
		return Encryptors.noOpText();
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(RsaSecretEncryptor.class)
	@ConditionalOnProperty(prefix = "encrypt.key-store", value = "location", matchIfMissing = false)
	protected static class KeyStoreConfiguration {

		@Autowired
		private KeyProperties key;

		@Autowired
		private RsaProperties rsaProperties;

		@Bean
		@ConditionalOnMissingBean
		public TextEncryptorLocator textEncryptorLocator() {
			KeyStore keyStore = key.getKeyStore();
			KeyStoreTextEncryptorLocator locator = new KeyStoreTextEncryptorLocator(
					new KeyStoreKeyFactory(keyStore.getLocation(), keyStore.getPassword().toCharArray(),
							key.getKeyStore().getType()),
					keyStore.getSecret(), keyStore.getAlias());
			RsaAlgorithm algorithm = this.rsaProperties.getAlgorithm();
			locator.setRsaAlgorithm(algorithm);
			locator.setSalt(this.rsaProperties.getSalt());
			locator.setStrong(this.rsaProperties.isStrong());
			return locator;
		}

	}

}

@ConditionalOnBean(TextEncryptor.class)
@ConditionalOnMissingBean(TextEncryptorLocator.class)
@Configuration(proxyBeanMethods = false)
class SingleTextEncryptorConfiguration {

	@Bean
	public SingleTextEncryptorLocator textEncryptorLocator(TextEncryptor encryptor) {
		return new SingleTextEncryptorLocator(encryptor);
	}

}
