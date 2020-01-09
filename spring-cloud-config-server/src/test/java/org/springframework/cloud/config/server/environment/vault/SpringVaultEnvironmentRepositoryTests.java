/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.config.server.environment.vault;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.junit.Test;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.environment.EnvironmentWatch;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.support.VaultResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Spencer Gibb
 * @author Ryan Baxter
 * @author Haroun Pacquee
 * @author Mark Paluch
 * @author Haytham Mohamed
 * @author Scott Frederick
 */
@SuppressWarnings("rawtypes")
public class SpringVaultEnvironmentRepositoryTests {

	@Test
	public void testFindOneNoDefaultKey() {
		VaultKeyValueOperations keyValueTemplate = mock(VaultKeyValueOperations.class);
		when(keyValueTemplate.get("myapp")).thenReturn(withVaultResponse("foo", "bar"));
		when(keyValueTemplate.get("application"))
				.thenReturn(withVaultResponse("def-foo", "def-bar"));

		SpringVaultEnvironmentRepository repo = new SpringVaultEnvironmentRepository(
				mockHttpRequest(), new EnvironmentWatch.Default(),
				new VaultEnvironmentProperties(), keyValueTemplate);

		Environment e = repo.findOne("myapp", null, null);
		assertThat(e.getName()).as("Name should be the same as the application argument")
				.isEqualTo("myapp");
		assertThat(e.getPropertySources()).as(
				"Properties for specified application and default application with key 'application' should be returned")
				.hasSize(2);

		assertThat(e.getPropertySources().get(0).getSource()).as(
				"Properties for specified application should be returned in priority position")
				.containsOnly((Map.Entry) entry("foo", "bar"));
		assertThat(e.getPropertySources().get(1).getSource()).as(
				"Properties for default application with key 'application' should be returned in second position")
				.containsOnly((Map.Entry) entry("def-foo", "def-bar"));
	}

	@Test
	public void testBackendWithSlashes() {
		VaultKeyValueOperations keyValueTemplate = mock(VaultKeyValueOperations.class);
		when(keyValueTemplate.get("myapp")).thenReturn(withVaultResponse("foo", "bar"));
		when(keyValueTemplate.get("application"))
				.thenReturn(withVaultResponse("def-foo", "def-bar"));

		VaultEnvironmentProperties properties = new VaultEnvironmentProperties();
		properties.setBackend("foo/bar/secret");

		SpringVaultEnvironmentRepository repo = new SpringVaultEnvironmentRepository(
				mockHttpRequest(), new EnvironmentWatch.Default(), properties,
				keyValueTemplate);

		Environment e = repo.findOne("myapp", null, null);
		assertThat(e.getName()).as("Name should be the same as the application argument")
				.isEqualTo("myapp");
		assertThat(e.getPropertySources()).as(
				"Properties for specified application and default application with key 'application' should be returned")
				.hasSize(2);

		assertThat(e.getPropertySources().get(0).getSource()).as(
				"Properties for specified application should be returned in priority position")
				.containsOnly((Map.Entry) entry("foo", "bar"));
		assertThat(e.getPropertySources().get(1).getSource()).as(
				"Properties for default application with key 'application' should be returned in second position")
				.containsOnly((Map.Entry) entry("def-foo", "def-bar"));
	}

	@Test
	public void testFindOneDefaultKeySetAndDifferentToApplication() {
		VaultKeyValueOperations keyValueTemplate = mock(VaultKeyValueOperations.class);
		when(keyValueTemplate.get("myapp")).thenReturn(withVaultResponse("foo", "bar"));
		when(keyValueTemplate.get("mydefaultkey"))
				.thenReturn(withVaultResponse("def-foo", "def-bar"));

		SpringVaultEnvironmentRepository repo = new SpringVaultEnvironmentRepository(
				mockHttpRequest(), new EnvironmentWatch.Default(),
				new VaultEnvironmentProperties(), keyValueTemplate);
		repo.setDefaultKey("mydefaultkey");

		Environment e = repo.findOne("myapp", null, null);
		assertThat(e.getName()).as("Name should be the same as the application argument")
				.isEqualTo("myapp");
		assertThat(e.getPropertySources()).as(
				"Properties for specified application and default application with key 'mydefaultkey' should be returned")
				.hasSize(2);

		assertThat(e.getPropertySources().get(0).getSource()).as(
				"Properties for specified application should be returned in priority position")
				.containsOnly((Map.Entry) entry("foo", "bar"));
		assertThat(e.getPropertySources().get(1).getSource()).as(
				"Properties for default application with key 'mydefaultkey' should be returned in second position")
				.containsOnly((Map.Entry) entry("def-foo", "def-bar"));
	}

