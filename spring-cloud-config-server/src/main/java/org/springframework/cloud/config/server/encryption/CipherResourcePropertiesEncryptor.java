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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.stereotype.Component;

/**
 * @{link org.springframework.cloud.config.server.encryption.ResourceEncryptor}
 * implementation that can decrypt property values prefixed with {cipher} marker in a
 * Properties file.
 * @author Sean Stiglitz
 */
@Component
public class CipherResourcePropertiesEncryptor extends AbstractCipherResourceEncryptor
		implements ResourceEncryptor {

	private static final List<String> SUPPORTED_EXTENSIONS = Arrays.asList("properties");

	public CipherResourcePropertiesEncryptor(TextEncryptorLocator encryptor) {
		super(encryptor);
	}

	@Override
	public List<String> getSupportedExtensions() {
		return SUPPORTED_EXTENSIONS;
	}

	@Override
	public String decrypt(String text, Environment environment) throws IOException {
		Set<String> valsToDecrpyt = new HashSet<String>();
		Properties properties = new Properties();
		StringBuffer sb = new StringBuffer();
		properties.load(new ByteArrayInputStream(text.getBytes()));

		for (Object value : properties.values()) {
			String valueStr = value.toString();
			if (valueStr.startsWith(CIPHER_MARKER)) {
				valsToDecrpyt.add(valueStr);
			}
		}

		for (String value : valsToDecrpyt) {
			String decryptedValue = decryptValue(value.replace(CIPHER_MARKER, ""),
					environment.getName(), environment.getProfiles());
			text = text.replace(value, decryptedValue);
		}

		return text;
	}

}
