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

package org.springframework.cloud.config.server.environment;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.config.server.environment.VaultKvAccessStrategy.VaultResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
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
@SuppressWarnings("deprecation")
public class VaultEnvironmentRepositoryTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	private final RestTemplate rest = mock(RestTemplate.class);

	@SuppressWarnings("unchecked")
	ArgumentCaptor<HttpEntity<?>> requestHeaderCaptor = ArgumentCaptor.forClass(HttpEntity.class);

	@Test
	public void testFindOneWithNoDefaultKey() {
		stubRestTemplate("secret/myapp,master", toEntityResponse("foo", "bar"));
		stubRestTemplate("secret/application,master", toEntityResponse("def-foo", "def-bar"));

		var e = vaultEnvironmentRepository().findOne("myapp", null, null);

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources().size()).isEqualTo(2);
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:myapp,master");
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo", "bar"));
		assertThat(e.getPropertySources().get(1).getName()).isEqualTo("vault:application,master");
		assertThat(e.getPropertySources().get(1).getSource()).isEqualTo(Map.of("def-foo", "def-bar"));

		assertThat(requestHeaderCaptor.getValue().getHeaders()).containsEntry("X-Vault-Token", List.of("token"));
	}

	@Test
	public void testFindOneWithEmptyDefaultKey() {
		stubRestTemplate("secret/myapp,my-label", toEntityResponse("foo", "bar"));
		stubRestTemplate("secret/application,my-label", toEntityResponse("def-foo", "def-bar"));

		var properties = new VaultEnvironmentProperties();
		properties.setDefaultKey("");

		var e = vaultEnvironmentRepository(properties).findOne("myapp", null, "my-label");

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources().size()).isEqualTo(1);
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:myapp,my-label");
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo", "bar"));
	}

	@Test
	public void testBackendWithSlashes() {
		stubRestTemplate("foo/bar/secret/myapp,master", toEntityResponse("foo", "bar"));
		stubRestTemplate("foo/bar/secret/application,master", toEntityResponse("def-foo", "def-bar"));

		var properties = new VaultEnvironmentProperties();
		properties.setBackend("foo/bar/secret");

		var e = vaultEnvironmentRepository(properties).findOne("myapp", null, null);

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources().size()).isEqualTo(2);
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:myapp,master");
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo", "bar"));
		assertThat(e.getPropertySources().get(1).getName()).isEqualTo("vault:application,master");
		assertThat(e.getPropertySources().get(1).getSource()).isEqualTo(Map.of("def-foo", "def-bar"));
	}

	@Test
	public void testFindOneDefaultKeySetAndDifferentToApplication() {
		stubRestTemplate("secret/myapp,main", toEntityResponse("foo", "bar"));
		stubRestTemplate("secret/mydefaultkey,main", toEntityResponse("def-foo", "def-bar"));

		var properties = new VaultEnvironmentProperties();
		properties.setDefaultKey("mydefaultkey");

		var e = vaultEnvironmentRepository(properties).findOne("myapp", null, "main");

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources().size()).isEqualTo(2);
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:myapp,main");
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo", "bar"));
		assertThat(e.getPropertySources().get(1).getName()).isEqualTo("vault:mydefaultkey,main");
		assertThat(e.getPropertySources().get(1).getSource()).isEqualTo(Map.of("def-foo", "def-bar"));
	}

	@Test
	public void testFindOneDefaultKeySetAndDifferentToMultipleApplications() {
		stubRestTemplate("secret/myapp,master", toEntityResponse("myapp-foo", "myapp-bar"));
		stubRestTemplate("secret/yourapp,master", toEntityResponse("yourapp-foo", "yourapp-bar"));
		stubRestTemplate("secret/mydefaultkey,master", toEntityResponse("def-foo", "def-bar"));

		var properties = new VaultEnvironmentProperties();
		properties.setDefaultKey("mydefaultkey");

		var e = vaultEnvironmentRepository(properties).findOne("myapp,yourapp", null, null);

		assertThat(e.getName()).isEqualTo("myapp,yourapp");

		assertThat(e.getPropertySources().size()).isEqualTo(3);
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("yourapp-foo", "yourapp-bar"));
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:yourapp,master");
		assertThat(e.getPropertySources().get(1).getSource()).isEqualTo(Map.of("myapp-foo", "myapp-bar"));
		assertThat(e.getPropertySources().get(1).getName()).isEqualTo("vault:myapp,master");
		assertThat(e.getPropertySources().get(2).getSource()).isEqualTo(Map.of("def-foo", "def-bar"));
		assertThat(e.getPropertySources().get(2).getName()).isEqualTo("vault:mydefaultkey,master");
	}

	@Test
	public void testFindOneDefaultKeySetAndEqualToApplication() {
		stubRestTemplate("secret/myapp,lbl", toEntityResponse("foo", "bar"));
		stubRestTemplate("secret/application,lbl", toEntityResponse("def-foo", "def-bar"));

		var properties = new VaultEnvironmentProperties();
		properties.setDefaultKey("myapp");

		var e = vaultEnvironmentRepository(properties).findOne("myapp", null, "lbl");

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources().size()).isEqualTo(1);
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:myapp,lbl");
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo", "bar"));
	}

	@Test
	public void testFindOneWithProfile() {
		stubRestTemplate("secret/myapp,lbl", toEntityResponse("foo", "bar"));
		stubRestTemplate("secret/myapp,my-profile,lbl", toEntityResponse("pro-foo", "pro-bar"));
		stubRestTemplate("secret/application,lbl", toEntityResponse("def-foo", "def-bar"));
		stubRestTemplate("secret/application,my-profile,lbl", toEntityResponse("def-pro-foo", "def-pro-bar"));

		var e = vaultEnvironmentRepository().findOne("myapp", "my-profile", "lbl");

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources().size()).isEqualTo(4);
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:myapp,my-profile,lbl");
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("pro-foo", "pro-bar"));
		assertThat(e.getPropertySources().get(1).getName()).isEqualTo("vault:application,my-profile,lbl");
		assertThat(e.getPropertySources().get(1).getSource()).isEqualTo(Map.of("def-pro-foo", "def-pro-bar"));
		assertThat(e.getPropertySources().get(2).getName()).isEqualTo("vault:myapp,lbl");
		assertThat(e.getPropertySources().get(2).getSource()).isEqualTo(Map.of("foo", "bar"));
		assertThat(e.getPropertySources().get(3).getName()).isEqualTo("vault:application,lbl");
		assertThat(e.getPropertySources().get(3).getSource()).isEqualTo(Map.of("def-foo", "def-bar"));
	}

	@Test
	public void testFindOneWithMultipleProfiles() {
		stubRestTemplate("secret/myapp,master", toEntityResponse("foo", "bar"));
		stubRestTemplate("secret/myapp,pr1,master", toEntityResponse("pr1-foo", "pr1-bar"));
		stubRestTemplate("secret/myapp,pr2,master", toEntityResponse("pr2-foo", "pr2-bar"));
		stubRestTemplate("secret/application,master", toEntityResponse("def-foo", "def-bar"));
		stubRestTemplate("secret/application,pr1,master", toEntityResponse("def-pr1-foo", "def-pr1-bar"));
		stubRestTemplate("secret/application,pr2,master", toEntityResponse("def-pr2-foo", "def-pr2-bar"));

		var e = vaultEnvironmentRepository().findOne("myapp", "pr1,pr2", null);

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources().size()).isEqualTo(6);
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:myapp,pr2,master");
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("pr2-foo", "pr2-bar"));
		assertThat(e.getPropertySources().get(1).getName()).isEqualTo("vault:application,pr2,master");
		assertThat(e.getPropertySources().get(1).getSource()).isEqualTo(Map.of("def-pr2-foo", "def-pr2-bar"));
		assertThat(e.getPropertySources().get(2).getName()).isEqualTo("vault:myapp,pr1,master");
		assertThat(e.getPropertySources().get(2).getSource()).isEqualTo(Map.of("pr1-foo", "pr1-bar"));
		assertThat(e.getPropertySources().get(3).getName()).isEqualTo("vault:application,pr1,master");
		assertThat(e.getPropertySources().get(3).getSource()).isEqualTo(Map.of("def-pr1-foo", "def-pr1-bar"));
		assertThat(e.getPropertySources().get(4).getName()).isEqualTo("vault:myapp,master");
		assertThat(e.getPropertySources().get(4).getSource()).isEqualTo(Map.of("foo", "bar"));
		assertThat(e.getPropertySources().get(5).getName()).isEqualTo("vault:application,master");
		assertThat(e.getPropertySources().get(5).getSource()).isEqualTo(Map.of("def-foo", "def-bar"));
	}

	@Test
	public void missingConfigToken() {
		ConfigTokenProvider nullTokenProvider = () -> null;

		Assertions.assertThatThrownBy(() -> vaultEnvironmentRepository(nullTokenProvider).findOne("myapp", null, null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void testVaultVersioning() {
		stubRestTemplate("secret/data/myapp,main", toEntityResponse("data", Map.of("foo", "bar")));
		stubRestTemplate("secret/data/application,main", toEntityResponse("data", Map.of("def-foo", "def-bar")));

		var properties = new VaultEnvironmentProperties();
		properties.setKvVersion(2);

		var e = vaultEnvironmentRepository(properties).findOne("myapp", null, "main");

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources().size()).isEqualTo(2);
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:myapp,main");
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo", "bar"));
		assertThat(e.getPropertySources().get(1).getName()).isEqualTo("vault:application,main");
		assertThat(e.getPropertySources().get(1).getSource()).isEqualTo(Map.of("def-foo", "def-bar"));

		assertThat(requestHeaderCaptor.getValue().getHeaders()).containsEntry("X-Vault-Token", List.of("token"));
	}

	@Test
	public void testVaultKV2WithPath2Key() {
		stubRestTemplate("secret/data/myorg/myapp,lbl", toEntityResponse("data", Map.of("foo", "bar")));
		stubRestTemplate("secret/data/myorg/application,lbl", toEntityResponse("data", Map.of("def-foo", "def-bar")));

		var properties = new VaultEnvironmentProperties();
		properties.setKvVersion(2);
		properties.setPathToKey("myorg");

		var e = vaultEnvironmentRepository(properties).findOne("myapp", null, "lbl");

		assertThat(e.getName()).isEqualTo("myapp");

		assertThat(e.getPropertySources().size()).isEqualTo(2);
		assertThat(e.getPropertySources().get(0).getName()).isEqualTo("vault:myapp,lbl");
		assertThat(e.getPropertySources().get(0).getSource()).isEqualTo(Map.of("foo", "bar"));
		assertThat(e.getPropertySources().get(1).getName()).isEqualTo("vault:application,lbl");
		assertThat(e.getPropertySources().get(1).getSource()).isEqualTo(Map.of("def-foo", "def-bar"));
	}

	@Test
	public void testNamespaceHeaderSent() {
		stubRestTemplate("secret/myapp,lbl", toEntityResponse("foo", "bar"));
		stubRestTemplate("secret/application,lbl", toEntityResponse("def-foo", "def-bar"));

		var properties = new VaultEnvironmentProperties();
		properties.setNamespace("mynamespace");

		vaultEnvironmentRepository(properties).findOne("myapp", null, "lbl");

		assertThat(requestHeaderCaptor.getValue().getHeaders()).containsEntry("X-Vault-Namespace",
				List.of("mynamespace"));
		assertThat(requestHeaderCaptor.getValue().getHeaders()).containsEntry("X-Vault-Token", List.of("token"));
	}

	private VaultEnvironmentRepository vaultEnvironmentRepository() {
		return vaultEnvironmentRepository(new VaultEnvironmentProperties());
	}

	private VaultEnvironmentRepository vaultEnvironmentRepository(ConfigTokenProvider tokenProvider) {
		return vaultEnvironmentRepository(new VaultEnvironmentProperties(), tokenProvider);
	}

	private VaultEnvironmentRepository vaultEnvironmentRepository(VaultEnvironmentProperties properties) {
		return vaultEnvironmentRepository(properties, () -> "token");
	}

	private VaultEnvironmentRepository vaultEnvironmentRepository(VaultEnvironmentProperties properties,
			ConfigTokenProvider tokenProvider) {
		return new VaultEnvironmentRepository(mockHttpRequest(), new EnvironmentWatch.Default(), rest, properties,
				tokenProvider);
	}

	private void stubRestTemplate(String path, ResponseEntity<VaultResponse> response) {
		var p = Path.of(path);
		var url = "http://127.0.0.1:8200/v1/%s/{key}".formatted(p.getParent());
		var key = p.getFileName().toString();
		when(rest.exchange(eq(url), eq(HttpMethod.GET), requestHeaderCaptor.capture(), eq(VaultResponse.class),
				eq(key)))
			.thenReturn(response);
	}

	private ResponseEntity<VaultResponse> toEntityResponse(String key, Object value) {
		return toEntityResponse(Map.of(key, value));
	}

	private ResponseEntity<VaultResponse> toEntityResponse(Map<String, Object> body) {
		var response = new VaultResponse();
		response.setData(objectMapper.valueToTree(body));

		return ResponseEntity.ok(response);
	}

	@SuppressWarnings("unchecked")
	private ObjectProvider<HttpServletRequest> mockHttpRequest() {
		var objectProvider = mock(ObjectProvider.class);
		when(objectProvider.getIfAvailable()).thenReturn(null);
		return objectProvider;
	}

}
