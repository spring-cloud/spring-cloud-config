package org.springframework.cloud.config.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.hamcrest.core.IsInstanceOf;
import org.hamcrest.core.IsNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.springframework.boot.test.util.EnvironmentTestUtils;
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
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

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
		mockRequestResponseWithoutLabel(new ResponseEntity<>(body,
				HttpStatus.OK));
		this.locator.setRestTemplate(this.restTemplate);
		assertNotNull(this.locator.locate(this.environment));
	}

	@Test
	public void sunnyDayWithLabel() {
		Environment body = new Environment("app", "master");
		mockRequestResponseWithLabel(
				new ResponseEntity<>(body, HttpStatus.OK), "v1.0.0");
		this.locator.setRestTemplate(this.restTemplate);
		EnvironmentTestUtils.addEnvironment(this.environment,
				"spring.cloud.config.label:v1.0.0");
		assertNotNull(this.locator.locate(this.environment));
	}

	@Test
	public void sunnyDayWithNoSuchLabel() {
		mockRequestResponseWithLabel(new ResponseEntity<Void>((Void) null,
				HttpStatus.NOT_FOUND), "nosuchlabel");
		this.locator.setRestTemplate(this.restTemplate);
		assertNull(this.locator.locate(this.environment));
	}

	@Test
	public void failsQuietly() {
		mockRequestResponseWithoutLabel(new ResponseEntity<>("Wah!",
				HttpStatus.INTERNAL_SERVER_ERROR));
		this.locator.setRestTemplate(this.restTemplate);
		assertNull(this.locator.locate(this.environment));
	}

	@Test
	public void failFast() throws Exception {
		ClientHttpRequestFactory requestFactory = Mockito
				.mock(ClientHttpRequestFactory.class);
		ClientHttpRequest request = Mockito.mock(ClientHttpRequest.class);
		ClientHttpResponse response = Mockito.mock(ClientHttpResponse.class);
		Mockito.when(
				requestFactory.createRequest(Mockito.any(URI.class),
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
		Mockito.when(response.getStatusCode()).thenReturn(
				HttpStatus.INTERNAL_SERVER_ERROR);
		Mockito.when(response.getBody()).thenReturn(
				new ByteArrayInputStream("{}".getBytes()));
		this.locator.setRestTemplate(restTemplate);
		this.expected.expectCause(IsInstanceOf
				.instanceOf(IllegalArgumentException.class));
		this.expected.expectMessage("fail fast property is set");
		this.locator.locate(this.environment);
	}

	@Test
	public void failFastWhenNotFound() throws Exception {
		ClientHttpRequestFactory requestFactory = Mockito
				.mock(ClientHttpRequestFactory.class);
		ClientHttpRequest request = Mockito.mock(ClientHttpRequest.class);
		ClientHttpResponse response = Mockito.mock(ClientHttpResponse.class);
		Mockito.when(
				requestFactory.createRequest(Mockito.any(URI.class),
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
		Mockito.when(response.getStatusCode()).thenReturn(
				HttpStatus.NOT_FOUND);
		Mockito.when(response.getBody()).thenReturn(
				new ByteArrayInputStream("".getBytes()));
		this.locator.setRestTemplate(restTemplate);
		this.expected.expectCause(IsInstanceOf.instanceOf(IllegalArgumentException.class));
		this.expected.expectMessage("fail fast property is set");
		this.locator.locate(this.environment);
	}

	@Test
	public void failFastWhenBothPasswordAndAuthorizationPropertiesSet() throws Exception {
		ClientHttpRequestFactory requestFactory = Mockito
				.mock(ClientHttpRequestFactory.class);
		ClientHttpRequest request = Mockito.mock(ClientHttpRequest.class);
		Mockito.when(
				requestFactory.createRequest(Mockito.any(URI.class),
						Mockito.any(HttpMethod.class))).thenReturn(request);
		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		defaults.setFailFast(true);
		defaults.setUsername("username");
		defaults.setPassword("password");
		defaults.setAuthorization("Basic dXNlcm5hbWU6cGFzc3dvcmQNCg==");
		this.locator = new ConfigServicePropertySourceLocator(defaults);
 		this.expected.expect(IllegalStateException.class);
		this.expected.expectMessage("You must set either 'password' or 'authorization'");
		this.locator.locate(this.environment);
	}

	@Test
	public void interceptorShouldAddHeaderWhenPasswordPropertySet() throws Exception {
		ClientHttpRequestFactory requestFactory = Mockito
				.mock(ClientHttpRequestFactory.class);
		ClientHttpRequest request = Mockito.mock(ClientHttpRequest.class);
		Mockito.when(requestFactory.createRequest(Mockito.any(URI.class),
				Mockito.any(HttpMethod.class))).thenReturn(request);

		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		defaults.setUsername("username");
		defaults.setPassword("password");
		this.locator = new ConfigServicePropertySourceLocator(defaults);

		RestTemplate restTemplate = ReflectionTestUtils.invokeMethod(this.locator,
				"getSecureRestTemplate", defaults);
		restTemplate.setRequestFactory(requestFactory);

		this.locator.setRestTemplate(restTemplate);
		this.locator.locate(this.environment);

		assertThat(restTemplate.getInterceptors()).hasSize(1);
	}

	@Test
	public void interceptorShouldAddHeaderWhenAuthorizationPropertySet() throws Exception {
		ClientHttpRequestFactory requestFactory = Mockito
				.mock(ClientHttpRequestFactory.class);
		ClientHttpRequest request = Mockito.mock(ClientHttpRequest.class);
		Mockito.when(requestFactory.createRequest(Mockito.any(URI.class),
				Mockito.any(HttpMethod.class))).thenReturn(request);

		ConfigClientProperties defaults = new ConfigClientProperties(this.environment);
		defaults.setAuthorization("Basic dXNlcm5hbWU6cGFzc3dvcmQ=");
		this.locator = new ConfigServicePropertySourceLocator(defaults);

		RestTemplate restTemplate = ReflectionTestUtils.invokeMethod(this.locator,
				"getSecureRestTemplate", defaults);
		restTemplate.setRequestFactory(requestFactory);

		this.locator.setRestTemplate(restTemplate);
		this.locator.locate(this.environment);

		assertThat(restTemplate.getInterceptors()).hasSize(1);
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

	@SuppressWarnings("unchecked")
	private void mockRequestResponseWithLabel(ResponseEntity<?> response, String label) {
		Mockito.when(
				this.restTemplate.exchange(Mockito.any(String.class),
						Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class),
						Mockito.any(Class.class), Matchers.anyString(),
						Matchers.anyString(), Matchers.eq(label))).thenReturn(response);
	}

	@SuppressWarnings("unchecked")
	private void mockRequestResponseWithoutLabel(ResponseEntity<?> response) {
		Mockito.when(
				this.restTemplate.exchange(Mockito.any(String.class),
						Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class),
						Mockito.any(Class.class), Matchers.anyString(),
						Matchers.anyString())).thenReturn(response);
	}
}
