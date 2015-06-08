/*
 * Copyright 2013-2014 the original author or authors.
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
package org.springframework.cloud.config.server;

import org.junit.Test;
import org.springframework.cloud.config.server.encryption.SingleTextEncryptorLocator;
import org.springframework.cloud.context.encrypt.KeyFormatException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.rsa.crypto.RsaSecretEncryptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Dave Syer
 *
 */
public class EncryptionControllerTests {

	private SingleTextEncryptorLocator textEncryptorLocator = new SingleTextEncryptorLocator();
	private ConfigServerProperties properties = new ConfigServerProperties();
	private EncryptionController controller = new EncryptionController(textEncryptorLocator, properties);

	@Test(expected = KeyNotInstalledException.class)
	public void cannotDecryptWithoutKey() {
		controller.decrypt("foo", MediaType.TEXT_PLAIN);
	}

	@Test(expected = KeyNotInstalledException.class)
	public void cannotDecryptWithNoopEncryptor() {
		textEncryptorLocator.setEncryptor(Encryptors.noOpText());
		controller.decrypt("foo", MediaType.TEXT_PLAIN);
	}

	@Test(expected = KeyFormatException.class)
	public void cannotUploadPublicKey() {
		controller.uploadKey("ssh-rsa ...", MediaType.TEXT_PLAIN);
	}

	@Test(expected = KeyFormatException.class)
	public void cannotUploadPublicKeyPemFormat() {
		controller.uploadKey("---- BEGIN RSA PUBLIC KEY ...", MediaType.TEXT_PLAIN);
	}

	@Test(expected = InvalidCipherException.class)
	public void invalidCipher() {
		controller.uploadKey("foo", MediaType.TEXT_PLAIN);
		controller.decrypt("foo", MediaType.TEXT_PLAIN);
	}

	@Test
	public void sunnyDaySymmetricKey() {
		controller.uploadKey("foo", MediaType.TEXT_PLAIN);
		String cipher = controller.encrypt("foo", MediaType.TEXT_PLAIN);
		assertEquals("foo", controller.decrypt(cipher, MediaType.TEXT_PLAIN));
	}

	@Test
	public void sunnyDayRsaKey() {
		textEncryptorLocator.setEncryptor(new RsaSecretEncryptor());
		String cipher = controller.encrypt("foo", MediaType.TEXT_PLAIN);
		assertEquals("foo", controller.decrypt(cipher, MediaType.TEXT_PLAIN));
	}

	@Test
	public void publicKey() {
		textEncryptorLocator.setEncryptor(new RsaSecretEncryptor());
		String key = controller.getPublicKey();
		assertTrue("Wrong key format: " + key, key.startsWith("ssh-rsa"));
	}

	@Test
	public void formDataIn() {
		textEncryptorLocator.setEncryptor(new RsaSecretEncryptor());
		// Add space to input
		String cipher = controller.encrypt("foo bar=", MediaType.APPLICATION_FORM_URLENCODED);
		String decrypt = controller.decrypt(cipher + "=", MediaType.APPLICATION_FORM_URLENCODED);
		assertEquals("Wrong decrypted plaintext: " + decrypt, "foo bar", decrypt);
	}

	@Test
	public void randomizedCipher() {
		controller.uploadKey("foo", MediaType.TEXT_PLAIN);
		String cipher = controller.encrypt("foo", MediaType.TEXT_PLAIN);
		assertNotEquals(cipher, controller.encrypt("foo", MediaType.TEXT_PLAIN));
	}

}
