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

package org.springframework.cloud.config.client;

import java.util.Objects;

import com.ulisesbocchio.jasyptspringboot.encryptor.SimplePBEByteEncryptor;
import com.ulisesbocchio.jasyptspringboot.encryptor.SimplePBEStringEncryptor;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.salt.RandomSaltGenerator;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * @author Bruce Randall
 *
 */
@ConfigurationProperties(EncryptorConfig.PREFIX)
public class EncryptorConfig {

	/**
	 * Prefix for Spring Cloud Config properties.
	 */
	public static final String PREFIX = "spring.cloud.config.encryptor";

	/**
	 * The Jayspt encryption password System Property.
	 */
	public static final String ENCRYPTOR_SYSTEM_PROPERTY = "jasypt.encryptor.password";

	/**
	 * The Jayspt Encryptor.
	 */
	private StringEncryptor encryptor;

	/**
	 * The Jasypt encryption algorithm.
	 */
	private String encryptorAlgorithm;

	/**
	 * Encryption iterations. Default = 1000.
	 */
	private Integer encryptorIterations;

	public String getEncryptorAlgorithm() {
		return encryptorAlgorithm;
	}

	public void setEncryptorAlgorithm(String encryptorAlgorithm) {
		this.encryptorAlgorithm = encryptorAlgorithm;
	}

	public Integer getEncryptorIterations() {
		return encryptorIterations;
	}

	public void setEncryptorIterations(Integer encryptorIterations) {
		this.encryptorIterations = encryptorIterations;
	}

	public StringEncryptor getEncryptor() {
		if (encryptor == null) {
			buildEncryptor();
		}
		return encryptor;
	}

	public String decryptProperty(String prop) {
		if (prop.startsWith("ENC(")) {
			prop = prop.substring(4, prop.lastIndexOf(")"));
			return getEncryptor().decrypt(prop);
		}
		return prop;
	}

	@Override
	public String toString() {
		return "EncryptorConfig{" + "encryptorAlgorithm='" + encryptorAlgorithm + '\'' + ", encryptorIterations="
				+ encryptorIterations + '}';
	}

	public void buildEncryptor() {
		SimplePBEByteEncryptor byteEncryptor = new SimplePBEByteEncryptor();
		byteEncryptor.setPassword(System.getProperty(ENCRYPTOR_SYSTEM_PROPERTY));
		if (StringUtils.hasText(this.encryptorAlgorithm)) {
			byteEncryptor.setAlgorithm(this.encryptorAlgorithm);
		}
		byteEncryptor.setIterations(Objects.requireNonNullElse(this.encryptorIterations, 1000));
		byteEncryptor.setSaltGenerator(new RandomSaltGenerator());

		encryptor = new SimplePBEStringEncryptor(byteEncryptor);
	}

}
