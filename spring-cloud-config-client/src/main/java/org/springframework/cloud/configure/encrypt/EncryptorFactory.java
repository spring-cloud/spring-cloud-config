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
package org.springframework.cloud.configure.encrypt;

import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.rsa.crypto.RsaSecretEncryptor;

/**
 * @author Dave Syer
 *
 */
public class EncryptorFactory {

	// TODO: expose as config property
	private static final String SALT = "deadbeef";

	public TextEncryptor create(String data) {

		TextEncryptor encryptor;
		if (data.contains("RSA PRIVATE KEY")) {

			try {
				encryptor = new RsaSecretEncryptor(data);
			}
			catch (IllegalArgumentException e) {
				throw new KeyFormatException();
			}

		}
		else if (data.startsWith("ssh-rsa") || data.contains("RSA PUBLIC KEY")) {
			throw new KeyFormatException();
		}
		else {
			encryptor = Encryptors.text(data, SALT);
		}

		return encryptor;
	}

}


