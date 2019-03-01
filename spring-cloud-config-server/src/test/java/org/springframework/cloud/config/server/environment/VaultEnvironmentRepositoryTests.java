/*
 * Copyright 2018-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import org.springframework.mock.web.MockHttpServletRequest;
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
 */
public class VaultEnvironmentRepositoryTests {

	private ObjectMapper objectMapper;

	@Before
	public void init() {
		this.objectMapper = new ObjectMapper();
	}

	@SuppressWarnings("unchecked")
	private ObjectProvider<HttpServletRequest> mockProvide(HttpServletRequest request) {
		ObjectProvider<HttpServletRequest> objectProvider = mock(ObjectProvider.class);
		when(objectProvider.getIfAvailable()).thenReturn(request);
		return objectProvider;
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFindOneNoDefaultKey() {
		MockHttpServletRequest configRequest = new MockHttpServletRequest();
		configRequest.addHeader("X-CONFIG-TOKEN", "mytoken");

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
				mockProvide(configRequest), new EnvironmentWatch.Default(), rest,
				new VaultEnvironmentProperties());

		Environment e = repo.findOne("myapp", null, null);
		assertThat(e.getName()).as("Name should be the same as the application argument")
				.isEqualTo("myapp");
		assertThat(e.getPropertySources().size()).as(
				"Properties for specified application and default application with key 'application' should be returned")
				.isEqualTo(2);
		Map<String, String> firstResult = new HashMap<String, String>();
		firstResult.put("foo", "bar");
		assertThat(e.getPropertySources().get(0).getSource()).as(
				"Properties for specified application should be returned in priority position")
				.isEqualTo(firstResult);

		Map<String, String> secondResult = new HashMap<String, String>();
		secondResult.put("def-foo", "def-bar");
		assertThat(e.getPropertySources().get(1).getSource()).as(
				"Properties for default application with key 'application' should be returned in second position")
				.isEqualTo(secondResult);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testBackendWithSlashes() {
		MockHttpServletRequest configRequest = new MockHttpServletRequest();
		configRequest.addHeader("X-CONFIG-TOKEN", "mytoken");

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
				mockProvide(configRequest), new EnvironmentWatch.Default(), rest,
				properties);

		Environment e = repo.findOne("myapp", null, null);
		assertThat(e.getName()).as("Name should be the same as the application argument")
				.isEqualTo("myapp");
		assertThat(e.getPropertySources().size()).as(
				"Properties for specified application and default application with key 'application' should be returned")
				.isEqualTo(2);
		Map<String, String> firstResult = new HashMap<String, String>();
		firstResult.put("foo", "bar");
		assertThat(e.getPropertySources().get(0).getSource()).as(
				"Properties for specified application should be returned in priority position")
				.isEqualTo(firstResult);

		Map<String, String> secondResult = new HashMap<String, String>();
		secondResult.put("def-foo", "def-bar");
		assertThat(e.getPropertySources().get(1).getSource()).as(
				"Properties for default application with key 'application' should be returned in second position")
				.isEqualTo(secondResult);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFindOneDefaultKeySetAndDifferentToApplication() {
		MockHttpServletRequest configRequest = new MockHttpServletRequest();
		configRequest.addHeader("X-CONFIG-TOKEN", "mytoken");
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
				mockProvide(configRequest), new EnvironmentWatch.Default(), rest,
				new VaultEnvironmentProperties());
		repo.setDefaultKey("mydefaultkey");

		Environment e = repo.findOne("myapp", null, null);
		assertThat(e.getName()).as("Name should be the same as the application argument")
				.isEqualTo("myapp");
		assertThat(e.getPropertySources().size()).as(
				"Properties for specified application and default application with key 'mydefaultkey' should be returned")
				.isEqualTo(2);

		Map<String, String> firstResult = new HashMap<String, String>();
		firstResult.put("foo", "bar");
		assertThat(e.getPropertySources().get(0).getSource()).as(
				"Properties for specified application should be returned in priority position")
				.isEqualTo(firstResult);

		Map<String, String> secondResult = new HashMap<String, String>();
		secondResult.put("def-foo", "def-bar");
		assertThat(e.getPropertySources().get(1).getSource()).as(
				"Properties for default application with key 'mydefaultkey' should be returned in second position")
				.isEqualTo(secondResult);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFindOneDefaultKeySetAndEqualToApplication() {
		MockHttpServletRequest configRequest = new MockHttpServletRequest();
		configRequest.addHeader("X-CONFIG-TOKEN", "mytoken");
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
				mockProvide(configRequest), new EnvironmentWatch.Default(), rest,
				new VaultEnvironmentProperties());
		repo.setDefaultKey("myapp");

		Environment e = repo.findOne("myapp", null, null);
		assertThat(e.getName()).as("Name should be the same as the application argument")
				.isEqualTo("myapp");
		assertThat(e.getPropertySources().size())
				.as("Only properties for specified application should be returned")
				.isEqualTo(1);

		Map<String, String> result = new HashMap<String, String>();
		result.put("foo", "bar");
		assertThat(e.getPropertySources().get(0).getSource())
				.as("Properties should be returned for specified application")
				.isEqualTo(result);
	}

