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

import java.util.Map;

import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.rsa.crypto.KeyStoreKeyFactory;
import org.springframework.security.rsa.crypto.RsaAlgorithm;
import org.springframework.security.rsa.crypto.RsaSecretEncryptor;

/**
 * A {@link TextEncryptorLocator} that pulls RSA key pairs out of a keystore. The input
 * map can contain entries for "key" or "secret" or both, or neither. The secret in the
 * input map is not, in general, the secret in the keystore, but is dereferenced through a
 * {@link SecretLocator} (so for example you can keep a table of encrypted secrets and
 * update it separately to the keystore).
 *
 * @author Dave Syer
 *
 */
public class KeyStoreTextEncryptorLocator implements TextEncryptorLocator {

	private final static String KEY = "key";

	private final static String SECRET = "secret";

	private KeyStoreKeyFactory keys;

	private String defaultSecret;

	private String defaultAlias;

	private SecretLocator secretLocator = new PassthruSecretLocator();

	private RsaAlgorithm rsaAlgorithm = RsaAlgorithm.DEFAULT;

	private boolean strong = false;

	private String salt = "deadbeef";

	public KeyStoreTextEncryptorLocator(KeyStoreKeyFactory keys, String defaultSecret,
			String defaultAlias) {
		this.keys = keys;
		this.defaultAlias = defaultAlias;
		this.defaultSecret = defaultSecret;
	}

	/**
	 * @param secretLocator the secretLocator to set
	 */
	public void setSecretLocator(SecretLocator secretLocator) {
		this.secretLocator = secretLocator;
	}

	public void setRsaAlgorithm(RsaAlgorithm rsaAlgorithm) {
		this.rsaAlgorithm = rsaAlgorithm;
	}

	public void setStrong(boolean strong) {
		this.strong = strong;
	}

	public void setSalt(String salt) {
		this.salt = salt;
	}

	@Override
	public TextEncryptor locate(Map<String, String> keys) {
		String alias = keys.containsKey(KEY) ? keys.get(KEY) : this.defaultAlias;
		String secret = keys.containsKey(SECRET) ? keys.get(SECRET) : this.defaultSecret;
		return new RsaSecretEncryptor(
				this.keys.getKeyPair(alias, this.secretLocator.locate(secret)),
				this.rsaAlgorithm, this.salt, this.strong);
	}

}
