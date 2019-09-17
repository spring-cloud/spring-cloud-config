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

package org.springframework.cloud.config.client;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.hamcrest.core.IsInstanceOf;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.Mockito;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator.GenericRequestHeaderInterceptor;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.cloud.config.client.ConfigClientProperties.AUTHORIZATION;
import static org.springframework.cloud.config.environment.EnvironmentMediaType.V2_JSON;

public class ConfigServicePropertySourceLocatorTests {

	@Rule
	public ExpectedException expected = ExpectedException.none();

	private ConfigurableEnvironment environment = new StandardEnvironment();

	private ConfigServicePropertySourceLocator locator = new ConfigServicePropertySourceLocator(
			new ConfigClientProperties(this.environment));

	private RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

	@Test
	public void sunnyDay() {
		Environment body = new Environment("app", "master");
		mockRequestResponseWithoutLabel(new ResponseEntity<>(body, HttpStatus.OK));
		this.locator.setRestTemplate(this.restTemplate);

		ArgumentCaptor<HttpEntity> argumentCaptor = ArgumentCaptor
				.forClass(HttpEntity.class);

		assertThat(this.locator.locate(this.environment)).isNotNull();

		Mockito.verify(this.restTemplate).exchange(anyString(), any(HttpMethod.class),
				argumentCaptor.capture(), any(Class.class), anyString(), anyString());

		HttpEntity httpEntity = argumentCaptor.getValue();
		assertThat(httpEntity.getHeaders().getAccept())
				.containsExactly(MediaType.parseMediaType(V2_JSON));
	}

	@Test
	public void sunnyDayWithLabel() {
		Environment body = new Environment("app", "master");
		mockRequestResponseWithLabel(new ResponseEntity<>(body, HttpStatus.OK), "v1.0.0");
		this.locator.setRestTemplate(this.restTemplate);
		TestPropertyValues.of("spring.cloud.config.label:v1.0.0")
				.applyTo(this.environment);
		assertThat(this.locator.locate(this.environment)).isNotNull();
	}

	@Test
	public void sunnyDayWithLabelThatContainsASlash() {
		Environment body = new Environment("app", "master");
		mockRequestResponseWithLabel(new ResponseEntity<>(body, HttpStatus.OK),
				"release(_)v1.0.0");
		this.locator.setRestTemplate(this.restTemplate);
		TestPropertyValues.of("spring.cloud.config.label:release/v1.0.0")
				.applyTo(this.environment);
		assertThat(this.locator.locate(this.environment)).isNotNull();
	}

	@Test
	public void sunnyDayWithNoSuchLabel() {
		mockRequestResponseWithLabel(
				new ResponseEntity<>((Void) null, HttpStatus.NOT_FOUND), "nosuchlabel");
		this.locator.setRestTemplate(this.restTemplate);
		assertThat(this.locator.locate(this.environment)).isNull();
	}

	@Test
	public void sunnyDayWithNoSuchLabelAndFailFast() {
		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		defaults.setFailFast(true);
		this.locator = new ConfigServicePropertySourceLocator(defaults);
		mockRequestResponseWithLabel(
				new ResponseEntity<>((Void) null, HttpStatus.NOT_FOUND),
				"release(_)v1.0.0");
		this.locator.setRestTemplate(this.restTemplate);
		TestPropertyValues.of("spring.cloud.config.label:release/v1.0.1")
				.applyTo(this.environment);
		this.expected.expect(IsInstanceOf.instanceOf(IllegalStateException.class));
		this.expected.expectMessage(
				"Could not locate PropertySource and the fail fast property is set, failing: None of labels [release/v1.0.1] found");
		this.locator.locate(this.environment);
	}

	@Test
	public void failsQuietly() {
		mockRequestResponseWithoutLabel(
				new ResponseEntity<>("Wah!", HttpStatus.INTERNAL_SERVER_ERROR));
		this.locator.setRestTemplate(this.restTemplate);
		assertThat(this.locator.locate(this.environment)).isNull();
	}

