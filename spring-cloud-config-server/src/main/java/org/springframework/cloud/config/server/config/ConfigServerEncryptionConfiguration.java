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
import org.springframework.cloud.config.server.encryption.EncryptionController;
import org.springframework.cloud.config.server.encryption.TextEncryptorLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Bartosz Wojtkiewicz
 * @author Rafal Zukowski
 *
 */
@Configuration
public class ConfigServerEncryptionConfiguration {

	@Autowired(required = false)
	private TextEncryptorLocator encryptor;

	@Autowired
	private ConfigServerProperties properties;

	@Bean
	public EncryptionController encryptionController() {
		EncryptionController controller = new EncryptionController(this.encryptor);
		controller.setDefaultApplicationName(this.properties.getDefaultApplicationName());
		controller.setDefaultProfile(this.properties.getDefaultProfile());
		return controller;
	}

}
