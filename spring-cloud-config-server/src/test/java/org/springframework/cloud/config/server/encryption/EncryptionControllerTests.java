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

import java.util.Map;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.http.MediaType;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.rsa.crypto.RsaSecretEncryptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Dave Syer
 *
 */
public class EncryptionControllerTests {

	private EncryptionController controller = new EncryptionController(
			new SingleTextEncryptorLocator(Encryptors.noOpText()));

	@Test(expected = EncryptionTooWeakException.class)
	public void cannotDecryptWithoutKey() {
		this.controller.decrypt("foo", MediaType.TEXT_PLAIN);
	}

	@Test(expected = EncryptionTooWeakException.class)
	public void cannotDecryptWithNoopEncryptor() {
		this.controller.decrypt("foo", MediaType.TEXT_PLAIN);
	}

	@Test(expected = InvalidCipherException.class)
	public void shouldThrowExceptionOnDecryptInvalidData() {
		this.controller = new EncryptionController(
				new SingleTextEncryptorLocator(new RsaSecretEncryptor()));
		this.controller.decrypt("foo", MediaType.TEXT_PLAIN);
	}

	@Test(expected = InvalidCipherException.class)
	public void shouldThrowExceptionOnDecryptWrongKey() {
		RsaSecretEncryptor encryptor = new RsaSecretEncryptor();
		this.controller = new EncryptionController(
				new SingleTextEncryptorLocator(new RsaSecretEncryptor()));
		this.controller.decrypt(encryptor.encrypt("foo"), MediaType.TEXT_PLAIN);
	}

	@Test
	public void sunnyDayRsaKey() {
		this.controller = new EncryptionController(
				new SingleTextEncryptorLocator(new RsaSecretEncryptor()));
		String cipher = this.controller.encrypt("foo", MediaType.TEXT_PLAIN);
		assertThat(this.controller.decrypt(cipher, MediaType.TEXT_PLAIN))
				.isEqualTo("foo");
	}

	@Test
	public void publicKey() {
		this.controller = new EncryptionController(
				new SingleTextEncryptorLocator(new RsaSecretEncryptor()));
		String key = this.controller.getPublicKey();
		assertThat(key.startsWith("ssh-rsa")).as("Wrong key format: " + key).isTrue();
	}

	@Test
	public void appAndProfile() {
		this.controller = new EncryptionController(
				new SingleTextEncryptorLocator(new RsaSecretEncryptor()));
		// Add space to input
		String cipher = this.controller.encrypt("app", "default", "foo bar",
				MediaType.TEXT_PLAIN);
		String decrypt = this.controller.decrypt("app", "default", cipher,
				MediaType.TEXT_PLAIN);
		assertThat(decrypt).as("Wrong decrypted plaintext: " + decrypt)
				.isEqualTo("foo bar");
	}

	@Test
	public void formDataIn() {
		this.controller = new EncryptionController(
				new SingleTextEncryptorLocator(new RsaSecretEncryptor()));
		// Add space to input
		String cipher = this.controller.encrypt("foo bar=",
				MediaType.APPLICATION_FORM_URLENCODED);
		String decrypt = this.controller.decrypt(cipher + "=",
				MediaType.APPLICATION_FORM_URLENCODED);
		assertThat(decrypt).as("Wrong decrypted plaintext: " + decrypt)
				.isEqualTo("foo bar");
	}

	@Test
	public void formDataInWithPrefix() {
		this.controller = new EncryptionController(
				new SingleTextEncryptorLocator(new RsaSecretEncryptor()));
		// Add space to input
		String cipher = this.controller.encrypt("{key:test}foo bar=",
				MediaType.APPLICATION_FORM_URLENCODED);
		String decrypt = this.controller.decrypt(cipher + "=",
				MediaType.APPLICATION_FORM_URLENCODED);
		assertThat(decrypt).as("Wrong decrypted plaintext: " + decrypt)
				.isEqualTo("foo bar");
	}

	@Test
	public void prefixStrippedBeforeEncrypt() {
		TextEncryptor encryptor = mock(TextEncryptor.class);
		when(encryptor.encrypt(anyString())).thenReturn("myEncryptedValue");

		this.controller = new EncryptionController(
				new SingleTextEncryptorLocator(encryptor));
		this.controller.encrypt("{key:test}foo", MediaType.TEXT_PLAIN);

		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(encryptor, atLeastOnce()).encrypt(captor.capture());
		assertThat(captor.getValue()).doesNotContain("{key:test}")
				.as("Prefix must be stripped prior to encrypt");
	}

	@Test
	public void encryptDecyptTextWithCurlyBrace() {
		this.controller = new EncryptionController(
				new SingleTextEncryptorLocator(new RsaSecretEncryptor()));

		String plain = "textwith}brace";

		String cipher = this.controller.encrypt(plain,
				MediaType.APPLICATION_FORM_URLENCODED);
		String decrypt = this.controller.decrypt(cipher,
				MediaType.APPLICATION_FORM_URLENCODED);
		assertThat(decrypt).isEqualTo(plain);
	}

	@Test
	public void addEnvironment() {
		TextEncryptorLocator locator = new TextEncryptorLocator() {

			private RsaSecretEncryptor encryptor = new RsaSecretEncryptor();

			@Override
			public TextEncryptor locate(Map<String, String> keys) {
				return this.encryptor;
			}
		};
		this.controller = new EncryptionController(locator);
		// Add space to input
		String cipher = this.controller.encrypt("app", "default", "foo bar",
				MediaType.TEXT_PLAIN);
		assertThat(cipher.contains("{name:app}")).as("Wrong cipher: " + cipher).isFalse();
		String decrypt = this.controller.decrypt("app", "default", cipher,
				MediaType.TEXT_PLAIN);
		assertThat(decrypt).as("Wrong decrypted plaintext: " + decrypt)
				.isEqualTo("foo bar");
	}

}