	@Test
	public void failFast() throws Exception {
		ClientHttpRequestFactory requestFactory = Mockito
				.mock(ClientHttpRequestFactory.class);
		ClientHttpRequest request = Mockito.mock(ClientHttpRequest.class);
		ClientHttpResponse response = Mockito.mock(ClientHttpResponse.class);
		Mockito.when(requestFactory.createRequest(Mockito.any(URI.class),
				Mockito.any(HttpMethod.class))).thenReturn(request);
		RestTemplate restTemplate = new RestTemplate(requestFactory);
		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		defaults.setFailFast(true);
		this.locator = new ConfigServicePropertySourceLocator(defaults);
		Mockito.when(request.getHeaders()).thenReturn(new HttpHeaders());
		Mockito.when(request.execute()).thenReturn(response);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		Mockito.when(response.getHeaders()).thenReturn(headers);
		Mockito.when(response.getStatusCode())
				.thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
		Mockito.when(response.getBody())
				.thenReturn(new ByteArrayInputStream("{}".getBytes()));
		this.locator.setRestTemplate(restTemplate);
		this.expected
				.expectCause(IsInstanceOf.instanceOf(IllegalArgumentException.class));
		this.expected.expectMessage("fail fast property is set");
		this.locator.locate(this.environment);
	}

	@Test
	public void failFastWhenNotFound() throws Exception {
		ClientHttpRequestFactory requestFactory = Mockito
				.mock(ClientHttpRequestFactory.class);
		ClientHttpRequest request = Mockito.mock(ClientHttpRequest.class);
		ClientHttpResponse response = Mockito.mock(ClientHttpResponse.class);
		Mockito.when(requestFactory.createRequest(Mockito.any(URI.class),
				Mockito.any(HttpMethod.class))).thenReturn(request);
		RestTemplate restTemplate = new RestTemplate(requestFactory);
		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		defaults.setFailFast(true);
		this.locator = new ConfigServicePropertySourceLocator(defaults);
		Mockito.when(request.getHeaders()).thenReturn(new HttpHeaders());
		Mockito.when(request.execute()).thenReturn(response);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		Mockito.when(response.getHeaders()).thenReturn(headers);
		Mockito.when(response.getStatusCode()).thenReturn(HttpStatus.NOT_FOUND);
		Mockito.when(response.getBody())
				.thenReturn(new ByteArrayInputStream("".getBytes()));
		this.locator.setRestTemplate(restTemplate);
		this.expected
				.expectCause(IsInstanceOf.instanceOf(IllegalArgumentException.class));
		this.expected.expectMessage("fail fast property is set");
		this.locator.locate(this.environment);
	}

	@Test
	public void failFastWhenBothPasswordAndAuthorizationPropertiesSet() throws Exception {
		ClientHttpRequestFactory requestFactory = Mockito
				.mock(ClientHttpRequestFactory.class);
		ClientHttpRequest request = Mockito.mock(ClientHttpRequest.class);
		Mockito.when(requestFactory.createRequest(Mockito.any(URI.class),
				Mockito.any(HttpMethod.class))).thenReturn(request);
		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		defaults.setFailFast(true);
		defaults.setUsername("username");
		defaults.setPassword("password");
		defaults.getHeaders().put(AUTHORIZATION, "Basic dXNlcm5hbWU6cGFzc3dvcmQNCg==");
		this.locator = new ConfigServicePropertySourceLocator(defaults);
		this.expected.expect(IllegalStateException.class);
		this.expected.expectMessage(
				"Could not locate PropertySource and the fail fast property is set, failing");
		this.locator.locate(this.environment);
	}

	@Test
	public void interceptorShouldAddHeadersWhenHeadersPropertySet() throws Exception {
		MockClientHttpRequest request = new MockClientHttpRequest();
		ClientHttpRequestExecution execution = Mockito
				.mock(ClientHttpRequestExecution.class);
		byte[] body = new byte[] {};
		Map<String, String> headers = new HashMap<>();
		headers.put("X-Example-Version", "2.1");
		new ConfigServicePropertySourceLocator.GenericRequestHeaderInterceptor(headers)
				.intercept(request, body, execution);
		Mockito.verify(execution).execute(request, body);
		assertThat(request.getHeaders().getFirst("X-Example-Version")).isEqualTo("2.1");
	}

	@Test
	public void shouldAddAuthorizationHeaderWhenPasswordSet() {
		HttpHeaders headers = new HttpHeaders();
		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		this.locator = new ConfigServicePropertySourceLocator(defaults);
		String username = "user";
		String password = "pass";
		ReflectionTestUtils.invokeMethod(this.locator, "addAuthorizationToken", defaults,
				headers, username, password);
		assertThat(headers).hasSize(1);
	}

