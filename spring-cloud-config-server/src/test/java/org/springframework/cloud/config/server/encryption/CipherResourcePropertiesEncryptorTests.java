/*
 * Copyright 2013-2019 the original author or authors.
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

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

import org.junit.Test;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.context.encrypt.EncryptorFactory;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.util.ResourceUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Sean Stiglitz
 */
public class CipherResourcePropertiesEncryptorTests {

	private final String salt = "deadbeef";

	private final String key = "foo";

	private TextEncryptor textEncryptor = new EncryptorFactory(salt).create(key);

	private CipherResourcePropertiesEncryptor encryptor = new CipherResourcePropertiesEncryptor(
			new TextEncryptorLocator() {
				@Override
				public TextEncryptor locate(Map<String, String> keys) {
					return CipherResourcePropertiesEncryptorTests.this.textEncryptor;
				}
			});

	@Test
	public void whenDecryptResource_thenAllEncryptedValuesDecrypted() throws Exception {
		// given
		Environment environment = new Environment("name", "profile", "label");
		File file = ResourceUtils.getFile("classpath:resource-encryptor/test.properties");
		String text = new String(Files.readAllBytes(file.toPath()));

		// when
		String decyptedResource = encryptor.decrypt(text, environment);

		// then
		assertThat(decyptedResource.contains("{cipher}")).isFalse();
	}

}
