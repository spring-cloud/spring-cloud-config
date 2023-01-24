/*
 * Copyright 2002-2019 the original author or authors.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.runners.Parameterized.Parameters;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.environment.PropertyValueDescriptor;
import org.springframework.cloud.context.encrypt.EncryptorFactory;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Converted all the tests to parameterized tests.
 *
 * @author Siva Krishna Battu
 */
public class CipherEnvironmentEncryptorTests {

	@Parameters
	public static List<Object[]> params() {
		List<Object[]> list = new ArrayList<>();
		list.add(new Object[] { "deadbeef", "foo" });
		list.add(new Object[] { "4567890a12345678", "bar" });
		return list;
	}

	@ParameterizedTest
	@MethodSource("params")
	public void shouldDecryptEnvironment(String salt, String key) {
		TextEncryptor textEncryptor = new EncryptorFactory(salt).create(key);
		EnvironmentEncryptor encryptor = new CipherEnvironmentEncryptor(keys -> textEncryptor);
		// given
		String secret = randomUUID().toString();

		// when
		Environment environment = new Environment("name", "profile", "label");
		environment.add(new PropertySource("a", Collections.<Object, Object>singletonMap(environment.getName(),
				"{cipher}" + textEncryptor.encrypt(secret))));

		// then
		assertThat(encryptor.decrypt(environment).getPropertySources().get(0).getSource().get(environment.getName()))
				.isEqualTo(secret);
	}

	@ParameterizedTest
	@MethodSource("params")
	public void shouldDecryptEnvironmentWithKey(String salt, String key) {

		TextEncryptor textEncryptor = new EncryptorFactory(salt).create(key);
		EnvironmentEncryptor encryptor = new CipherEnvironmentEncryptor(keys -> textEncryptor);

		// given
		String secret = randomUUID().toString();

		// when
		Environment environment = new Environment("name", "profile", "label");
		environment.add(new PropertySource("a", Collections.<Object, Object>singletonMap(environment.getName(),
				"{cipher}{key:test}" + textEncryptor.encrypt(secret))));

		// then
		assertThat(encryptor.decrypt(environment).getPropertySources().get(0).getSource().get(environment.getName()))
				.isEqualTo(secret);
	}

	@ParameterizedTest
	@MethodSource("params")
	public void shouldBeAbleToUseNullAsPropertyValue(String salt, String key) {
		TextEncryptor textEncryptor = new EncryptorFactory(salt).create(key);
		EnvironmentEncryptor encryptor = new CipherEnvironmentEncryptor(keys -> textEncryptor);
		// when
		Environment environment = new Environment("name", "profile", "label");
		environment.add(new PropertySource("a", Collections.<Object, Object>singletonMap(environment.getName(), null)));

		// then
		assertThat(encryptor.decrypt(environment).getPropertySources().get(0).getSource().get(environment.getName()))
				.isEqualTo(null);
	}

	@ParameterizedTest
	@MethodSource("params")
	public void shouldDecryptEnvironmentIncludeOrigin(String salt, String key) {
		TextEncryptor textEncryptor = new EncryptorFactory(salt).create(key);
		EnvironmentEncryptor encryptor = new CipherEnvironmentEncryptor(keys -> textEncryptor);
		// given
		String secret = randomUUID().toString();

		// when
		Environment environment = new Environment("name", "profile", "label");
		String encrypted = "{cipher}" + textEncryptor.encrypt(secret);
		environment.add(new PropertySource("a", Collections.<Object, Object>singletonMap(environment.getName(),
				new PropertyValueDescriptor(encrypted, "encrypted value"))));

		// then
		assertThat(encryptor.decrypt(environment).getPropertySources().get(0).getSource().get(environment.getName()))
				.isEqualTo(secret);
	}

}
