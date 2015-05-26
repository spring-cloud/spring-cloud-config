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

package org.springframework.cloud.config.server.encryption;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.config.server.ConfigServerProperties;
import org.springframework.cloud.config.server.EncryptionController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * @author Bartosz Wojtkiewicz
 * @author Rafal Zukowski
 *
 */
@Configuration
public class ConfigServerEncryptionConfiguration {

	@Autowired(required = false)
	private TextEncryptor encryptor;

	@Autowired
	private TextEncryptorLocator locator;

	@Autowired
	private ConfigServerProperties properties;

	@Bean
	public EncryptionController encryptionController() {
		return new EncryptionController(locator, properties);
	}

	@Bean
	public EnvironmentEncryptor environmentEncryptor() {
		return new CipherEnvironmentEncryptor(locator);
	}

	@Bean
	@ConditionalOnMissingBean
	public TextEncryptorLocator textEncryptorLocator() {
		return new SingleTextEncryptorLocator(encryptor);
	}
}
