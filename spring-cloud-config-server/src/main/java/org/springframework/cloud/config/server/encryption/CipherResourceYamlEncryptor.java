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

package org.springframework.cloud.config.server.encryption;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.stereotype.Component;

/**
 * @{link org.springframework.cloud.config.server.encryption.ResourceEncryptor}
 * implementation that can decrypt property values prefixed with {cipher} marker in a YAML
 * file.
 * @author Sean Stiglitz
 */
@Component
public class CipherResourceYamlEncryptor extends AbstractCipherResourceEncryptor
		implements ResourceEncryptor {

	private static final List<String> SUPPORTED_EXTENSIONS = Arrays.asList("yml", "yaml");

	private final YAMLFactory factory;

	public CipherResourceYamlEncryptor(TextEncryptorLocator encryptor) {
		super(encryptor);
		this.factory = new YAMLFactory();
	}

	@Override
	public List<String> getSupportedExtensions() {
		return SUPPORTED_EXTENSIONS;
	}

	@Override
	public String decrypt(String text, Environment environment) throws IOException {
		return decryptWithJacksonParser(text, environment.getName(),
				environment.getProfiles(), factory);
	}

}