	@Test(expected = IllegalArgumentException.class)
	@SuppressWarnings("unchecked")
	public void missingConfigToken() {
		MockHttpServletRequest configRequest = new MockHttpServletRequest();
		RestTemplate rest = mock(RestTemplate.class);
		ResponseEntity<VaultResponse> myAppResp = mock(ResponseEntity.class);
		when(myAppResp.getStatusCode()).thenReturn(HttpStatus.OK);
		VaultResponse myAppVaultResp = mock(VaultResponse.class);
		when(myAppVaultResp.getData()).thenReturn("{\"foo\":\"bar\"}");
		when(myAppResp.getBody()).thenReturn(myAppVaultResp);
		when(rest.exchange(eq("http://127.0.0.1:8200/v1/secret/{key}"),
				eq(HttpMethod.GET), any(HttpEntity.class), eq(VaultResponse.class),
				eq("myapp"))).thenReturn(myAppResp);
		VaultEnvironmentRepository repo = new VaultEnvironmentRepository(
				mockProvide(configRequest), new EnvironmentWatch.Default(), rest,
				new VaultEnvironmentProperties());
		repo.findOne("myapp", null, null);
	}

	@Test(expected = IllegalStateException.class)
	@SuppressWarnings("unchecked")
	public void missingHttpRequest() {
		ObjectProvider<HttpServletRequest> objectProvider = mock(ObjectProvider.class);
		when(objectProvider.getIfAvailable()).thenReturn(null);

		RestTemplate rest = mock(RestTemplate.class);
		VaultEnvironmentRepository repo = new VaultEnvironmentRepository(objectProvider,
				new EnvironmentWatch.Default(), rest, new VaultEnvironmentProperties());
		repo.findOne("myapp", null, null);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testVaultVersioning() {
		MockHttpServletRequest configRequest = new MockHttpServletRequest();
		configRequest.addHeader("X-CONFIG-TOKEN", "mytoken");

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
				mockProvide(configRequest), new EnvironmentWatch.Default(), rest,
				vaultEnvironmentProperties);

		Environment e = repo.findOne("myapp", null, null);
		assertThat(e.getName()).as("Name should be the same as the application argument")
				.isEqualTo("myapp");
		assertThat(e.getPropertySources().size()).as(
				"Properties for specified application and default application with key 'application' should be returned")
				.isEqualTo(2);
		Map<String, String> firstResult = new HashMap<String, String>();
		firstResult.put("foo", "bar");
		assertThat(e.getPropertySources().get(0).getSource()).as(
				"Properties for specified application should be returned in priority position")
				.isEqualTo(firstResult);
	}

	@Test
	@SuppressWarnings({ "Duplicates", "unchecked" })
	public void testNamespaceHeaderSent() {
		MockHttpServletRequest configRequest = new MockHttpServletRequest();
		configRequest.addHeader("X-CONFIG-TOKEN", "mytoken");

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
				mockProvide(configRequest), new EnvironmentWatch.Default(), rest,
				properties);

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
