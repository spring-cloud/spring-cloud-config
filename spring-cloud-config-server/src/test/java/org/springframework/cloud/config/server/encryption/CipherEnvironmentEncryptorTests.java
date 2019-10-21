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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.environment.PropertyValueDescriptor;
import org.springframework.cloud.context.encrypt.EncryptorFactory;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class CipherEnvironmentEncryptorTests {

	TextEncryptor textEncryptor = new EncryptorFactory().create("foo");

	EnvironmentEncryptor encryptor;

	public CipherEnvironmentEncryptorTests(String salt, String key) {
		this.textEncryptor = new EncryptorFactory(salt).create(key);
		this.encryptor = new CipherEnvironmentEncryptor(
				keys -> CipherEnvironmentEncryptorTests.this.textEncryptor);
	}

	@Parameters
	public static List<Object[]> params() {
		List<Object[]> list = new ArrayList<>();
		list.add(new Object[] { "deadbeef", "foo" });
		list.add(new Object[] { "4567890a12345678", "bar" });
		return list;
	}

	@Test
	public void shouldDecryptEnvironment() {
		// given
		String secret = randomUUID().toString();

		// when
		Environment environment = new Environment("name", "profile", "label");
		environment.add(new PropertySource("a", Collections.<Object, Object>singletonMap(
				environment.getName(), "{cipher}" + this.textEncryptor.encrypt(secret))));

		// then
		assertThat(this.encryptor.decrypt(environment).getPropertySources().get(0)
				.getSource().get(environment.getName())).isEqualTo(secret);
	}

	@Test
	public void shouldDecryptEnvironmentWithKey() {
		// given
		String secret = randomUUID().toString();

		// when
		Environment environment = new Environment("name", "profile", "label");
		environment.add(new PropertySource("a",
				Collections.<Object, Object>singletonMap(environment.getName(),
						"{cipher}{key:test}" + this.textEncryptor.encrypt(secret))));

		// then
		assertThat(this.encryptor.decrypt(environment).getPropertySources().get(0)
				.getSource().get(environment.getName())).isEqualTo(secret);
	}

	@Test
	public void shouldBeAbleToUseNullAsPropertyValue() {

		// when
		Environment environment = new Environment("name", "profile", "label");
		environment.add(new PropertySource("a",
				Collections.<Object, Object>singletonMap(environment.getName(), null)));

		// then
		assertThat(this.encryptor.decrypt(environment).getPropertySources().get(0)
				.getSource().get(environment.getName())).isEqualTo(null);
	}

	@Test
	public void shouldDecryptEnvironmentIncludeOrigin() {
		// given
		String secret = randomUUID().toString();

		// when
		Environment environment = new Environment("name", "profile", "label");
		String encrypted = "{cipher}" + this.textEncryptor.encrypt(secret);
		environment.add(new PropertySource("a",
				Collections.<Object, Object>singletonMap(environment.getName(),
						new PropertyValueDescriptor(encrypted, "encrypted value"))));

		// then
		assertThat(this.encryptor.decrypt(environment).getPropertySources().get(0)
				.getSource().get(environment.getName())).isEqualTo(secret);
	}

}
