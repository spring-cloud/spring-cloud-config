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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.environment.VaultKvAccessStrategy.VaultResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
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
public class VaultEnvironmentRepositoryTests {

	private ObjectMapper objectMapper;

	@Before
	public void init() {
		this.objectMapper = new ObjectMapper();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFindOneNoDefaultKey() {
		RestTemplate rest = mock(RestTemplate.class);

		ResponseEntity<VaultResponse> myAppResp = mock(ResponseEntity.class);
		when(myAppResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultResponse myAppVaultResp = mock(VaultResponse.class);
		when(myAppVaultResp.getData()).thenReturn("{\"foo\":\"bar\"}");
		when(myAppResp.getBody()).thenReturn(myAppVaultResp);
		when(rest.exchange(eq("http://127.0.0.1:8200/v1/secret/{key}"),
				eq(HttpMethod.GET), any(HttpEntity.class), eq(VaultResponse.class),
				eq("myapp"))).thenReturn(myAppResp);

		ResponseEntity<VaultResponse> appResp = mock(ResponseEntity.class);
		when(appResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultResponse appVaultResp = mock(VaultResponse.class);
		when(appVaultResp.getData()).thenReturn("{\"def-foo\":\"def-bar\"}");
		when(appResp.getBody()).thenReturn(appVaultResp);
		when(rest.exchange(eq("http://127.0.0.1:8200/v1/secret/{key}"),
				eq(HttpMethod.GET), any(HttpEntity.class), eq(VaultResponse.class),
				eq("application"))).thenReturn(appResp);

		VaultEnvironmentRepository repo = new VaultEnvironmentRepository(
				mockHttpRequest(), new EnvironmentWatch.Default(), rest,
				new VaultEnvironmentProperties(), mockTokenProvider());

		Environment e = repo.findOne("myapp", null, null);
		assertThat(e.getName()).as("Name should be the same as the application argument")
				.isEqualTo("myapp");
		assertThat(e.getPropertySources().size()).as(
				"Properties for specified application and default application with key 'application' should be returned")
				.isEqualTo(2);
		Map<String, String> firstResult = new HashMap<>();
		firstResult.put("foo", "bar");
		assertThat(e.getPropertySources().get(0).getSource()).as(
				"Properties for specified application should be returned in priority position")
				.isEqualTo(firstResult);

		Map<String, String> secondResult = new HashMap<>();
		secondResult.put("def-foo", "def-bar");
		assertThat(e.getPropertySources().get(1).getSource()).as(
				"Properties for default application with key 'application' should be returned in second position")
				.isEqualTo(secondResult);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testBackendWithSlashes() {
		RestTemplate rest = mock(RestTemplate.class);

		ResponseEntity<VaultResponse> myAppResp = mock(ResponseEntity.class);
		when(myAppResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultResponse myAppVaultResp = mock(VaultResponse.class);
		when(myAppVaultResp.getData()).thenReturn("{\"foo\":\"bar\"}");
		when(myAppResp.getBody()).thenReturn(myAppVaultResp);
		when(rest.exchange(eq("http://127.0.0.1:8200/v1/foo/bar/secret/{key}"),
				eq(HttpMethod.GET), any(HttpEntity.class), eq(VaultResponse.class),
				eq("myapp"))).thenReturn(myAppResp);

		ResponseEntity<VaultResponse> appResp = mock(ResponseEntity.class);
		when(appResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultResponse appVaultResp = mock(VaultResponse.class);
		when(appVaultResp.getData()).thenReturn("{\"def-foo\":\"def-bar\"}");
		when(appResp.getBody()).thenReturn(appVaultResp);
		when(rest.exchange(eq("http://127.0.0.1:8200/v1/foo/bar/secret/{key}"),
				eq(HttpMethod.GET), any(HttpEntity.class), eq(VaultResponse.class),
				eq("application"))).thenReturn(appResp);

		VaultEnvironmentProperties properties = new VaultEnvironmentProperties();
		properties.setBackend("foo/bar/secret");

		VaultEnvironmentRepository repo = new VaultEnvironmentRepository(
				mockHttpRequest(), new EnvironmentWatch.Default(), rest, properties,
				mockTokenProvider());

		Environment e = repo.findOne("myapp", null, null);
		assertThat(e.getName()).as("Name should be the same as the application argument")
				.isEqualTo("myapp");
		assertThat(e.getPropertySources().size()).as(
				"Properties for specified application and default application with key 'application' should be returned")
				.isEqualTo(2);
		Map<String, String> firstResult = new HashMap<>();
		firstResult.put("foo", "bar");
		assertThat(e.getPropertySources().get(0).getSource()).as(
				"Properties for specified application should be returned in priority position")
				.isEqualTo(firstResult);

		Map<String, String> secondResult = new HashMap<>();
		secondResult.put("def-foo", "def-bar");
		assertThat(e.getPropertySources().get(1).getSource()).as(
				"Properties for default application with key 'application' should be returned in second position")
				.isEqualTo(secondResult);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFindOneDefaultKeySetAndDifferentToApplication() {
		RestTemplate rest = mock(RestTemplate.class);

		ResponseEntity<VaultResponse> myAppResp = mock(ResponseEntity.class);
		when(myAppResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultResponse myAppVaultResp = mock(VaultResponse.class);
		when(myAppVaultResp.getData()).thenReturn("{\"foo\":\"bar\"}");
		when(myAppResp.getBody()).thenReturn(myAppVaultResp);
		when(rest.exchange(eq("http://127.0.0.1:8200/v1/secret/{key}"),
				eq(HttpMethod.GET), any(HttpEntity.class), eq(VaultResponse.class),
				eq("myapp"))).thenReturn(myAppResp);

		ResponseEntity<VaultResponse> myDefaultKeyResp = mock(ResponseEntity.class);
		when(myDefaultKeyResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultResponse myDefaultKeyVaultResp = mock(VaultResponse.class);
		when(myDefaultKeyVaultResp.getData()).thenReturn("{\"def-foo\":\"def-bar\"}");
		when(myDefaultKeyResp.getBody()).thenReturn(myDefaultKeyVaultResp);
		when(rest.exchange(eq("http://127.0.0.1:8200/v1/secret/{key}"),
				eq(HttpMethod.GET), any(HttpEntity.class), eq(VaultResponse.class),
				eq("mydefaultkey"))).thenReturn(myDefaultKeyResp);

		VaultEnvironmentRepository repo = new VaultEnvironmentRepository(
				mockHttpRequest(), new EnvironmentWatch.Default(), rest,
				new VaultEnvironmentProperties(), mockTokenProvider());
		repo.setDefaultKey("mydefaultkey");

		Environment e = repo.findOne("myapp", null, null);
		assertThat(e.getName()).as("Name should be the same as the application argument")
				.isEqualTo("myapp");
		assertThat(e.getPropertySources().size()).as(
				"Properties for specified application and default application with key 'mydefaultkey' should be returned")
				.isEqualTo(2);

		Map<String, String> firstResult = new HashMap<>();
		firstResult.put("foo", "bar");
		assertThat(e.getPropertySources().get(0).getSource()).as(
				"Properties for specified application should be returned in priority position")
				.isEqualTo(firstResult);

		Map<String, String> secondResult = new HashMap<>();
		secondResult.put("def-foo", "def-bar");
		assertThat(e.getPropertySources().get(1).getSource()).as(
				"Properties for default application with key 'mydefaultkey' should be returned in second position")
				.isEqualTo(secondResult);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFindOneDefaultKeySetAndDifferentToMultipleApplications() {
		RestTemplate rest = mock(RestTemplate.class);

		ResponseEntity<VaultResponse> myAppResp = mock(ResponseEntity.class);
		when(myAppResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultResponse myAppVaultResp = mock(VaultResponse.class);
		when(myAppVaultResp.getData()).thenReturn("{\"myapp-foo\":\"myapp-bar\"}");
		when(myAppResp.getBody()).thenReturn(myAppVaultResp);
		when(rest.exchange(eq("http://127.0.0.1:8200/v1/secret/{key}"),
				eq(HttpMethod.GET), any(HttpEntity.class), eq(VaultResponse.class),
				eq("myapp"))).thenReturn(myAppResp);

		ResponseEntity<VaultResponse> yourAppResp = mock(ResponseEntity.class);
		when(yourAppResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultResponse yourAppVaultResp = mock(VaultResponse.class);
		when(yourAppVaultResp.getData()).thenReturn("{\"yourapp-foo\":\"yourapp-bar\"}");
		when(yourAppResp.getBody()).thenReturn(yourAppVaultResp);
		when(rest.exchange(eq("http://127.0.0.1:8200/v1/secret/{key}"),
				eq(HttpMethod.GET), any(HttpEntity.class), eq(VaultResponse.class),
				eq("yourapp"))).thenReturn(yourAppResp);

		ResponseEntity<VaultResponse> myDefaultKeyResp = mock(ResponseEntity.class);
		when(myDefaultKeyResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultResponse myDefaultKeyVaultResp = mock(VaultResponse.class);
		when(myDefaultKeyVaultResp.getData()).thenReturn("{\"def-foo\":\"def-bar\"}");
		when(myDefaultKeyResp.getBody()).thenReturn(myDefaultKeyVaultResp);
		when(rest.exchange(eq("http://127.0.0.1:8200/v1/secret/{key}"),
				eq(HttpMethod.GET), any(HttpEntity.class), eq(VaultResponse.class),
				eq("mydefaultkey"))).thenReturn(myDefaultKeyResp);

		VaultEnvironmentRepository repo = new VaultEnvironmentRepository(
				mockHttpRequest(), new EnvironmentWatch.Default(), rest,
				new VaultEnvironmentProperties(), mockTokenProvider());
		repo.setDefaultKey("mydefaultkey");

		Environment e = repo.findOne("myapp,yourapp", null, null);
		assertThat(e.getName()).as("Name should be the same as the application argument")
				.isEqualTo("myapp,yourapp");
		assertThat(e.getPropertySources().size()).as(
				"Properties for specified applications and default application with key 'mydefaultkey' should be returned")
				.isEqualTo(3);

		Map<String, String> firstResult = new HashMap<>();
		firstResult.put("yourapp-foo", "yourapp-bar");
		assertThat(e.getPropertySources().get(0).getSource()).as(
				"Properties for first specified application should be returned in priority position")
				.isEqualTo(firstResult);

		Map<String, String> secondResult = new HashMap<>();
		secondResult.put("myapp-foo", "myapp-bar");
		assertThat(e.getPropertySources().get(1).getSource()).as(
				"Properties for second specified application should be returned in priority position")
				.isEqualTo(secondResult);

		Map<String, String> thirdResult = new HashMap<>();
		thirdResult.put("def-foo", "def-bar");
		assertThat(e.getPropertySources().get(2).getSource()).as(
				"Properties for default application with key 'mydefaultkey' should be returned in second position")
				.isEqualTo(thirdResult);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFindOneDefaultKeySetAndEqualToApplication() {
		RestTemplate rest = mock(RestTemplate.class);

		ResponseEntity<VaultResponse> myAppResp = mock(ResponseEntity.class);
		when(myAppResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultResponse myAppVaultResp = mock(VaultResponse.class);
		when(myAppVaultResp.getData()).thenReturn("{\"foo\":\"bar\"}");
		when(myAppResp.getBody()).thenReturn(myAppVaultResp);
		when(rest.exchange(eq("http://127.0.0.1:8200/v1/secret/{key}"),
				eq(HttpMethod.GET), any(HttpEntity.class), eq(VaultResponse.class),
				eq("myapp"))).thenReturn(myAppResp);

		ResponseEntity<VaultResponse> appResp = mock(ResponseEntity.class);
		when(appResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultResponse appVaultResp = mock(VaultResponse.class);
		when(appVaultResp.getData()).thenReturn("{\"def-foo\":\"def-bar\"}");
		when(appResp.getBody()).thenReturn(appVaultResp);
		when(rest.exchange(eq("http://127.0.0.1:8200/v1/secret/{key}"),
				eq(HttpMethod.GET), any(HttpEntity.class), eq(VaultResponse.class),
				eq("application"))).thenReturn(appResp);

		VaultEnvironmentRepository repo = new VaultEnvironmentRepository(
				mockHttpRequest(), new EnvironmentWatch.Default(), rest,
				new VaultEnvironmentProperties(), mockTokenProvider());
		repo.setDefaultKey("myapp");

		Environment e = repo.findOne("myapp", null, null);
		assertThat(e.getName()).as("Name should be the same as the application argument")
				.isEqualTo("myapp");
		assertThat(e.getPropertySources().size())
				.as("Only properties for specified application should be returned")
				.isEqualTo(1);

		Map<String, String> result = new HashMap<>();
		result.put("foo", "bar");
		assertThat(e.getPropertySources().get(0).getSource())
				.as("Properties should be returned for specified application")
				.isEqualTo(result);
	}

	@Test(expected = IllegalArgumentException.class)
	public void missingConfigToken() {
		ConfigTokenProvider tokenProvider = mock(ConfigTokenProvider.class);
		when(tokenProvider.getToken()).thenReturn(null);

		VaultEnvironmentRepository repo = new VaultEnvironmentRepository(
				mockHttpRequest(), new EnvironmentWatch.Default(),
				mock(RestTemplate.class), new VaultEnvironmentProperties(),
				tokenProvider);
		repo.findOne("myapp", null, null);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testVaultVersioning() {
		RestTemplate rest = mock(RestTemplate.class);

		ResponseEntity<VaultResponse> myAppResp = mock(ResponseEntity.class);
		when(myAppResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultResponse myAppVaultResp = getVaultResponse(
				"{\"data\": {\"data\": {\"foo\": \"bar\"}}}");
		when(myAppResp.getBody()).thenReturn(myAppVaultResp);
		when(rest.exchange(eq("http://127.0.0.1:8200/v1/secret/data/{key}"),
				eq(HttpMethod.GET), any(HttpEntity.class), eq(VaultResponse.class),
				eq("myapp"))).thenReturn(myAppResp);

		ResponseEntity<VaultResponse> appResp = mock(ResponseEntity.class);
		when(appResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultResponse appVaultResp = getVaultResponse(
				"{\"data\": {\"data\": {\"def-foo\":\"def-bar\"}}}");
		when(appResp.getBody()).thenReturn(appVaultResp);
		when(rest.exchange(eq("http://127.0.0.1:8200/v1/secret/data/{key}"),
				eq(HttpMethod.GET), any(HttpEntity.class), eq(VaultResponse.class),
				eq("application"))).thenReturn(appResp);

		final VaultEnvironmentProperties vaultEnvironmentProperties = new VaultEnvironmentProperties();
		vaultEnvironmentProperties.setKvVersion(2);
		VaultEnvironmentRepository repo = new VaultEnvironmentRepository(
				mockHttpRequest(), new EnvironmentWatch.Default(), rest,
				vaultEnvironmentProperties, mockTokenProvider());

		Environment e = repo.findOne("myapp", null, null);
		assertThat(e.getName()).as("Name should be the same as the application argument")
				.isEqualTo("myapp");
		assertThat(e.getPropertySources().size()).as(
				"Properties for specified application and default application with key 'application' should be returned")
				.isEqualTo(2);
		Map<String, String> firstResult = new HashMap<>();
		firstResult.put("foo", "bar");
		assertThat(e.getPropertySources().get(0).getSource()).as(
				"Properties for specified application should be returned in priority position")
				.isEqualTo(firstResult);
	}

	@Test
	@SuppressWarnings({ "Duplicates", "unchecked" })
	public void testNamespaceHeaderSent() {
		RestTemplate rest = mock(RestTemplate.class);

		ResponseEntity<VaultResponse> myAppResp = mock(ResponseEntity.class);
		when(myAppResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultResponse myAppVaultResp = mock(VaultResponse.class);
		when(myAppVaultResp.getData()).thenReturn("{\"foo\":\"bar\"}");
		when(myAppResp.getBody()).thenReturn(myAppVaultResp);
		when(rest.exchange(eq("http://127.0.0.1:8200/v1/secret/{key}"),
				eq(HttpMethod.GET), any(HttpEntity.class), eq(VaultResponse.class),
				eq("myapp"))).thenReturn(myAppResp);

		ResponseEntity<VaultResponse> appResp = mock(ResponseEntity.class);
		when(appResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultResponse appVaultResp = mock(VaultResponse.class);
		when(appVaultResp.getData()).thenReturn("{\"def-foo\":\"def-bar\"}");
		when(appResp.getBody()).thenReturn(appVaultResp);
		when(rest.exchange(eq("http://127.0.0.1:8200/v1/secret/{key}"),
				eq(HttpMethod.GET), any(HttpEntity.class), eq(VaultResponse.class),
				eq("application"))).thenReturn(appResp);

		VaultEnvironmentProperties properties = new VaultEnvironmentProperties();
		properties.setNamespace("mynamespace");
		VaultEnvironmentRepository repo = new VaultEnvironmentRepository(
				mockHttpRequest(), new EnvironmentWatch.Default(), rest, properties,
				mockTokenProvider());

		TestAccessStrategy accessStrategy = new TestAccessStrategy(rest, properties);
		repo.setAccessStrategy(accessStrategy);

		repo.findOne("myapp", null, null);

		assertThat(accessStrategy.headers).containsEntry(
				VaultEnvironmentRepository.VAULT_NAMESPACE,
				Collections.singletonList("mynamespace"));
	}

	private VaultResponse getVaultResponse(String json) {
		try {
			return this.objectMapper.readValue(json, VaultResponse.class);
		}
		catch (Exception e) {
			fail(e.getMessage());
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private ObjectProvider<HttpServletRequest> mockHttpRequest() {
		ObjectProvider<HttpServletRequest> objectProvider = mock(ObjectProvider.class);
		when(objectProvider.getIfAvailable()).thenReturn(null);
		return objectProvider;
	}

	private ConfigTokenProvider mockTokenProvider() {
		ConfigTokenProvider tokenProvider = mock(ConfigTokenProvider.class);
		when(tokenProvider.getToken()).thenReturn("token");
		return tokenProvider;
	}

	private static class TestAccessStrategy implements VaultKvAccessStrategy {

		private final VaultKvAccessStrategy accessStrategy;

		private HttpHeaders headers;

		TestAccessStrategy(RestTemplate restTemplate,
				VaultEnvironmentProperties properties) {
			String baseUrl = String.format("%s://%s:%s", properties.getScheme(),
					properties.getHost(), properties.getPort());
			this.accessStrategy = VaultKvAccessStrategyFactory.forVersion(restTemplate,
					baseUrl, properties.getKvVersion());
		}

		@Override
		public String getData(HttpHeaders headers, String backend, String key)
				throws RestClientException {
			this.headers = headers;
			return this.accessStrategy.getData(headers, backend, key);
		}

	}

}
