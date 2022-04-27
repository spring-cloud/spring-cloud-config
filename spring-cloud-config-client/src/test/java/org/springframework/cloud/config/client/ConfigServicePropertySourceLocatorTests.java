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
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.LogFactory;
import org.hamcrest.core.IsInstanceOf;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.cloud.config.client.ConfigClientRequestTemplateFactory.GenericRequestHeaderInterceptor;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
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

		ArgumentCaptor<HttpEntity> argumentCaptor = ArgumentCaptor.forClass(HttpEntity.class);

		assertThat(this.locator.locateCollection(this.environment)).isNotNull();

		Mockito.verify(this.restTemplate).exchange(anyString(), any(HttpMethod.class), argumentCaptor.capture(),
				any(Class.class), anyString(), anyString());

		HttpEntity httpEntity = argumentCaptor.getValue();
		assertThat(httpEntity.getHeaders().getAccept()).containsExactly(MediaType.parseMediaType(V2_JSON));
	}

	@Test
	public void customMediaType() {
		Environment body = new Environment("app", "master");
		mockRequestResponseWithoutLabel(new ResponseEntity<>(body, HttpStatus.OK));
		ConfigClientProperties properties = new ConfigClientProperties(this.environment);
		properties.setMediaType("application/json");
		ConfigServicePropertySourceLocator locator = new ConfigServicePropertySourceLocator(properties);
		locator.setRestTemplate(this.restTemplate);

		ArgumentCaptor<HttpEntity> argumentCaptor = ArgumentCaptor.forClass(HttpEntity.class);

		assertThat(locator.locateCollection(this.environment)).isNotNull();

		Mockito.verify(this.restTemplate).exchange(anyString(), any(HttpMethod.class), argumentCaptor.capture(),
				any(Class.class), anyString(), anyString());

		HttpEntity httpEntity = argumentCaptor.getValue();
		assertThat(httpEntity.getHeaders().getAccept()).containsExactly(MediaType.parseMediaType("application/json"));
	}

	@Test
	public void sunnyDayWithLabel() {
		Environment body = new Environment("app", "master");
		mockRequestResponseWithLabel(new ResponseEntity<>(body, HttpStatus.OK), "v1.0.0");
		this.locator.setRestTemplate(this.restTemplate);
		TestPropertyValues.of("spring.cloud.config.label:v1.0.0").applyTo(this.environment);
		assertThat(this.locator.locateCollection(this.environment)).isNotNull();
	}

	@Test
	public void sunnyDayWithLabelThatContainsASlash() {
		Environment body = new Environment("app", "master");
		mockRequestResponseWithLabel(new ResponseEntity<>(body, HttpStatus.OK), "release(_)v1.0.0");
		this.locator.setRestTemplate(this.restTemplate);
		TestPropertyValues.of("spring.cloud.config.label:release/v1.0.0").applyTo(this.environment);
		assertThat(this.locator.locateCollection(this.environment)).isNotNull();
	}

	@Test
	public void sunnyDayWithNoSuchLabel() {
		mockRequestResponseWithLabel(new ResponseEntity<>((Void) null, HttpStatus.NOT_FOUND), "nosuchlabel");
		this.locator.setRestTemplate(this.restTemplate);
		assertThat(this.locator.locateCollection(this.environment)).isEmpty();
	}

	@Test
	public void sunnyDayWithNoSuchLabelAndFailFast() {
		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		defaults.setFailFast(true);
		this.locator = new ConfigServicePropertySourceLocator(defaults);
		mockRequestResponseWithLabel(new ResponseEntity<>((Void) null, HttpStatus.NOT_FOUND), "release(_)v1.0.0");
		this.locator.setRestTemplate(this.restTemplate);
		TestPropertyValues.of("spring.cloud.config.label:release/v1.0.1").applyTo(this.environment);
		this.expected.expect(IsInstanceOf.instanceOf(IllegalStateException.class));
		this.expected.expectMessage(
				"Could not locate PropertySource and the fail fast property is set, failing: None of labels [release/v1.0.1] found");
		this.locator.locateCollection(this.environment);
	}

	@Test
	public void failsQuietly() {
		mockRequestResponseWithoutLabel(new ResponseEntity<>("Wah!", HttpStatus.INTERNAL_SERVER_ERROR));
		this.locator.setRestTemplate(this.restTemplate);
		assertThat(this.locator.locateCollection(this.environment)).isEmpty();
	}

	@Test
	public void failFast() throws Exception {
		ClientHttpRequestFactory requestFactory = Mockito.mock(ClientHttpRequestFactory.class);
		mockRequestResponse(requestFactory, null, HttpStatus.INTERNAL_SERVER_ERROR);
		RestTemplate restTemplate = new RestTemplate(requestFactory);
		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		defaults.setFailFast(true);
		this.locator = new ConfigServicePropertySourceLocator(defaults);
		this.locator.setRestTemplate(restTemplate);
		this.expected.expect(IsInstanceOf.instanceOf(IllegalStateException.class));
		this.expected.expectCause(IsInstanceOf.instanceOf(HttpServerErrorException.class));
		this.expected.expectMessage("fail fast property is set");
		this.locator.locateCollection(this.environment);
	}

	@Test
	public void failFastWhenNotFound() throws Exception {
		ClientHttpRequestFactory requestFactory = Mockito.mock(ClientHttpRequestFactory.class);
		mockRequestResponse(requestFactory, null, HttpStatus.NOT_FOUND);
		RestTemplate restTemplate = new RestTemplate(requestFactory);
		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		defaults.setFailFast(true);
		this.locator = new ConfigServicePropertySourceLocator(defaults);
		this.locator.setRestTemplate(restTemplate);
		this.expected.expect(IsInstanceOf.instanceOf(IllegalStateException.class));
		this.expected.expectMessage("fail fast property is set, failing: None of labels [] found");
		this.locator.locateCollection(this.environment);
	}

	@Test
	public void failFastWhenRequestTimesOut() {
		mockRequestTimedOut();
		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		defaults.setFailFast(true);
		this.locator = new ConfigServicePropertySourceLocator(defaults);
		this.locator.setRestTemplate(this.restTemplate);
		this.expected.expect(IsInstanceOf.instanceOf(IllegalStateException.class));
		this.expected.expectCause(IsInstanceOf.instanceOf(ResourceAccessException.class));
		this.expected.expectMessage("fail fast property is set");
		this.locator.locateCollection(this.environment);
	}

	@Test
	public void failFastWhenBothPasswordAndAuthorizationPropertiesSet() throws Exception {
		ClientHttpRequestFactory requestFactory = Mockito.mock(ClientHttpRequestFactory.class);
		ClientHttpRequest request = Mockito.mock(ClientHttpRequest.class);
		Mockito.when(requestFactory.createRequest(Mockito.any(URI.class), Mockito.any(HttpMethod.class)))
				.thenReturn(request);
		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		defaults.setFailFast(true);
		defaults.setUsername("username");
		defaults.setPassword("password");
		defaults.getHeaders().put(AUTHORIZATION, "Basic dXNlcm5hbWU6cGFzc3dvcmQNCg==");
		this.locator = new ConfigServicePropertySourceLocator(defaults);
		this.expected.expect(IllegalStateException.class);
		this.expected.expectMessage("Could not locate PropertySource and the fail fast property is set, failing");
		this.locator.locateCollection(this.environment);
	}

	@Test
	public void interceptorShouldAddHeadersWhenHeadersPropertySet() throws Exception {
		MockClientHttpRequest request = new MockClientHttpRequest();
		ClientHttpRequestExecution execution = Mockito.mock(ClientHttpRequestExecution.class);
		byte[] body = new byte[] {};
		Map<String, String> headers = new HashMap<>();
		headers.put("X-Example-Version", "2.1");
		new GenericRequestHeaderInterceptor(headers).intercept(request, body, execution);
		Mockito.verify(execution).execute(request, body);
		assertThat(request.getHeaders().getFirst("X-Example-Version")).isEqualTo("2.1");
	}

	@Test
	public void shouldAddAuthorizationHeaderWhenPasswordSet() {
		HttpHeaders headers = new HttpHeaders();
		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		String username = "user";
		String password = "pass";
		factory(defaults).addAuthorizationToken(headers, username, password);
		assertThat(headers).hasSize(1);
	}

	@Test
	public void shouldAddAuthorizationHeaderWhenAuthorizationSet() {
		HttpHeaders headers = new HttpHeaders();
		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		defaults.getHeaders().put(AUTHORIZATION, "Basic dXNlcm5hbWU6cGFzc3dvcmQNCg==");
		String username = "user";
		String password = null;
		factory(defaults).addAuthorizationToken(headers, username, password);
		assertThat(headers).hasSize(1);
	}

	@Test
	public void shouldThrowExceptionWhenPasswordAndAuthorizationBothSet() {
		HttpHeaders headers = new HttpHeaders();
		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		defaults.getHeaders().put(AUTHORIZATION, "Basic dXNlcm5hbWU6cGFzc3dvcmQNCg==");
		String username = "user";
		String password = "pass";
		this.expected.expect(IllegalStateException.class);
		this.expected.expectMessage("You must set either 'password' or 'authorization'");
		factory(defaults).addAuthorizationToken(headers, username, password);
	}

	@Test
	public void shouldThrowExceptionWhenNegativeReadTimeoutSet() {
		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		defaults.setRequestReadTimeout(-1);
		this.expected.expect(IllegalStateException.class);
		this.expected.expectMessage("Invalid Value for Read Timeout set.");
		factory(defaults).create();
	}

	@Test
	public void shouldThrowExceptionWhenNegativeConnectTimeoutSet() {
		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		defaults.setRequestConnectTimeout(-1);
		this.expected.expect(IllegalStateException.class);
		this.expected.expectMessage("Invalid Value for Connect Timeout set.");
		factory(defaults).create();
	}

	@Test
	public void shouldNotUseNextUriFor_400_And_CONNECTION_TIMEOUT_ONLY_Strategy() throws Exception {
		assertNextUriIsNotTriedForClientError(ConfigClientProperties.MultipleUriStrategy.CONNECTION_TIMEOUT_ONLY,
				HttpStatus.BAD_REQUEST);
	}

	@Test
	public void shouldUseNextUriFor_400_And_ALWAYS_Strategy() throws Exception {
		assertNextUriIsTried(ConfigClientProperties.MultipleUriStrategy.ALWAYS, HttpStatus.BAD_REQUEST);
	}

	@Test
	public void shouldNotUseNextUriFor_404_And_CONNECTION_TIMEOUT_ONLY_Strategy() throws Exception {
		assertNextUriIsNotTriedForNotFoundError(ConfigClientProperties.MultipleUriStrategy.CONNECTION_TIMEOUT_ONLY,
				HttpStatus.NOT_FOUND);
	}

	@Test
	public void shouldUseNextUriFor_404_And_ALWAYS_Strategy() throws Exception {
		assertNextUriIsTried(ConfigClientProperties.MultipleUriStrategy.ALWAYS, HttpStatus.NOT_FOUND);
	}

	@Test
	public void shouldNotUseNextUriFor_500_And_CONNECTION_TIMEOUT_ONLY_Strategy() throws Exception {
		assertNextUriIsNotTriedForServerError(ConfigClientProperties.MultipleUriStrategy.CONNECTION_TIMEOUT_ONLY,
				HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@Test
	public void shouldUseNextUriFor_500_And_ALWAYS_Strategy() throws Exception {
		assertNextUriIsTried(ConfigClientProperties.MultipleUriStrategy.ALWAYS, HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@Test
	public void shouldUseNextUriFor_TimeOut_And_ALWAYS_Strategy() throws Exception {
		assertNextUriIsTriedOnTimeout(ConfigClientProperties.MultipleUriStrategy.ALWAYS);
	}

	@Test
	public void shouldUseNextUriFor_TimeOut_And_CONNECTION_TIMEOUT_ONLY_Strategy() throws Exception {
		assertNextUriIsTriedOnTimeout(ConfigClientProperties.MultipleUriStrategy.CONNECTION_TIMEOUT_ONLY);
	}

	@Test
	public void shouldNotUseNextUriWhenOneIsSuccessful() throws Exception {
		// Set up with three URIs.
		ConfigClientProperties clientProperties = new ConfigClientProperties(this.environment);
		String badURI1 = "http://baduri1";
		String goodURI = "http://localhost:8888";
		String badURI2 = "http://baduri2";
		String[] uris = new String[] { badURI1, goodURI, badURI2 };
		clientProperties.setUri(uris);
		clientProperties.setFailFast(true);
		clientProperties.setMultipleUriStrategy(ConfigClientProperties.MultipleUriStrategy.ALWAYS);
		this.locator = new ConfigServicePropertySourceLocator(clientProperties);
		ClientHttpRequestFactory requestFactory = Mockito.mock(ClientHttpRequestFactory.class);
		RestTemplate restTemplate = new RestTemplate(requestFactory);

		// Second URI will be successful, and the third one should never be called, so
		// locateCollection
		// should return a value.
		mockRequestResponse(requestFactory, badURI1, HttpStatus.BAD_REQUEST);
		mockRequestResponse(requestFactory, goodURI, HttpStatus.OK);
		mockRequestResponse(requestFactory, badURI2, HttpStatus.INTERNAL_SERVER_ERROR);

		this.locator.setRestTemplate(restTemplate);
		assertThat(this.locator.locateCollection(this.environment)).isNotNull();
	}

	@Test
	public void shouldUseMultipleURIs() throws Exception {
		// Set up with four URIs. All should be called until one is successful
		ConfigClientProperties clientProperties = new ConfigClientProperties(this.environment);
		String badURI1 = "http://baduri1";
		String badURI2 = "http://baduri2";
		String badURI3 = "http://baduri3";
		String goodURI = "http://localhost:8888";
		String[] uris = new String[] { badURI1, goodURI, badURI2 };
		clientProperties.setUri(uris);
		clientProperties.setFailFast(true);
		clientProperties.setMultipleUriStrategy(ConfigClientProperties.MultipleUriStrategy.ALWAYS);
		this.locator = new ConfigServicePropertySourceLocator(clientProperties);
		ClientHttpRequestFactory requestFactory = Mockito.mock(ClientHttpRequestFactory.class);
		RestTemplate restTemplate = new RestTemplate(requestFactory);

		// Second URI will be successful, and the third one should never be called, so
		// locateCollection
		// should return a value.
		mockRequestResponse(requestFactory, badURI1, HttpStatus.BAD_REQUEST);
		mockRequestResponse(requestFactory, badURI2, HttpStatus.INTERNAL_SERVER_ERROR);
		mockRequestResponse(requestFactory, badURI3, HttpStatus.NOT_FOUND);
		mockRequestResponse(requestFactory, goodURI, HttpStatus.OK);

		this.locator.setRestTemplate(restTemplate);
		assertThat(this.locator.locateCollection(this.environment)).isNotNull();
	}

	@Test
	public void checkInterceptorHasNoAuthorizationHeaderPresent() {
		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		defaults.getHeaders().put(AUTHORIZATION, "Basic dXNlcm5hbWU6cGFzc3dvcmQNCg==");
		defaults.getHeaders().put("key", "value");
		RestTemplate restTemplate = factory(defaults).create();
		Iterator<ClientHttpRequestInterceptor> iterator = restTemplate.getInterceptors().iterator();
		while (iterator.hasNext()) {
			GenericRequestHeaderInterceptor genericRequestHeaderInterceptor = (GenericRequestHeaderInterceptor) iterator
					.next();
			assertThat(genericRequestHeaderInterceptor.getHeaders().get(AUTHORIZATION)).isEqualTo(null);
		}
	}

	private ConfigClientRequestTemplateFactory factory(ConfigClientProperties properties) {
		return new ConfigClientRequestTemplateFactory(LogFactory.getLog(getClass()), properties);
	}

	@SuppressWarnings({ "unchecked", "raw" })
	@Test
	public void shouldPreserveOrder() {
		Environment body = new Environment("app", "master");
		LinkedHashMap<Object, Object> properties = new LinkedHashMap<>();
		properties.put("zuul.routes.specificproduct.path", originValue("/v1/product/electronics/**",
				"Config Server /config-repo/zuul-service/zuul-service.yml:5:13"));

		properties.put("zuul.routes.specificproduct.service-id", originValue("electronic-product-service",
				"Config Server /config-repo/zuul-service/zuul-service.yml:6:19"));

		properties.put("zuul.routes.specificproduct.strip-prefix",
				originValue("false", "Config Server /config-repo/zuul-service/zuul-service.yml:7:21"));

		properties.put("zuul.routes.specificproduct.sensitiveHeaders",
				originValue("", "Config Server /config-repo/zuul-service/zuul-service.yml:8:24"));

		properties.put("zuul.routes.genericproduct.path",
				originValue("/v1/product/**", "Config Server /config-repo/zuul-service/zuul-service.yml:10:13"));

		properties.put("zuul.routes.genericproduct.service-id",
				originValue("product-service", "Config Server /config-repo/zuul-service/zuul-service.yml:11:19"));

		properties.put("zuul.routes.genericproduct.strip-prefix",
				originValue("false", "Config Server /config-repo/zuul-service/zuul-service.yml:12:21"));

		properties.put("zuul.routes.genericproduct.sensitiveHeaders",
				originValue("", "Config Server /config-repo/zuul-service/zuul-service.yml:13:24"));
		body.add(new PropertySource("source1", properties));
		mockRequestResponseWithoutLabel(new ResponseEntity<>(body, HttpStatus.OK));
		this.locator.setRestTemplate(this.restTemplate);

		List<org.springframework.core.env.PropertySource<?>> propertySources = new ArrayList<>(
				this.locator.locateCollection(this.environment));
		assertThat(propertySources).hasSize(2);
		org.springframework.core.env.PropertySource<?> propertySource = propertySources.get(1);
		Map source = (Map) propertySource.getSource();
		Iterator iterator = source.keySet().iterator();
		assertThat(iterator.next()).isEqualTo("zuul.routes.specificproduct.path");
		assertThat(iterator.next()).isEqualTo("zuul.routes.specificproduct.service-id");
		assertThat(source).isInstanceOf(LinkedHashMap.class);
	}

	private void assertNextUriIsNotTried(ConfigClientProperties.MultipleUriStrategy multipleUriStrategy,
			HttpStatus firstUriResponse, Class<? extends Exception> expectedCause) throws Exception {
		// Set up with two URIs.
		ConfigClientProperties clientProperties = new ConfigClientProperties(this.environment);
		String badURI = "http://baduri";
		String goodURI = "http://localhost:8888";
		String[] uris = new String[] { badURI, goodURI };
		clientProperties.setUri(uris);
		clientProperties.setFailFast(true);
		// Strategy is CONNECTION_TIMEOUT_ONLY, so it should not try the next URI for
		// INTERNAL_SERVER_ERROR
		clientProperties.setMultipleUriStrategy(multipleUriStrategy);
		this.locator = new ConfigServicePropertySourceLocator(clientProperties);
		ClientHttpRequestFactory requestFactory = Mockito.mock(ClientHttpRequestFactory.class);
		RestTemplate restTemplate = new RestTemplate(requestFactory);
		mockRequestResponse(requestFactory, badURI, firstUriResponse);
		mockRequestResponse(requestFactory, goodURI, HttpStatus.OK);
		this.locator.setRestTemplate(restTemplate);
		this.expected.expect(IsInstanceOf.instanceOf(IllegalStateException.class));
		if (expectedCause != null) {
			this.expected.expectCause(IsInstanceOf.instanceOf(expectedCause));
		}
		this.expected.expectMessage("fail fast property is set");
		this.locator.locateCollection(this.environment);
	}

	@SuppressWarnings("SameParameterValue")
	private void assertNextUriIsNotTriedForClientError(ConfigClientProperties.MultipleUriStrategy multipleUriStrategy,
			HttpStatus firstUriResponse) throws Exception {

		assertNextUriIsNotTried(multipleUriStrategy, firstUriResponse, HttpClientErrorException.class);
	}

	@SuppressWarnings("SameParameterValue")
	private void assertNextUriIsNotTriedForNotFoundError(ConfigClientProperties.MultipleUriStrategy multipleUriStrategy,
			HttpStatus firstUriResponse) throws Exception {

		// NOT_FOUND is treated differently
		assertNextUriIsNotTried(multipleUriStrategy, firstUriResponse, null);
	}

	@SuppressWarnings("SameParameterValue")
	private void assertNextUriIsNotTriedForServerError(ConfigClientProperties.MultipleUriStrategy multipleUriStrategy,
			HttpStatus firstUriResponse) throws Exception {

		assertNextUriIsNotTried(multipleUriStrategy, firstUriResponse, HttpServerErrorException.class);
	}

	@SuppressWarnings("SameParameterValue")
	private void assertNextUriIsTried(ConfigClientProperties.MultipleUriStrategy multipleUriStrategy,
			HttpStatus firstUriResponse) throws Exception {

		// Set up with two URIs.
		ConfigClientProperties clientProperties = new ConfigClientProperties(this.environment);
		String badURI = "http://baduri";
		String goodURI = "http://localhost:8888";
		String[] uris = new String[] { badURI, goodURI };
		clientProperties.setUri(uris);
		clientProperties.setFailFast(true);
		// Strategy is ALWAYS, so it should try all URIs until successful
		clientProperties.setMultipleUriStrategy(multipleUriStrategy);
		this.locator = new ConfigServicePropertySourceLocator(clientProperties);
		ClientHttpRequestFactory requestFactory = Mockito.mock(ClientHttpRequestFactory.class);
		RestTemplate restTemplate = new RestTemplate(requestFactory);
		mockRequestResponse(requestFactory, badURI, firstUriResponse);
		mockRequestResponse(requestFactory, goodURI, HttpStatus.OK);
		this.locator.setRestTemplate(restTemplate);
		assertThat(this.locator.locateCollection(this.environment)).isNotNull();
	}

	private void assertNextUriIsTriedOnTimeout(ConfigClientProperties.MultipleUriStrategy multipleUriStrategy)
			throws Exception {
		// Set up with two URIs.
		ConfigClientProperties clientProperties = new ConfigClientProperties(this.environment);
		String badURI = "http://baduri";
		String goodURI = "http://localhost:8888";
		String[] uris = new String[] { badURI, goodURI };
		clientProperties.setUri(uris);
		clientProperties.setFailFast(true);
		// Strategy should not matter when the error is connection timed out
		clientProperties.setMultipleUriStrategy(multipleUriStrategy);
		this.locator = new ConfigServicePropertySourceLocator(clientProperties);
		ClientHttpRequestFactory requestFactory = Mockito.mock(ClientHttpRequestFactory.class);
		RestTemplate restTemplate = new RestTemplate(requestFactory);

		// First URI times out. Second one is successful
		mockRequestTimedOut(requestFactory, badURI);
		mockRequestResponse(requestFactory, goodURI, HttpStatus.OK);
		this.locator.setRestTemplate(restTemplate);
		assertThat(this.locator.locateCollection(this.environment)).isNotNull();
	}

	private Map<String, Object> originValue(String value, String origin) {
		HashMap<String, Object> map = new HashMap<>();
		map.put("value", value);
		map.put("origin", origin);
		return map;
	}

	private void mockRequestResponse(ClientHttpRequestFactory requestFactory, String baseURI, HttpStatus status)
			throws Exception {
		ClientHttpRequest request = Mockito.mock(ClientHttpRequest.class);
		ClientHttpResponse response = Mockito.mock(ClientHttpResponse.class);

		if (baseURI == null) {
			Mockito.when(requestFactory.createRequest(Mockito.any(URI.class), Mockito.any(HttpMethod.class)))
					.thenReturn(request);
		}
		else {
			Mockito.when(requestFactory.createRequest(Mockito.eq(new URI(baseURI + "/application/default")),
					Mockito.any(HttpMethod.class))).thenReturn(request);
		}

		Mockito.when(request.getHeaders()).thenReturn(new HttpHeaders());
		Mockito.when(request.execute()).thenReturn(response);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		Mockito.when(response.getHeaders()).thenReturn(headers);
		Mockito.when(response.getStatusCode()).thenReturn(status);
		Mockito.when(response.getRawStatusCode()).thenReturn(status.value());
		Mockito.when(response.getBody()).thenReturn(new ByteArrayInputStream("{}".getBytes()));
	}

	@SuppressWarnings("unchecked")
	private void mockRequestResponseWithLabel(ResponseEntity<?> response, String label) {
		Mockito.when(this.restTemplate.exchange(Mockito.any(String.class), Mockito.any(HttpMethod.class),
				Mockito.any(HttpEntity.class), Mockito.any(Class.class), anyString(), anyString(),
				ArgumentMatchers.eq(label))).thenReturn(response);
	}

	@SuppressWarnings("unchecked")
	private void mockRequestResponseWithoutLabel(ResponseEntity<?> response) {
		Mockito.when(this.restTemplate.exchange(Mockito.any(String.class), Mockito.any(HttpMethod.class),
				Mockito.any(HttpEntity.class), Mockito.any(Class.class), anyString(), anyString()))
				.thenReturn(response);
	}

	@SuppressWarnings("unchecked")
	private void mockRequestTimedOut() {
		Mockito.when(this.restTemplate.exchange(Mockito.any(String.class), Mockito.any(HttpMethod.class),
				Mockito.any(HttpEntity.class), Mockito.any(Class.class), anyString(), anyString()))
				.thenThrow(ResourceAccessException.class);
	}

	private void mockRequestTimedOut(ClientHttpRequestFactory requestFactory, String baseURI) throws Exception {
		ClientHttpRequest request = Mockito.mock(ClientHttpRequest.class);

		if (baseURI == null) {
			Mockito.when(requestFactory.createRequest(Mockito.any(URI.class), Mockito.any(HttpMethod.class)))
					.thenReturn(request);
		}
		else {
			Mockito.when(requestFactory.createRequest(Mockito.eq(new URI(baseURI + "/application/default")),
					Mockito.any(HttpMethod.class))).thenReturn(request);
		}

		Mockito.when(request.getHeaders()).thenReturn(new HttpHeaders());
		Mockito.when(request.execute()).thenThrow(IOException.class);
	}

}
