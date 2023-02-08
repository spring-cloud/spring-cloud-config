/*
 * Copyright 2020-2020 the original author or authors.
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

package org.springframework.cloud.config.server.encryption.vault;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.support.VaultResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Alexey Zhokhov
 */
public class VaultEnvironmentEncryptorTests {

	@Test
	public void shouldResolveProperty() {
		// given
		String secret = "mysecret";

		VaultKeyValueOperations keyValueTemplate = mock(VaultKeyValueOperations.class);
		when(keyValueTemplate.get("accounts/mypay")).thenReturn(withVaultResponse("access_key", secret));

		VaultEnvironmentEncryptor encryptor = new VaultEnvironmentEncryptor(keyValueTemplate);

		// when
		Environment environment = new Environment("name", "profile", "label");
		environment.add(new PropertySource("a",
				Collections.<Object, Object>singletonMap(environment.getName(), "{vault}:accounts/mypay#access_key")));

		// then
		assertThat(encryptor.decrypt(environment).getPropertySources().get(0).getSource().get(environment.getName()))
				.isEqualTo(secret);
	}

	@Test
	public void shouldReturnNullIfPropertyNotFoundInVault() {
		// given
		String secret = "mysecret";

		VaultKeyValueOperations keyValueTemplate = mock(VaultKeyValueOperations.class);
		when(keyValueTemplate.get("accounts/mypay")).thenReturn(withVaultResponse("access_key", secret));

		VaultEnvironmentEncryptor encryptor = new VaultEnvironmentEncryptor(keyValueTemplate);

		// when
		Environment environment = new Environment("name", "profile", "label");
		environment.add(new PropertySource("a",
				Collections.<Object, Object>singletonMap(environment.getName(), "{vault}:accounts/mypay#another_key")));

		// then
		assertThat(encryptor.decrypt(environment).getPropertySources().get(0).getSource().get(environment.getName()))
				.isNull();
	}

	@Test
	public void shouldSkipPropertyWithNotVaultPrefix() {
		// given
		String value = "test{vault}:accounts/mypay#access_key";

		VaultKeyValueOperations keyValueTemplate = mock(VaultKeyValueOperations.class);

		VaultEnvironmentEncryptor encryptor = new VaultEnvironmentEncryptor(keyValueTemplate);

		// when
		Environment environment = new Environment("name", "profile", "label");
		environment
				.add(new PropertySource("a", Collections.<Object, Object>singletonMap(environment.getName(), value)));

		// then
		assertThat(encryptor.decrypt(environment).getPropertySources().get(0).getSource().get(environment.getName()))
				.isEqualTo(value);
	}

	@Test
	public void shouldMarkAsInvalidPropertyWithNoKeyValue() {
		// given
		String value = "{vault}:accounts/mypay";

		VaultKeyValueOperations keyValueTemplate = mock(VaultKeyValueOperations.class);

		VaultEnvironmentEncryptor encryptor = new VaultEnvironmentEncryptor(keyValueTemplate);

		// when
		Environment environment = new Environment("name", "profile", "label");
		environment
				.add(new PropertySource("a", Collections.<Object, Object>singletonMap(environment.getName(), value)));

		// then
		Environment processedEnvironment = encryptor.decrypt(environment);

		assertThat(processedEnvironment.getPropertySources().get(0).getSource().get(environment.getName())).isNull();
		assertThat(processedEnvironment.getPropertySources().get(0).getSource().get("invalid." + environment.getName()))
				.isEqualTo("<n/a>");
	}

	@Test
	public void shouldMarkAsInvalidPropertyWithNoEmptyValue() {
		// given
		String value = "{vault}:accounts/mypay#";

		VaultKeyValueOperations keyValueTemplate = mock(VaultKeyValueOperations.class);

		VaultEnvironmentEncryptor encryptor = new VaultEnvironmentEncryptor(keyValueTemplate);

		// when
		Environment environment = new Environment("name", "profile", "label");
		environment
				.add(new PropertySource("a", Collections.<Object, Object>singletonMap(environment.getName(), value)));

		// then
		Environment processedEnvironment = encryptor.decrypt(environment);

		assertThat(processedEnvironment.getPropertySources().get(0).getSource().get(environment.getName())).isNull();
		assertThat(processedEnvironment.getPropertySources().get(0).getSource().get("invalid." + environment.getName()))
				.isEqualTo("<n/a>");
	}

	@Test
	public void shouldMarkAsInvalidPropertyWithWrongFormat() {
		// given
		String value = "{vault}test:accounts/mypay#";

		VaultKeyValueOperations keyValueTemplate = mock(VaultKeyValueOperations.class);

		VaultEnvironmentEncryptor encryptor = new VaultEnvironmentEncryptor(keyValueTemplate);

		// when
		Environment environment = new Environment("name", "profile", "label");
		environment
				.add(new PropertySource("a", Collections.<Object, Object>singletonMap(environment.getName(), value)));

		// then
		Environment processedEnvironment = encryptor.decrypt(environment);

		assertThat(processedEnvironment.getPropertySources().get(0).getSource().get(environment.getName())).isNull();
		assertThat(processedEnvironment.getPropertySources().get(0).getSource().get("invalid." + environment.getName()))
				.isEqualTo("<n/a>");
	}

	@Test
	public void shouldMarkAsInvalidPropertyWithWrongFormat2() {
		// given
		String value = "{vault}:#xxx";

		VaultKeyValueOperations keyValueTemplate = mock(VaultKeyValueOperations.class);

		VaultEnvironmentEncryptor encryptor = new VaultEnvironmentEncryptor(keyValueTemplate);

		// when
		Environment environment = new Environment("name", "profile", "label");
		environment
				.add(new PropertySource("a", Collections.<Object, Object>singletonMap(environment.getName(), value)));

		// then
		Environment processedEnvironment = encryptor.decrypt(environment);

		assertThat(processedEnvironment.getPropertySources().get(0).getSource().get(environment.getName())).isNull();
		assertThat(processedEnvironment.getPropertySources().get(0).getSource().get("invalid." + environment.getName()))
				.isEqualTo("<n/a>");
	}

	private VaultResponse withVaultResponse(String key, Object value) {
		Map<String, Object> responseData = new HashMap<>();
		responseData.put(key, value);

		VaultResponse response = new VaultResponse();
		response.setData(responseData);

		return response;
	}

}
