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
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.config.server.encryption.CipherEnvironmentEncryptor;
import org.springframework.cloud.config.server.encryption.EnvironmentEncryptor;
import org.springframework.cloud.config.server.encryption.SingleTextEncryptorLocator;
import org.springframework.cloud.config.server.encryption.TextEncryptorLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * Autoconfiguration for text encryptors and environment encryptors (non-web stuff). Users
 * can provide beans of the same type as any or all of the beans defined here in
 * application code to override the default behaviour.
 *
 * @author Bartosz Wojtkiewicz
 * @author Rafal Zukowski
 * @author Dave Syer
 * @author Olga Maciaszek-Sharma
 *
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(DefaultTextEncryptionAutoConfiguration.class)
public class EncryptionAutoConfiguration {

	@Bean
	@ConditionalOnBean(TextEncryptor.class)
	@ConditionalOnMissingBean(TextEncryptorLocator.class)
	public SingleTextEncryptorLocator singleTextEncryptorLocator(TextEncryptor encryptor) {
		return new SingleTextEncryptorLocator(encryptor);
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

}