	@Test
	public void testFindOneDefaultKeySetAndDifferentToMultipleApplications() {
		VaultKeyValueOperations keyValueTemplate = mock(VaultKeyValueOperations.class);
		when(keyValueTemplate.get("myapp"))
				.thenReturn(withVaultResponse("myapp-foo", "myapp-bar"));
		when(keyValueTemplate.get("yourapp"))
				.thenReturn(withVaultResponse("yourapp-foo", "yourapp-bar"));
		when(keyValueTemplate.get("mydefaultkey"))
				.thenReturn(withVaultResponse("def-foo", "def-bar"));

		SpringVaultEnvironmentRepository repo = new SpringVaultEnvironmentRepository(
				mockHttpRequest(), new EnvironmentWatch.Default(),
				new VaultEnvironmentProperties(), keyValueTemplate);
		repo.setDefaultKey("mydefaultkey");

		Environment e = repo.findOne("myapp,yourapp", null, null);
		assertThat(e.getName()).as("Name should be the same as the application argument")
				.isEqualTo("myapp,yourapp");
		assertThat(e.getPropertySources()).as(
				"Properties for specified applications and default application with key 'mydefaultkey' should be returned")
				.hasSize(3);

		assertThat(e.getPropertySources().get(0).getSource()).as(
				"Properties for first specified application should be returned in priority position")
				.containsOnly((Map.Entry) entry("yourapp-foo", "yourapp-bar"));

		assertThat(e.getPropertySources().get(1).getSource()).as(
				"Properties for second specified application should be returned in priority position")
				.containsOnly((Map.Entry) entry("myapp-foo", "myapp-bar"));

		assertThat(e.getPropertySources().get(2).getSource()).as(
				"Properties for default application with key 'mydefaultkey' should be returned in second position")
				.containsOnly((Map.Entry) entry("def-foo", "def-bar"));
	}

	@Test
	public void testFindOneDefaultKeySetAndEqualToApplication() {
		VaultKeyValueOperations keyValueTemplate = mock(VaultKeyValueOperations.class);
		when(keyValueTemplate.get("myapp")).thenReturn(withVaultResponse("foo", "bar"));
		when(keyValueTemplate.get("application"))
				.thenReturn(withVaultResponse("def-foo", "def-bar"));

		SpringVaultEnvironmentRepository repo = new SpringVaultEnvironmentRepository(
				mockHttpRequest(), new EnvironmentWatch.Default(),
				new VaultEnvironmentProperties(), keyValueTemplate);
		repo.setDefaultKey("myapp");

		Environment e = repo.findOne("myapp", null, null);
		assertThat(e.getName()).as("Name should be the same as the application argument")
				.isEqualTo("myapp");
		assertThat(e.getPropertySources())
				.as("Only properties for specified application should be returned")
				.hasSize(1);

		assertThat(e.getPropertySources().get(0).getSource())
				.as("Properties should be returned for specified application")
				.containsOnly((Map.Entry) entry("foo", "bar"));
	}

	@Test
	public void testVaultVersioning() {
		VaultKeyValueOperations keyValueTemplate = mock(VaultKeyValueOperations.class);
		when(keyValueTemplate.get("myapp")).thenReturn(withVaultResponse("foo", "bar"));
		when(keyValueTemplate.get("application"))
				.thenReturn(withVaultResponse("def-foo", "def-bar"));

		final VaultEnvironmentProperties vaultEnvironmentProperties = new VaultEnvironmentProperties();
		vaultEnvironmentProperties.setKvVersion(2);
		SpringVaultEnvironmentRepository repo = new SpringVaultEnvironmentRepository(
				mockHttpRequest(), new EnvironmentWatch.Default(),
				vaultEnvironmentProperties, keyValueTemplate);

		Environment e = repo.findOne("myapp", null, null);
		assertThat(e.getName()).as("Name should be the same as the application argument")
				.isEqualTo("myapp");
		assertThat(e.getPropertySources()).as(
				"Properties for specified application and default application with key 'application' should be returned")
				.hasSize(2);

		assertThat(e.getPropertySources().get(0).getSource()).as(
				"Properties for specified application should be returned in priority position")
				.containsOnly((Map.Entry) entry("foo", "bar"));
	}

	private VaultResponse withVaultResponse(String key, Object value) {
		Map<String, Object> responseData = new HashMap<>();
		responseData.put(key, value);

		VaultResponse response = new VaultResponse();
		response.setData(responseData);

		return response;
	}

	@SuppressWarnings("unchecked")
	private ObjectProvider<HttpServletRequest> mockHttpRequest() {
		ObjectProvider<HttpServletRequest> objectProvider = mock(ObjectProvider.class);
		when(objectProvider.getIfAvailable()).thenReturn(null);
		return objectProvider;
	}

}
