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
 * @author Pavel Andrusov
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
	public void shouldNotPrefixInvalidPropertyWithNoKeyValue() {
		// given
		String accounts = "accounts/mypay";
		String value = "{vault}:" + accounts;

		VaultKeyValueOperations keyValueTemplate = mock(VaultKeyValueOperations.class);

		VaultEnvironmentEncryptor encryptor = new VaultEnvironmentEncryptor(keyValueTemplate);
		encryptor.setPrefixInvalidProperties(false);

		// when
		Environment environment = new Environment("name", "profile", "label");
		environment
			.add(new PropertySource("a", Collections.<Object, Object>singletonMap(environment.getName(), value)));

		// then
		Environment processedEnvironment = encryptor.decrypt(environment);

		assertThat(processedEnvironment.getPropertySources().get(0).getSource().get("invalid." + environment.getName()))
			.isNull();
		assertThat(processedEnvironment.getPropertySources().get(0).getSource().get(environment.getName()))
			.isEqualTo(accounts);
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

	@Test
	public void shouldResolvePropertyWithEnvironmentVariableInVaultKey() {
		// given
		String secret = "mysecret";
		String vaultKeyWithEnvVar = "accounts/${PATH}/mypay";

		VaultKeyValueOperations keyValueTemplate = mock(VaultKeyValueOperations.class);

		// Use PATH environment variable which should exist on most systems
		String pathValue = System.getenv("PATH");
		when(keyValueTemplate.get("accounts/" + pathValue + "/mypay"))
			.thenReturn(withVaultResponse("access_key", secret));

		VaultEnvironmentEncryptor encryptor = new VaultEnvironmentEncryptor(keyValueTemplate);

		// when
		Environment environment = new Environment("name", "profile", "label");
		environment.add(new PropertySource("a", Collections.<Object, Object>singletonMap(environment.getName(),
				"{vault}:" + vaultKeyWithEnvVar + "#access_key")));

		// then
		assertThat(encryptor.decrypt(environment).getPropertySources().get(0).getSource().get(environment.getName()))
			.isEqualTo(secret);
	}

	@Test
	public void shouldResolvePropertyWithEnvironmentVariableInVaultParamName() {
		// given
		String secret = "mysecret";
		String vaultParamWithEnvVar = "${USER}_key";

		VaultKeyValueOperations keyValueTemplate = mock(VaultKeyValueOperations.class);

		// Use USER environment variable which should exist on most systems
		String userValue = System.getenv("USER");
		when(keyValueTemplate.get("accounts/mypay")).thenReturn(withVaultResponse(userValue + "_key", secret));

		VaultEnvironmentEncryptor encryptor = new VaultEnvironmentEncryptor(keyValueTemplate);

		// when
		Environment environment = new Environment("name", "profile", "label");
		environment.add(new PropertySource("a", Collections.<Object, Object>singletonMap(environment.getName(),
				"{vault}:accounts/mypay#" + vaultParamWithEnvVar)));

		// then
		assertThat(encryptor.decrypt(environment).getPropertySources().get(0).getSource().get(environment.getName()))
			.isEqualTo(secret);
	}

	@Test
	public void shouldResolvePropertyWithMultipleEnvironmentVariables() {
		// given
		String secret = "mysecret";
		String vaultKeyWithMultipleEnvVars = "${USER}/accounts/${PATH}/mypay";

		VaultKeyValueOperations keyValueTemplate = mock(VaultKeyValueOperations.class);

		// Use USER and PATH environment variables which should exist on most systems
		String userValue = System.getenv("USER");
		String pathValue = System.getenv("PATH");
		when(keyValueTemplate.get(userValue + "/accounts/" + pathValue + "/mypay"))
			.thenReturn(withVaultResponse("access_key", secret));

		VaultEnvironmentEncryptor encryptor = new VaultEnvironmentEncryptor(keyValueTemplate);

		// when
		Environment environment = new Environment("name", "profile", "label");
		environment.add(new PropertySource("a", Collections.<Object, Object>singletonMap(environment.getName(),
				"{vault}:" + vaultKeyWithMultipleEnvVars + "#access_key")));

		// then
		assertThat(encryptor.decrypt(environment).getPropertySources().get(0).getSource().get(environment.getName()))
			.isEqualTo(secret);
	}

	@Test
	public void shouldKeepOriginalPlaceholderWhenEnvironmentVariableNotFound() {
		// given
		String secret = "mysecret";
		String vaultKeyWithNonExistentEnvVar = "accounts/${NON_EXISTENT_VAR}/mypay";

		VaultKeyValueOperations keyValueTemplate = mock(VaultKeyValueOperations.class);
		when(keyValueTemplate.get("accounts/${NON_EXISTENT_VAR}/mypay"))
			.thenReturn(withVaultResponse("access_key", secret));

		VaultEnvironmentEncryptor encryptor = new VaultEnvironmentEncryptor(keyValueTemplate);

		// when
		Environment environment = new Environment("name", "profile", "label");
		environment.add(new PropertySource("a", Collections.<Object, Object>singletonMap(environment.getName(),
				"{vault}:" + vaultKeyWithNonExistentEnvVar + "#access_key")));

		// then
		assertThat(encryptor.decrypt(environment).getPropertySources().get(0).getSource().get(environment.getName()))
			.isEqualTo(secret);
	}

	@Test
	public void shouldHandleMixedExistingAndNonExistingEnvironmentVariables() {
		// given
		String secret = "mysecret";
		String vaultKeyWithMixedEnvVars = "${USER}/accounts/${NON_EXISTENT_VAR}/mypay";

		VaultKeyValueOperations keyValueTemplate = mock(VaultKeyValueOperations.class);

		// Use USER environment variable which should exist on most systems
		String userValue = System.getenv("USER");
		when(keyValueTemplate.get(userValue + "/accounts/${NON_EXISTENT_VAR}/mypay"))
			.thenReturn(withVaultResponse("access_key", secret));

		VaultEnvironmentEncryptor encryptor = new VaultEnvironmentEncryptor(keyValueTemplate);

		// when
		Environment environment = new Environment("name", "profile", "label");
		environment.add(new PropertySource("a", Collections.<Object, Object>singletonMap(environment.getName(),
				"{vault}:" + vaultKeyWithMixedEnvVars + "#access_key")));

		// then
		assertThat(encryptor.decrypt(environment).getPropertySources().get(0).getSource().get(environment.getName()))
			.isEqualTo(secret);
	}

	@Test
	public void shouldHandleNullInputGracefully() {
		// given
		String secret = "mysecret";

		VaultKeyValueOperations keyValueTemplate = mock(VaultKeyValueOperations.class);
		when(keyValueTemplate.get(null)).thenReturn(withVaultResponse("access_key", secret));

		VaultEnvironmentEncryptor encryptor = new VaultEnvironmentEncryptor(keyValueTemplate);

		// when
		Environment environment = new Environment("name", "profile", "label");
		environment.add(new PropertySource("a",
				Collections.<Object, Object>singletonMap(environment.getName(), "{vault}:#access_key")));

		// then
		Environment processedEnvironment = encryptor.decrypt(environment);
		assertThat(processedEnvironment.getPropertySources().get(0).getSource().get("invalid." + environment.getName()))
			.isEqualTo("<n/a>");
	}

	@Test
	public void shouldHandleEmptyStringInput() {
		// given
		String secret = "mysecret";

		VaultKeyValueOperations keyValueTemplate = mock(VaultKeyValueOperations.class);
		when(keyValueTemplate.get("")).thenReturn(withVaultResponse("access_key", secret));

		VaultEnvironmentEncryptor encryptor = new VaultEnvironmentEncryptor(keyValueTemplate);

		// when
		Environment environment = new Environment("name", "profile", "label");
		environment.add(new PropertySource("a",
				Collections.<Object, Object>singletonMap(environment.getName(), "{vault}:#access_key")));

		// then
		Environment processedEnvironment = encryptor.decrypt(environment);
		assertThat(processedEnvironment.getPropertySources().get(0).getSource().get("invalid." + environment.getName()))
			.isEqualTo("<n/a>");
	}

	@Test
	public void shouldHandleMultiplePropertiesWithEnvironmentVariables() {
		// given
		String secret1 = "secret1";
		String secret2 = "secret2";

		VaultKeyValueOperations keyValueTemplate = mock(VaultKeyValueOperations.class);
		String userValue = System.getenv("USER");
		String pathValue = System.getenv("PATH");
		when(keyValueTemplate.get("accounts/" + userValue + "/mypay"))
			.thenReturn(withVaultResponse("access_key", secret1));
		when(keyValueTemplate.get("accounts/" + pathValue + "/mypay"))
			.thenReturn(withVaultResponse("access_key", secret2));

		VaultEnvironmentEncryptor encryptor = new VaultEnvironmentEncryptor(keyValueTemplate);

		// when
		Environment environment = new Environment("name", "profile", "label");
		Map<Object, Object> properties = new HashMap<>();
		properties.put("property1", "{vault}:accounts/${USER}/mypay#access_key");
		properties.put("property2", "{vault}:accounts/${PATH}/mypay#access_key");
		environment.add(new PropertySource("a", properties));

		// then
		Environment processedEnvironment = encryptor.decrypt(environment);
		assertThat(processedEnvironment.getPropertySources().get(0).getSource().get("property1")).isEqualTo(secret1);
		assertThat(processedEnvironment.getPropertySources().get(0).getSource().get("property2")).isEqualTo(secret2);
	}

	private VaultResponse withVaultResponse(String key, Object value) {
		Map<String, Object> responseData = new HashMap<>();
		responseData.put(key, value);

		VaultResponse response = new VaultResponse();
		response.setData(responseData);

		return response;
	}

}
