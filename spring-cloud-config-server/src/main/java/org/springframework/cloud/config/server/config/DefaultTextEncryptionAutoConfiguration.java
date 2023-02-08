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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.bootstrap.encrypt.KeyProperties;
import org.springframework.cloud.config.server.encryption.LocatorTextEncryptor;
import org.springframework.cloud.config.server.encryption.TextEncryptorLocator;
import org.springframework.cloud.context.encrypt.EncryptorFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.util.StringUtils;

/**
 * Default text encryption auto-configuration.
 *
 * @author Olga Maciaszek-Sharma
 * @since 4.0.0
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(RsaEncryptionAutoConfiguration.class)
@EnableConfigurationProperties
public class DefaultTextEncryptionAutoConfiguration {

	@Autowired
	ApplicationContext context;

	@Bean
	@ConditionalOnMissingBean
	public KeyProperties keyProperties() {
		return new KeyProperties();
	}

	@Bean
	@ConditionalOnMissingBean(TextEncryptor.class)
	@ConditionalOnBean(TextEncryptorLocator.class)
	public TextEncryptor defaultLocatorBasedTextEncryptor(TextEncryptorLocator locator) {
		return new LocatorTextEncryptor(locator);
	}

	@Bean
	@ConditionalOnMissingBean(TextEncryptor.class)
	public TextEncryptor defaultTextEncryptor(KeyProperties key) {
		if (StringUtils.hasText(key.getKey())) {
			return new EncryptorFactory(key.getSalt()).create(key.getKey());
		}
		return Encryptors.noOpText();
	}

}
