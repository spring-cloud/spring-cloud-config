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

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.config.server.environment.EnvironmentWatch;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties;
import org.springframework.util.StringUtils;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.support.VaultResponse;

import static org.assertj.core.api.Assertions.assertThat;
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
public class SpringVaultEnvironmentRepositoryTests {

	private final VaultKeyValueOperations keyValueTemplate = mock(VaultKeyValueOperations.class);

	@Test
	public void testFindOneNoDefaultKey() {
		defaultKeyTest("", 2);
	}

	@Test
	public void testPathKey() {
		defaultKeyTest("mypath", 2);
	}

	@Test
	public void testPathKeyNotUsedWithVersionOne() {
		defaultKeyTest("mypath", 1);
	}

	private void defaultKeyTest(String myPathKey, int version) {
		String path = "";
		if (StringUtils.hasText(myPathKey) && version == 2) {
			path = myPathKey + "/";
		}

		when(keyValueTemplate.get(path + "myapp")).thenReturn(withVaultResponse("foo", "bar"));
		when(keyValueTemplate.get(path + "application")).thenReturn(withVaultResponse("def-foo", "def-bar"));

		var properties = new VaultEnvironmentProperties();
		properties.setPathToKey(myPathKey);
		properties.setKvVersion(version);

		var e = springVaultEnvironmentRepository(properties).findOne("myapp", null, null);

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources()).hasSize(2);
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo", "bar"));
		assertThat(e.getPropertySources().get(1).getSource()).isEqualTo(Map.of("def-foo", "def-bar"));
	}

	@Test
	public void testBackendWithSlashes() {
		when(keyValueTemplate.get("myapp")).thenReturn(withVaultResponse("foo", "bar"));
		when(keyValueTemplate.get("application")).thenReturn(withVaultResponse("def-foo", "def-bar"));

		var properties = new VaultEnvironmentProperties();
		properties.setBackend("foo/bar/secret");

		var e = springVaultEnvironmentRepository(properties).findOne("myapp", null, null);

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources()).hasSize(2);
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo", "bar"));
		assertThat(e.getPropertySources().get(1).getSource()).isEqualTo(Map.of("def-foo", "def-bar"));
	}

	@Test
	public void testFindOneDefaultKeySetAndDifferentToApplication() {
		when(keyValueTemplate.get("myapp")).thenReturn(withVaultResponse("foo", "bar"));
		when(keyValueTemplate.get("mydefaultkey")).thenReturn(withVaultResponse("def-foo", "def-bar"));

		var properties = new VaultEnvironmentProperties();
		properties.setDefaultKey("mydefaultkey");

		var e = springVaultEnvironmentRepository(properties).findOne("myapp", null, null);

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources()).hasSize(2);
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo", "bar"));
		assertThat(e.getPropertySources().get(1).getSource()).isEqualTo(Map.of("def-foo", "def-bar"));
	}

	@Test
	public void testFindOneDefaultKeySetAndDifferentToMultipleApplications() {
		when(keyValueTemplate.get("myapp")).thenReturn(withVaultResponse("myapp-foo", "myapp-bar"));
		when(keyValueTemplate.get("yourapp")).thenReturn(withVaultResponse("yourapp-foo", "yourapp-bar"));
		when(keyValueTemplate.get("mydefaultkey")).thenReturn(withVaultResponse("def-foo", "def-bar"));

		var properties = new VaultEnvironmentProperties();
		properties.setDefaultKey("mydefaultkey");

		var e = springVaultEnvironmentRepository(properties).findOne("myapp,yourapp", null, null);

		assertThat(e.getName()).isEqualTo("myapp,yourapp");

		assertThat(e.getPropertySources()).hasSize(3);
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("yourapp-foo", "yourapp-bar"));
		assertThat(e.getPropertySources().get(1).getSource()).isEqualTo(Map.of("myapp-foo", "myapp-bar"));
		assertThat(e.getPropertySources().get(2).getSource()).isEqualTo(Map.of("def-foo", "def-bar"));
	}

	@Test
	public void testFindOneDefaultKeySetAndEqualToApplication() {
		when(keyValueTemplate.get("myapp")).thenReturn(withVaultResponse("foo", "bar"));
		when(keyValueTemplate.get("application")).thenReturn(withVaultResponse("def-foo", "def-bar"));

		var properties = new VaultEnvironmentProperties();
		properties.setDefaultKey("myapp");

		var e = springVaultEnvironmentRepository(properties).findOne("myapp", null, null);

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources()).hasSize(1);
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo", "bar"));
	}

	@Test
	public void testVaultVersioning() {
		when(keyValueTemplate.get("myapp")).thenReturn(withVaultResponse("foo", "bar"));
		when(keyValueTemplate.get("application")).thenReturn(withVaultResponse("def-foo", "def-bar"));

		var properties = new VaultEnvironmentProperties();
		properties.setKvVersion(2);

		var e = springVaultEnvironmentRepository(properties).findOne("myapp", null, null);

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources()).hasSize(2);
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo", "bar"));
	}

	private SpringVaultEnvironmentRepository springVaultEnvironmentRepository(VaultEnvironmentProperties properties) {
		return new SpringVaultEnvironmentRepository(mockHttpRequest(), new EnvironmentWatch.Default(), properties,
				keyValueTemplate);
	}

	private VaultResponse withVaultResponse(String key, Object value) {
		VaultResponse response = new VaultResponse();
		response.setData(Map.of(key, value));
		return response;
	}

	@SuppressWarnings("unchecked")
	private ObjectProvider<HttpServletRequest> mockHttpRequest() {
		ObjectProvider<HttpServletRequest> objectProvider = mock(ObjectProvider.class);
		when(objectProvider.getIfAvailable()).thenReturn(null);
		return objectProvider;
	}

}