	@Test
	public void shouldAddAuthorizationHeaderWhenAuthorizationSet() {
		HttpHeaders headers = new HttpHeaders();
		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		defaults.getHeaders().put(AUTHORIZATION, "Basic dXNlcm5hbWU6cGFzc3dvcmQNCg==");
		this.locator = new ConfigServicePropertySourceLocator(defaults);
		String username = "user";
		String password = null;
		ReflectionTestUtils.invokeMethod(this.locator, "addAuthorizationToken", defaults,
				headers, username, password);
		assertThat(headers).hasSize(1);
	}

	@Test
	public void shouldThrowExceptionWhenPasswordAndAuthorizationBothSet() {
		HttpHeaders headers = new HttpHeaders();
		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		defaults.getHeaders().put(AUTHORIZATION, "Basic dXNlcm5hbWU6cGFzc3dvcmQNCg==");
		this.locator = new ConfigServicePropertySourceLocator(defaults);
		String username = "user";
		String password = "pass";
		this.expected.expect(IllegalStateException.class);
		this.expected.expectMessage("You must set either 'password' or 'authorization'");
		ReflectionTestUtils.invokeMethod(this.locator, "addAuthorizationToken", defaults,
				headers, username, password);
	}

	@Test
	public void shouldThrowExceptionWhenNegativeReadTimeoutSet() {
		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		defaults.setRequestReadTimeout(-1);
		this.locator = new ConfigServicePropertySourceLocator(defaults);
		this.expected.expect(IllegalStateException.class);
		this.expected.expectMessage("Invalid Value for Read Timeout set.");
		ReflectionTestUtils.invokeMethod(this.locator, "getSecureRestTemplate", defaults);
	}

	@Test
	public void shouldThrowExceptionWhenNegativeConnectTimeoutSet() {
		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		defaults.setRequestConnectTimeout(-1);
		this.locator = new ConfigServicePropertySourceLocator(defaults);
		this.expected.expect(IllegalStateException.class);
		this.expected.expectMessage("Invalid Value for Connect Timeout set.");
		ReflectionTestUtils.invokeMethod(this.locator, "getSecureRestTemplate", defaults);
	}

	@Test
	public void checkInterceptorHasNoAuthorizationHeaderPresent() {
		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		defaults.getHeaders().put(AUTHORIZATION, "Basic dXNlcm5hbWU6cGFzc3dvcmQNCg==");
		defaults.getHeaders().put("key", "value");
		this.locator = new ConfigServicePropertySourceLocator(defaults);
		RestTemplate restTemplate = ReflectionTestUtils.invokeMethod(this.locator,
				"getSecureRestTemplate", defaults);
		Iterator<ClientHttpRequestInterceptor> iterator = restTemplate.getInterceptors()
				.iterator();
		while (iterator.hasNext()) {
			GenericRequestHeaderInterceptor genericRequestHeaderInterceptor = (GenericRequestHeaderInterceptor) iterator
					.next();
			assertThat(genericRequestHeaderInterceptor.getHeaders().get(AUTHORIZATION))
					.isEqualTo(null);
		}
	}

	@SuppressWarnings("unchecked")
	private void mockRequestResponseWithLabel(ResponseEntity<?> response, String label) {
		Mockito.when(this.restTemplate.exchange(Mockito.any(String.class),
				Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class),
				Mockito.any(Class.class), anyString(), anyString(), Matchers.eq(label)))
				.thenReturn(response);
	}

	@SuppressWarnings("unchecked")
	private void mockRequestResponseWithoutLabel(ResponseEntity<?> response) {
		Mockito.when(this.restTemplate.exchange(Mockito.any(String.class),
				Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class),
				Mockito.any(Class.class), anyString(), anyString())).thenReturn(response);
	}

	@SuppressWarnings("unchecked")
	private void mockRequestResponseWithoutLabelWithExpectedName(
			ResponseEntity<?> response, String expectedName) {
		Mockito.when(this.restTemplate.exchange(Mockito.any(String.class),
				Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class),
				Mockito.any(Class.class), Matchers.eq(expectedName), anyString()))
				.thenReturn(response);
	}

}
