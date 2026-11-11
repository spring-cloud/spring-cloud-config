/*
 * Copyright 2013-present the original author or authors.
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
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.context.encrypt.EncryptorFactory;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.util.ResourceUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Asish Kumar
 */
public class CipherResourceXmlEncryptorTests {

	private static final String CIPHER = "{cipher}d1b2458ccede07c856ff952bd841638ff4dd12ed1d36812663c3c7262d57bf46";

	private final TextEncryptor textEncryptor = new EncryptorFactory("deadbeef").create("foo");

	private final CipherResourceXmlEncryptor encryptor = new CipherResourceXmlEncryptor(new TextEncryptorLocator() {
		@Override
		public TextEncryptor locate(Map<String, String> keys) {
			return CipherResourceXmlEncryptorTests.this.textEncryptor;
		}
	});

	private final Environment environment = new Environment("name", "profile", "label");

	@Test
	public void supportsXmlExtension() {
		assertThat(this.encryptor.getSupportedExtensions()).containsExactly("xml");
	}

	@Test
	public void whenDecryptResource_thenAllEncryptedValuesDecrypted() throws Exception {
		File file = ResourceUtils.getFile("classpath:resource-encryptor/test.xml");
		String text = new String(Files.readAllBytes(file.toPath()));

		String decrypted = this.encryptor.decrypt(text, this.environment);

		assertThat(decrypted).doesNotContain("{cipher}");
		assertThat(decrypted).contains("<plain>value</plain>");
		assertThat(decrypted).contains("<foo>" + this.textEncryptor.decrypt(CIPHER.replace("{cipher}", "")) + "</foo>");
		assertThat(decrypted)
			.contains("name=\"" + this.textEncryptor.decrypt(CIPHER.replace("{cipher}", "")) + "\">a</item>");
	}

	@Test
	public void decryptedValueIsEscaped() throws Exception {
		String encrypted = "{cipher}" + this.textEncryptor.encrypt("a<b&\"c\"");

		String decrypted = this.encryptor.decrypt("<a v=\"" + encrypted + "\">" + encrypted + "</a>",
				this.environment);

		assertThat(decrypted).isEqualTo("<a v=\"a&lt;b&amp;&quot;c&quot;\">a&lt;b&amp;&quot;c&quot;</a>");
	}

	@Test
	public void externalEntitiesAreNotResolved() {
		String text = "<!DOCTYPE a [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><a>&x;</a>";

		assertThatThrownBy(() -> this.encryptor.decrypt(text, this.environment)).isInstanceOf(IOException.class);
	}

	@Test
	public void malformedXmlFails() {
		assertThatThrownBy(() -> this.encryptor.decrypt("<a>" + CIPHER, this.environment))
			.isInstanceOf(IOException.class);
	}

}
