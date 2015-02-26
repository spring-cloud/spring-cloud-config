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
package org.springframework.cloud.configure.server;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.security.KeyPair;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.configure.Environment;
import org.springframework.cloud.configure.PropertySource;
import org.springframework.cloud.configure.encrypt.EncryptorFactory;
import org.springframework.cloud.configure.encrypt.KeyFormatException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.codec.Base64;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.rsa.crypto.KeyStoreKeyFactory;
import org.springframework.security.rsa.crypto.RsaKeyHolder;
import org.springframework.security.rsa.crypto.RsaSecretEncryptor;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author Dave Syer
 *
 */
@RestController
@RequestMapping("${spring.cloud.config.server.prefix:}")
public class EncryptionController {

	private static Log logger = LogFactory.getLog(EncryptionController.class);

	private TextEncryptor encryptor;

	@Autowired(required = false)
	public void setEncryptor(TextEncryptor encryptor) {
		this.encryptor = encryptor;
	}

	@RequestMapping(value = "/key", method = RequestMethod.GET)
	public String getPublicKey() {
		if (!(encryptor instanceof RsaKeyHolder)) {
			throw new KeyNotAvailableException();
		}
		return ((RsaKeyHolder) encryptor).getPublicKey();
	}

	@RequestMapping(value = "/key", method = RequestMethod.POST, params = { "password" })
	public ResponseEntity<Map<String, Object>> uploadKeyStore(
			@RequestParam("file") MultipartFile file,
			@RequestParam("password") String password, @RequestParam("alias") String alias) {

		Map<String, Object> body = new HashMap<String, Object>();
		body.put("status", "OK");

		try {
			ByteArrayResource resource = new ByteArrayResource(file.getBytes());
			KeyPair keyPair = new KeyStoreKeyFactory(resource, password.toCharArray())
					.getKeyPair(alias);
			encryptor = new RsaSecretEncryptor(keyPair);
			body.put("publicKey", ((RsaKeyHolder) encryptor).getPublicKey());
		}
		catch (IOException e) {
			throw new KeyFormatException();
		}

		return new ResponseEntity<Map<String, Object>>(body, HttpStatus.CREATED);

	}

	@RequestMapping(value = "/key", method = RequestMethod.POST, params = { "!password" })
	public ResponseEntity<Map<String, Object>> uploadKey(@RequestBody String data,
			@RequestHeader("Content-Type") MediaType type) {

		Map<String, Object> body = new HashMap<String, Object>();
		body.put("status", "OK");

		encryptor = new EncryptorFactory().create(stripFormData(data, type, false));

		if (encryptor instanceof RsaKeyHolder) {
			body.put("publicKey", ((RsaKeyHolder) encryptor).getPublicKey());
		}
		return new ResponseEntity<Map<String, Object>>(body, HttpStatus.CREATED);

	}

	@ExceptionHandler(KeyFormatException.class)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> keyFormat() {
		Map<String, Object> body = new HashMap<String, Object>();
		body.put("status", "BAD_REQUEST");
		body.put("description", "Key data not in correct format (PEM or jks keystore)");
		return new ResponseEntity<Map<String, Object>>(body, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(KeyNotAvailableException.class)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> keyUnavailable() {
		Map<String, Object> body = new HashMap<String, Object>();
		body.put("status", "NOT_FOUND");
		body.put("description", "No public key available");
		return new ResponseEntity<Map<String, Object>>(body, HttpStatus.NOT_FOUND);
	}

	@RequestMapping(value = "encrypt/status", method = RequestMethod.GET)
	public Map<String, Object> status() {
		if (encryptor == null) {
			throw new KeyNotInstalledException();
		}
		return Collections.<String, Object> singletonMap("status", "OK");
	}

	@RequestMapping(value = "encrypt", method = RequestMethod.POST)
	public String encrypt(@RequestBody String data,
			@RequestHeader("Content-Type") MediaType type) {
		if (encryptor == null) {
			throw new KeyNotInstalledException();
		}
		data = stripFormData(data, type, false);
		return encryptor.encrypt(data);
	}

	@RequestMapping(value = "decrypt", method = RequestMethod.POST)
	public String decrypt(@RequestBody String data,
			@RequestHeader("Content-Type") MediaType type) {
		if (encryptor == null) {
			throw new KeyNotInstalledException();
		}
		try {
			data = stripFormData(data, type, true);
			return encryptor.decrypt(data);
		}
		catch (IllegalArgumentException e) {
			throw new InvalidCipherException();
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
			String candidate = data.substring(0, data.length()-1);
			if (cipher) {
				if (data.endsWith("=")) {
					 if (data.length()/2!=(data.length()+1)/2) {
						 try {
							 Hex.decode(candidate);
							 return candidate;
						 } catch (IllegalArgumentException e) {
							 if (Base64.isBase64(data.getBytes())) {
								 return data;
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

	@ExceptionHandler(KeyNotInstalledException.class)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> notInstalled() {
		Map<String, Object> body = new HashMap<String, Object>();
		body.put("status", "NO_KEY");
		body.put("description", "No key was installed for encryption service");
		return new ResponseEntity<Map<String, Object>>(body, HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(InvalidCipherException.class)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> invalidCipher() {
		Map<String, Object> body = new HashMap<String, Object>();
		body.put("status", "INVALID");
		body.put("description", "Text not encrypted with this key");
		return new ResponseEntity<Map<String, Object>>(body, HttpStatus.BAD_REQUEST);
	}

	public Environment decrypt(Environment environment) {
		Environment result = new Environment(environment.getName(),
				environment.getLabel());
		for (PropertySource source : environment.getPropertySources()) {
			Map<Object, Object> map = new LinkedHashMap<Object, Object>(
					source.getSource());
			for (Entry<Object,Object> entry : map.entrySet()) {
				Object key = entry.getKey();
				String name = key.toString();
				String value = entry.getValue().toString();
				if (value.startsWith("{cipher}")) {
					map.remove(key);
					if (encryptor == null) {
						map.put(name, value);
					}
					else {
						try {
							value = value == null ? null : encryptor.decrypt(value
									.substring("{cipher}".length()));
						}
						catch (Exception e) {
							value = "<n/a>";
							name = "invalid." + name;
							logger.warn("Cannot decrypt key: " + key + " ("
									+ e.getClass() + ": " + e.getMessage() + ")");
						}
						map.put(name, value);
					}
				}
			}
			result.add(new PropertySource(source.getName(), map));
		}
		return result;
	}
}

@SuppressWarnings("serial")
class KeyNotInstalledException extends RuntimeException {
}

@SuppressWarnings("serial")
class KeyNotAvailableException extends RuntimeException {
}

@SuppressWarnings("serial")
class InvalidCipherException extends RuntimeException {
}
