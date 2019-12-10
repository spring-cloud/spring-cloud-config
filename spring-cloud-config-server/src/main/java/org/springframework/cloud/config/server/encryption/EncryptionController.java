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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.context.encrypt.KeyFormatException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.rsa.crypto.RsaKeyHolder;
import org.springframework.security.rsa.crypto.RsaSecretEncryptor;
import org.springframework.util.Base64Utils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Dave Syer
 * @author Tim Ysewyn
 *
 */
@RestController
@RequestMapping(path = "${spring.cloud.config.server.prefix:}")
public class EncryptionController {

	private static Log logger = LogFactory.getLog(EncryptionController.class);

	volatile private TextEncryptorLocator encryptorLocator;

	private EnvironmentPrefixHelper helper = new EnvironmentPrefixHelper();

	private String defaultApplicationName = "application";

	private String defaultProfile = "default";

	public EncryptionController(TextEncryptorLocator encryptorLocator) {
		this.encryptorLocator = encryptorLocator;
	}

	public void setDefaultApplicationName(String defaultApplicationName) {
		this.defaultApplicationName = defaultApplicationName;
	}

	public void setDefaultProfile(String defaultProfile) {
		this.defaultProfile = defaultProfile;
	}

	@RequestMapping(value = "/key", method = RequestMethod.GET)
	public String getPublicKey() {
		return getPublicKey(defaultApplicationName, defaultProfile);
	}

	@RequestMapping(value = "/key/{name}/{profiles}", method = RequestMethod.GET)
	public String getPublicKey(@PathVariable String name, @PathVariable String profiles) {
		TextEncryptor encryptor = getEncryptor(name, profiles, "");
		if (!(encryptor instanceof RsaKeyHolder)) {
			throw new KeyNotAvailableException();
		}
		return ((RsaKeyHolder) encryptor).getPublicKey();
	}

	@RequestMapping(value = "encrypt/status", method = RequestMethod.GET)
	public Map<String, Object> status() {
		TextEncryptor encryptor = getEncryptor(defaultApplicationName, defaultProfile,
				"");
		validateEncryptionWeakness(encryptor);
		return Collections.singletonMap("status", "OK");
	}

	@RequestMapping(value = "encrypt", method = RequestMethod.POST)
	public String encrypt(@RequestBody String data,
			@RequestHeader("Content-Type") MediaType type) {
		return encrypt(defaultApplicationName, defaultProfile, data, type);
	}

	@RequestMapping(value = "/encrypt/{name}/{profiles}", method = RequestMethod.POST)
	public String encrypt(@PathVariable String name, @PathVariable String profiles,
			@RequestBody String data, @RequestHeader("Content-Type") MediaType type) {
		TextEncryptor encryptor = getEncryptor(name, profiles, "");
		validateEncryptionWeakness(encryptor);
		String input = stripFormData(data, type, false);
		Map<String, String> keys = helper.getEncryptorKeys(name, profiles, input);
		String textToEncrypt = helper.stripPrefix(input);
		String encrypted = helper.addPrefix(keys,
				encryptorLocator.locate(keys).encrypt(textToEncrypt));
		logger.info("Encrypted data");
		return encrypted;
	}

	@RequestMapping(value = "decrypt", method = RequestMethod.POST)
	public String decrypt(@RequestBody String data,
			@RequestHeader("Content-Type") MediaType type) {
		return decrypt(defaultApplicationName, defaultProfile, data, type);
	}

	@RequestMapping(value = "/decrypt/{name}/{profiles}", method = RequestMethod.POST)
	public String decrypt(@PathVariable String name, @PathVariable String profiles,
			@RequestBody String data, @RequestHeader("Content-Type") MediaType type) {
		TextEncryptor encryptor = getEncryptor(name, profiles, "");
		checkDecryptionPossible(encryptor);
		validateEncryptionWeakness(encryptor);
		try {
			encryptor = getEncryptor(name, profiles, data);
			String input = stripFormData(helper.stripPrefix(data), type, true);
			String decrypted = encryptor.decrypt(input);
			logger.info("Decrypted cipher data");
			return decrypted;
		}
		catch (IllegalArgumentException | IllegalStateException e) {
			logger.error("Cannot decrypt key:" + name + ", value:" + data, e);
			throw new InvalidCipherException();
		}
	}

