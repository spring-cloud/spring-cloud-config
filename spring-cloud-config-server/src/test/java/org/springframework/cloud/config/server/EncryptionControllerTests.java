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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;
import org.springframework.cloud.config.Environment;
import org.springframework.cloud.config.PropertySource;
import org.springframework.http.MediaType;
import org.springframework.security.rsa.crypto.RsaSecretEncryptor;

/**
 * @author Dave Syer
 *
 */
public class EncryptionControllerTests {

	private EncryptionController controller = new EncryptionController();

	@Test(expected = KeyNotInstalledException.class)
	public void cannotDecryptWithoutKey() {
		controller.decrypt("foo", MediaType.TEXT_PLAIN);
	}

	@Test(expected = KeyFormatException.class)
	public void cannotUploadPublicKey() {
		controller.uploadKey("ssh-rsa ...");
	}

	@Test(expected = KeyFormatException.class)
	public void cannotUploadPublicKeyPemFormat() {
		controller.uploadKey("---- BEGIN RSA PUBLIC KEY ...");
	}

	@Test(expected = InvalidCipherException.class)
	public void invalidCipher() {
		controller.uploadKey("foo");
		controller.decrypt("foo", MediaType.TEXT_PLAIN);
	}

	@Test
	public void sunnyDaySymmetricKey() {
		controller.uploadKey("foo");
		String cipher = controller.encrypt("foo", MediaType.TEXT_PLAIN);
		assertEquals("foo", controller.decrypt(cipher, MediaType.TEXT_PLAIN));
	}

	@Test
	public void sunnyDayRsaKey() {
		controller.setEncryptor(new RsaSecretEncryptor());
		String cipher = controller.encrypt("foo", MediaType.TEXT_PLAIN);
		assertEquals("foo", controller.decrypt(cipher, MediaType.TEXT_PLAIN));
	}

	@Test
	public void publicKey() {
		controller.setEncryptor(new RsaSecretEncryptor());
		String key = controller.getPublicKey();
		assertTrue("Wrong key format: " + key, key.startsWith("ssh-rsa"));
	}

	@Test
	public void decryptEnvironment() {
		controller.uploadKey("foo");
		String cipher = controller.encrypt("foo", MediaType.TEXT_PLAIN);
		Environment environment = new Environment("foo", "bar");
		environment.add(new PropertySource("spam", Collections
				.<Object, Object> singletonMap("my", "{cipher}" + cipher)));
		Environment result = controller.decrypt(environment);
		assertEquals("foo", result.getPropertySources().get(0).getSource().get("my"));
	}

	@Test
	public void randomizedCipher() {
		controller.uploadKey("foo");
		String cipher = controller.encrypt("foo", MediaType.TEXT_PLAIN);
		assertNotEquals(cipher, controller.encrypt("foo", MediaType.TEXT_PLAIN));
	}

}
