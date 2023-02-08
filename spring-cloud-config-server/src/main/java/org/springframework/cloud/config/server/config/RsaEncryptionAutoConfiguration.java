/*
 * Copyright 2002-2022 the original author or authors.
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

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.bootstrap.encrypt.KeyProperties;
import org.springframework.cloud.bootstrap.encrypt.RsaProperties;
import org.springframework.cloud.config.server.encryption.KeyStoreTextEncryptorLocator;
import org.springframework.cloud.config.server.encryption.TextEncryptorLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.rsa.crypto.KeyStoreKeyFactory;
import org.springframework.security.rsa.crypto.RsaAlgorithm;
import org.springframework.security.rsa.crypto.RsaSecretEncryptor;

/**
 * Autoconfiguration for RSA encryption.
 *
 * @author Olga Maciaszek-Sharma
 * @since 4.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "encrypt.key-store", value = "location")
@ConditionalOnClass(RsaSecretEncryptor.class)
@EnableConfigurationProperties
public class RsaEncryptionAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public KeyProperties keyProperties() {
		return new KeyProperties();
	}

	@Bean
	@ConditionalOnMissingBean
	public TextEncryptorLocator textEncryptorLocator(KeyProperties key, RsaProperties rsaProperties) {
		KeyProperties.KeyStore keyStore = key.getKeyStore();
		KeyStoreTextEncryptorLocator locator = new KeyStoreTextEncryptorLocator(
				new KeyStoreKeyFactory(keyStore.getLocation(), keyStore.getPassword().toCharArray(),
						key.getKeyStore().getType()),
				keyStore.getSecret(), keyStore.getAlias());
		RsaAlgorithm algorithm = rsaProperties.getAlgorithm();
		locator.setRsaAlgorithm(algorithm);
		locator.setSalt(rsaProperties.getSalt());
		locator.setStrong(rsaProperties.isStrong());
		return locator;
	}

}