	private TextEncryptor getEncryptor(String name, String profiles, String data) {
		if (encryptorLocator == null) {
			throw new KeyNotInstalledException();
		}
		TextEncryptor encryptor = encryptorLocator
				.locate(helper.getEncryptorKeys(name, profiles, data));
		if (encryptor == null) {
			throw new KeyNotInstalledException();
		}
		return encryptor;
	}

	private void validateEncryptionWeakness(TextEncryptor textEncryptor) {
		if (textEncryptor.encrypt("FOO").equals("FOO")) {
			throw new EncryptionTooWeakException();
		}
	}

	private void checkDecryptionPossible(TextEncryptor textEncryptor) {
		if (textEncryptor instanceof RsaSecretEncryptor
				&& !((RsaSecretEncryptor) textEncryptor).canDecrypt()) {
			throw new DecryptionNotSupportedException();
		}
	}

	private String stripFormData(String data, MediaType type, boolean cipher) {

		if (data.endsWith("=") && !type.equals(MediaType.TEXT_PLAIN)) {
			try {
				data = URLDecoder.decode(data, "UTF-8");
				if (cipher) {
					data = data.replace(" ", "+");
				}
			}
			catch (UnsupportedEncodingException e) {
				// Really?
			}
			String candidate = data.substring(0, data.length() - 1);
			if (cipher) {
				if (data.endsWith("=")) {
					if (data.length() / 2 != (data.length() + 1) / 2) {
						try {
							Hex.decode(candidate);
							return candidate;
						}
						catch (IllegalArgumentException e) {
							try {
								Base64Utils.decode(candidate.getBytes());
								return candidate;
							}
							catch (IllegalArgumentException ex) {
							}
						}
					}
				}
				return data;
			}
			// User posted data with content type form but meant it to be text/plain
			data = candidate;
		}

		return data;

	}

	@ExceptionHandler(KeyFormatException.class)
	public ResponseEntity<Map<String, Object>> keyFormat() {
		Map<String, Object> body = new HashMap<>();
		body.put("status", "BAD_REQUEST");
		body.put("description", "Key data not in correct format (PEM or jks keystore)");
		return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(KeyNotAvailableException.class)
	public ResponseEntity<Map<String, Object>> keyUnavailable() {
		Map<String, Object> body = new HashMap<>();
		body.put("status", "NOT_FOUND");
		body.put("description", "No public key available");
		return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(DecryptionNotSupportedException.class)
	public ResponseEntity<Map<String, Object>> decryptionDisabled() {
		Map<String, Object> body = new HashMap<>();
		body.put("status", "BAD_REQUEST");
		body.put("description", "Server-side decryption is not supported");
		return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(KeyNotInstalledException.class)
	public ResponseEntity<Map<String, Object>> notInstalled() {
		Map<String, Object> body = new HashMap<>();
		body.put("status", "NO_KEY");
		body.put("description", "No key was installed for encryption service");
		return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(EncryptionTooWeakException.class)
	public ResponseEntity<Map<String, Object>> encryptionTooWeak() {
		Map<String, Object> body = new HashMap<>();
		body.put("status", "INVALID");
		body.put("description", "The encryption algorithm is not strong enough");
		return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(InvalidCipherException.class)
	public ResponseEntity<Map<String, Object>> invalidCipher() {
		Map<String, Object> body = new HashMap<>();
		body.put("status", "INVALID");
		body.put("description", "Text not encrypted with this key");
		return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
	}

}

@SuppressWarnings("serial")
class KeyNotInstalledException extends RuntimeException {

}

@SuppressWarnings("serial")
class KeyNotAvailableException extends RuntimeException {

}

@SuppressWarnings("serial")
class EncryptionTooWeakException extends RuntimeException {

}

@SuppressWarnings("serial")
class InvalidCipherException extends RuntimeException {

}

@SuppressWarnings("serial")
class DecryptionNotSupportedException extends RuntimeException {

}
