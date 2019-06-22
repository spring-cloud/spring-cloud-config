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

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.cloud.config.server.encryption.CipherResourceJsonEncryptor;
import org.springframework.cloud.config.server.encryption.CipherResourcePropertiesEncryptor;
import org.springframework.cloud.config.server.encryption.CipherResourceYamlEncryptor;
import org.springframework.cloud.config.server.encryption.ResourceEncryptor;
import org.springframework.cloud.config.server.encryption.TextEncryptorLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds configuration to decrypt plain text files served through
 * {@link org.springframework.cloud.config.server.resource.ResourceController}. Each
 * supported extension is added as a key with its associated @{link
 * org.springframework.cloud.config.server.encryption.ResourceEncryptor} implementation as
 * a value.
 *
 * @author Sean Stiglitz
 */
@Configuration
@ConditionalOnExpression("${spring.cloud.config.server.encrypt.enabled:true} && ${spring.cloud.config.server.encrypt.plainTextEncrypt:false}")
public class ResourceEncryptorConfiguration {

	@Autowired
	private TextEncryptorLocator encryptor;

	@Bean
	Map<String, ResourceEncryptor> resourceEncryptors() {
		Map<String, ResourceEncryptor> resourceEncryptorMap = new HashMap<>();
		addSupportedExtensionsToMap(resourceEncryptorMap,
				new CipherResourceJsonEncryptor(encryptor));
		addSupportedExtensionsToMap(resourceEncryptorMap,
				new CipherResourcePropertiesEncryptor(encryptor));
		addSupportedExtensionsToMap(resourceEncryptorMap,
				new CipherResourceYamlEncryptor(encryptor));
		return resourceEncryptorMap;
	}

	private void addSupportedExtensionsToMap(
			Map<String, ResourceEncryptor> resourceEncryptorMap,
			ResourceEncryptor resourceEncryptor) {
		for (String ext : resourceEncryptor.getSupportedExtensions()) {
			resourceEncryptorMap.put(ext, resourceEncryptor);
		}
	}

}
