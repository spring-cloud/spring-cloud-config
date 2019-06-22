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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.util.StringUtils;

/**
 * Abstract base class for any @{link
 * org.springframework.cloud.config.server.encryption.ResourceEncryptor} implementations.
 * Meant to house shared configuration and logic.
 *
 * @author Sean Stiglitz
 */
abstract class AbstractCipherResourceEncryptor implements ResourceEncryptor {

	protected final String CIPHER_MARKER = "{cipher}";

	private final TextEncryptorLocator encryptor;

	private EnvironmentPrefixHelper helper = new EnvironmentPrefixHelper();

	AbstractCipherResourceEncryptor(TextEncryptorLocator encryptor) {
		this.encryptor = encryptor;
	}

	@Override
	public abstract List<String> getSupportedExtensions();

	@Override
	public abstract String decrypt(String text, Environment environment)
			throws IOException;

	protected String decryptWithJacksonParser(String text, String name, String[] profiles,
			JsonFactory factory) throws IOException {
		Set<String> valsToDecrpyt = new HashSet<String>();
		JsonParser parser = factory.createParser(text);
		JsonToken token;

		while ((token = parser.nextToken()) != null) {
			if (token.equals(JsonToken.VALUE_STRING)
					&& parser.getValueAsString().startsWith(CIPHER_MARKER)) {
				valsToDecrpyt.add(parser.getValueAsString().trim());
			}
		}

		for (String value : valsToDecrpyt) {
			String decryptedValue = decryptValue(value.replace(CIPHER_MARKER, ""), name,
					profiles);
			text = text.replace(value, decryptedValue);
		}

		return text;
	}

	protected String decryptValue(String value, String name, String[] profiles) {
		return encryptor
				.locate(this.helper.getEncryptorKeys(name,
						StringUtils.arrayToCommaDelimitedString(profiles), value))
				.decrypt(this.helper.stripPrefix(value));
	}

}
