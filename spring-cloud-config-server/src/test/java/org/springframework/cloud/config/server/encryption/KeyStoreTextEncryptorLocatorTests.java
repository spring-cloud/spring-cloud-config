/*
 * Copyright 2015-2019 the original author or authors.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.rsa.crypto.KeyStoreKeyFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class KeyStoreTextEncryptorLocatorTests {

	private KeyStoreTextEncryptorLocator locator = new KeyStoreTextEncryptorLocator(
			new KeyStoreKeyFactory(new ClassPathResource("server.jks"),
					"letmein".toCharArray()),
			"changeme", "mytestkey");

	@Test
	public void testDefaults() {
		TextEncryptor encryptor = this.locator
				.locate(Collections.<String, String>emptyMap());
		assertThat(encryptor.decrypt(encryptor.encrypt("foo"))).isEqualTo("foo");
	}

	@Test
	public void testDifferentKeyDefaultSecret() {
		this.locator.setSecretLocator(new SecretLocator() {

			@Override
			public char[] locate(String secret) {
				assertThat(secret).isEqualTo("changeme");
				// The actual secret for "mykey" is the same as the keystore password
				return "letmein".toCharArray();
			}
		});
		TextEncryptor encryptor = this.locator
				.locate(Collections.<String, String>singletonMap("key", "mykey"));
		assertThat(encryptor.decrypt(encryptor.encrypt("foo"))).isEqualTo("foo");
	}

	@Test
	public void testDifferentKeyAndSecret() {
		Map<String, String> map = new HashMap<String, String>();
		map.put("key", "mytestkey");
		map.put("secret", "changeme");
		TextEncryptor encryptor = this.locator.locate(map);
		assertThat(encryptor.decrypt(encryptor.encrypt("foo"))).isEqualTo("foo");
	}

}
