/*
 * Copyright 2018-present the original author or authors.
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
	public void findOneNoDefaultKey() {
		defaultKeyTest("", 2);
	}

	@Test
	public void findOneWithPathKey() {
		defaultKeyTest("mypath", 2);
	}

	@Test
	public void findOneWithPathKeyNotUsedWithVersionOne() {
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
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:myapp");
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo", "bar"));
		assertThat(e.getPropertySources().get(1).getName()).isEqualTo("vault:application");
		assertThat(e.getPropertySources().get(1).getSource()).isEqualTo(Map.of("def-foo", "def-bar"));
	}

	@Test
	public void findOneWithBackend() {
		when(keyValueTemplate.get("myapp")).thenReturn(withVaultResponse("foo", "bar"));

		var properties = new VaultEnvironmentProperties();
		properties.setBackend("custom-path");
		properties.setFullKeyPath(true);

		var e = springVaultEnvironmentRepository(properties).findOne("myapp", null, null);

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources()).hasSize(1);
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:custom-path/myapp");
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo", "bar"));
	}

	@Test
	public void findOneWithSlashesInBackend() {
		when(keyValueTemplate.get("myapp")).thenReturn(withVaultResponse("foo", "bar"));
		when(keyValueTemplate.get("application")).thenReturn(withVaultResponse("def-foo", "def-bar"));

		var properties = new VaultEnvironmentProperties();
		properties.setBackend("foo/bar/secret");

		var e = springVaultEnvironmentRepository(properties).findOne("myapp", null, null);

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources()).hasSize(2);
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:myapp");
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo", "bar"));
		assertThat(e.getPropertySources().get(1).getName()).isEqualTo("vault:application");
		assertThat(e.getPropertySources().get(1).getSource()).isEqualTo(Map.of("def-foo", "def-bar"));
	}

	@Test
	public void findOneWithDefaultKeySet() {
		when(keyValueTemplate.get("myapp")).thenReturn(withVaultResponse("foo", "bar"));
		when(keyValueTemplate.get("mydefaultkey")).thenReturn(withVaultResponse("def-foo", "def-bar"));

		var properties = new VaultEnvironmentProperties();
		properties.setDefaultKey("mydefaultkey");

		var e = springVaultEnvironmentRepository(properties).findOne("myapp", null, "label");

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources()).hasSize(2);
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:myapp");
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo", "bar"));
		assertThat(e.getPropertySources().get(1).getName()).isEqualTo("vault:mydefaultkey");
		assertThat(e.getPropertySources().get(1).getSource()).isEqualTo(Map.of("def-foo", "def-bar"));
	}

	@Test
	public void findOneWithEmptyDefaultKey() {
		when(keyValueTemplate.get("myapp")).thenReturn(withVaultResponse("foo", "bar"));
		when(keyValueTemplate.get("application")).thenReturn(withVaultResponse("def-foo", "def-bar"));

		var properties = new VaultEnvironmentProperties();
		properties.setDefaultKey("");

		var e = springVaultEnvironmentRepository(properties).findOne("myapp", null, "label");

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources()).hasSize(1);
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:myapp");
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo", "bar"));
	}

	@Test
	public void findOneWithDefaultKeyAndMultipleApplicationNames() {
		when(keyValueTemplate.get("myapp")).thenReturn(withVaultResponse("myapp-foo", "myapp-bar"));
		when(keyValueTemplate.get("yourapp")).thenReturn(withVaultResponse("yourapp-foo", "yourapp-bar"));
		when(keyValueTemplate.get("mydefaultkey")).thenReturn(withVaultResponse("def-foo", "def-bar"));

		var properties = new VaultEnvironmentProperties();
		properties.setDefaultKey("mydefaultkey");

		var e = springVaultEnvironmentRepository(properties).findOne("myapp,yourapp", null, "lbl");

		assertThat(e.getName()).isEqualTo("myapp,yourapp");

		assertThat(e.getPropertySources()).hasSize(3);
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:yourapp");
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("yourapp-foo", "yourapp-bar"));
		assertThat(e.getPropertySources().get(1).getName()).isEqualTo("vault:myapp");
		assertThat(e.getPropertySources().get(1).getSource()).isEqualTo(Map.of("myapp-foo", "myapp-bar"));
		assertThat(e.getPropertySources().get(2).getName()).isEqualTo("vault:mydefaultkey");
		assertThat(e.getPropertySources().get(2).getSource()).isEqualTo(Map.of("def-foo", "def-bar"));
	}

	@Test
	public void findOneWithDefaultKeySetToApplicationName() {
		when(keyValueTemplate.get("myapp")).thenReturn(withVaultResponse("foo", "bar"));
		when(keyValueTemplate.get("application")).thenReturn(withVaultResponse("def-foo", "def-bar"));

		var properties = new VaultEnvironmentProperties();
		properties.setDefaultKey("myapp");

		var e = springVaultEnvironmentRepository(properties).findOne("myapp", null, null);

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources()).hasSize(1);
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:myapp");
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo", "bar"));
	}

	@Test
	public void findOneWithProfile() {
		when(keyValueTemplate.get("myapp")).thenReturn(withVaultResponse("foo", "bar"));
		when(keyValueTemplate.get("myapp,pr1")).thenReturn(withVaultResponse("foo-pr1", "bar-pr1"));
		when(keyValueTemplate.get("application")).thenReturn(withVaultResponse("def-foo", "def-bar"));
		when(keyValueTemplate.get("application,pr1")).thenReturn(withVaultResponse("def-pr1-foo", "def-pr1-bar"));

		var properties = new VaultEnvironmentProperties();

		var e = springVaultEnvironmentRepository(properties).findOne("myapp", "pr1", "lbl");

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources()).hasSize(4);
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:myapp,pr1");
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo-pr1", "bar-pr1"));
		assertThat(e.getPropertySources().get(1).getName()).isEqualTo("vault:application,pr1");
		assertThat(e.getPropertySources().get(1).getSource()).isEqualTo(Map.of("def-pr1-foo", "def-pr1-bar"));
		assertThat(e.getPropertySources().get(2).getName()).isEqualTo("vault:myapp");
		assertThat(e.getPropertySources().get(2).getSource()).isEqualTo(Map.of("foo", "bar"));
		assertThat(e.getPropertySources().get(3).getName()).isEqualTo("vault:application");
		assertThat(e.getPropertySources().get(3).getSource()).isEqualTo(Map.of("def-foo", "def-bar"));
	}

	@Test
	public void findOneWithMultipleProfiles() {
		when(keyValueTemplate.get("myapp")).thenReturn(withVaultResponse("foo", "bar"));
		when(keyValueTemplate.get("myapp,pr1")).thenReturn(withVaultResponse("foo-pr1", "bar-pr1"));
		when(keyValueTemplate.get("myapp,pr2")).thenReturn(withVaultResponse("foo-pr2", "bar-pr2"));
		when(keyValueTemplate.get("application")).thenReturn(withVaultResponse("def-foo", "def-bar"));
		when(keyValueTemplate.get("application,pr1")).thenReturn(withVaultResponse("def-pr1-foo", "def-pr1-bar"));
		when(keyValueTemplate.get("application,pr2")).thenReturn(withVaultResponse("def-pr2-foo", "def-pr2-bar"));

		var properties = new VaultEnvironmentProperties();

		var e = springVaultEnvironmentRepository(properties).findOne("myapp", "pr1,pr2", "lbl");

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources()).hasSize(6);
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:myapp,pr2");
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo-pr2", "bar-pr2"));
		assertThat(e.getPropertySources().get(1).getName()).isEqualTo("vault:application,pr2");
		assertThat(e.getPropertySources().get(1).getSource()).isEqualTo(Map.of("def-pr2-foo", "def-pr2-bar"));
		assertThat(e.getPropertySources().get(2).getName()).isEqualTo("vault:myapp,pr1");
		assertThat(e.getPropertySources().get(2).getSource()).isEqualTo(Map.of("foo-pr1", "bar-pr1"));
		assertThat(e.getPropertySources().get(3).getName()).isEqualTo("vault:application,pr1");
		assertThat(e.getPropertySources().get(3).getSource()).isEqualTo(Map.of("def-pr1-foo", "def-pr1-bar"));
		assertThat(e.getPropertySources().get(4).getName()).isEqualTo("vault:myapp");
		assertThat(e.getPropertySources().get(4).getSource()).isEqualTo(Map.of("foo", "bar"));
		assertThat(e.getPropertySources().get(5).getName()).isEqualTo("vault:application");
		assertThat(e.getPropertySources().get(5).getSource()).isEqualTo(Map.of("def-foo", "def-bar"));
	}

	@Test
	public void findOneWithVaultVersioning() {
		when(keyValueTemplate.get("myapp")).thenReturn(withVaultResponse("foo", "bar"));
		when(keyValueTemplate.get("application")).thenReturn(withVaultResponse("def-foo", "def-bar"));

		var properties = new VaultEnvironmentProperties();
		properties.setKvVersion(2);

		var e = springVaultEnvironmentRepository(properties).findOne("myapp", null, null);

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources()).hasSize(2);
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:myapp");
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo", "bar"));
		assertThat(e.getPropertySources().get(1).getName()).isEqualTo("vault:application");
		assertThat(e.getPropertySources().get(1).getSource()).isEqualTo(Map.of("def-foo", "def-bar"));
	}

	@Test
	public void findOneWithDefaultLabelWhenLabelEnabled() {
		when(keyValueTemplate.get("myapp,default,main")).thenReturn(withVaultResponse("foo", "bar"));
		when(keyValueTemplate.get("application,default,main")).thenReturn(withVaultResponse("def-foo", "def-bar"));

		var properties = new VaultEnvironmentProperties();
		properties.setEnableLabel(true);

		var e = springVaultEnvironmentRepository(properties).findOne("myapp", null, null);

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources()).hasSize(2);
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:myapp,default,main");
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo", "bar"));
		assertThat(e.getPropertySources().get(1).getName()).isEqualTo("vault:application,default,main");
		assertThat(e.getPropertySources().get(1).getSource()).isEqualTo(Map.of("def-foo", "def-bar"));
	}

	@Test
	public void findOneWithProfileAndDefaultLabelWhenLabelEnabled() {
		when(keyValueTemplate.get("myapp,default,main")).thenReturn(withVaultResponse("foo", "bar"));
		when(keyValueTemplate.get("myapp,pr1,main")).thenReturn(withVaultResponse("pr1-foo", "pr1-bar"));
		when(keyValueTemplate.get("application,default,main")).thenReturn(withVaultResponse("def-foo", "def-bar"));
		when(keyValueTemplate.get("application,pr1,main")).thenReturn(withVaultResponse("def-pr1-foo", "def-pr1-bar"));

		var properties = new VaultEnvironmentProperties();
		properties.setEnableLabel(true);

		var e = springVaultEnvironmentRepository(properties).findOne("myapp", "pr1", null);

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources()).hasSize(4);
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:myapp,pr1,main");
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("pr1-foo", "pr1-bar"));
		assertThat(e.getPropertySources().get(1).getName()).isEqualTo("vault:application,pr1,main");
		assertThat(e.getPropertySources().get(1).getSource()).isEqualTo(Map.of("def-pr1-foo", "def-pr1-bar"));
		assertThat(e.getPropertySources().get(2).getName()).isEqualTo("vault:myapp,default,main");
		assertThat(e.getPropertySources().get(2).getSource()).isEqualTo(Map.of("foo", "bar"));
		assertThat(e.getPropertySources().get(3).getName()).isEqualTo("vault:application,default,main");
		assertThat(e.getPropertySources().get(3).getSource()).isEqualTo(Map.of("def-foo", "def-bar"));
	}

	@Test
	public void findOneWithCustomDefaultLabelWhenLabelEnabled() {
		when(keyValueTemplate.get("myapp,default,custom")).thenReturn(withVaultResponse("foo", "bar"));
		when(keyValueTemplate.get("application,default,custom")).thenReturn(withVaultResponse("def-foo", "def-bar"));

		var properties = new VaultEnvironmentProperties();
		properties.setEnableLabel(true);
		properties.setDefaultLabel("custom");

		var e = springVaultEnvironmentRepository(properties).findOne("myapp", null, null);

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources()).hasSize(2);
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:myapp,default,custom");
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo", "bar"));
		assertThat(e.getPropertySources().get(1).getName()).isEqualTo("vault:application,default,custom");
		assertThat(e.getPropertySources().get(1).getSource()).isEqualTo(Map.of("def-foo", "def-bar"));
	}

	@Test
	public void findOneWithCustomLabelWhenLabelEnabled() {
		when(keyValueTemplate.get("myapp,default,my-label")).thenReturn(withVaultResponse("foo", "bar"));
		when(keyValueTemplate.get("application,default,my-label")).thenReturn(withVaultResponse("def-foo", "def-bar"));

		var properties = new VaultEnvironmentProperties();
		properties.setEnableLabel(true);
		properties.setDefaultLabel("custom");

		var e = springVaultEnvironmentRepository(properties).findOne("myapp", null, "my-label");

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources()).hasSize(2);
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:myapp,default,my-label");
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo", "bar"));
		assertThat(e.getPropertySources().get(1).getName()).isEqualTo("vault:application,default,my-label");
		assertThat(e.getPropertySources().get(1).getSource()).isEqualTo(Map.of("def-foo", "def-bar"));
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
