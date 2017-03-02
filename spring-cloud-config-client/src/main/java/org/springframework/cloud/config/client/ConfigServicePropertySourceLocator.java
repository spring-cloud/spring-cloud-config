/*
 * Copyright 2013-2014 the original author or authors.
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

package org.springframework.cloud.config.client;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.Retryable;
import org.springframework.util.Base64Utils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import static org.springframework.cloud.config.client.ConfigClientProperties.STATE_HEADER;
import static org.springframework.cloud.config.client.ConfigClientProperties.TOKEN_HEADER;

/**
 * @author Dave Syer
 * @author Mathieu Ouellet
 *
 */
@Order(0)
public class ConfigServicePropertySourceLocator implements PropertySourceLocator {

	private static Log logger = LogFactory
			.getLog(ConfigServicePropertySourceLocator.class);

	private RestTemplate restTemplate;
	private ConfigClientProperties defaultProperties;

	public ConfigServicePropertySourceLocator(ConfigClientProperties defaultProperties) {
		this.defaultProperties = defaultProperties;
	}

	@Override
	@Retryable(interceptor = "configServerRetryInterceptor")
	public org.springframework.core.env.PropertySource<?> locate(
			org.springframework.core.env.Environment environment) {
		ConfigClientProperties properties = this.defaultProperties.override(environment);
		CompositePropertySource composite = new CompositePropertySource("configService");
		RestTemplate restTemplate = this.restTemplate == null ? getSecureRestTemplate(properties)
				: this.restTemplate;
		Exception error = null;
		String errorBody = null;
		logger.info("Fetching config from server at: " + properties.getRawUri());
		try {
			String[] labels = new String[] { "" };
			if (StringUtils.hasText(properties.getLabel())) {
				labels = StringUtils.commaDelimitedListToStringArray(properties.getLabel());
			}

			String state = ConfigClientStateHolder.getState();

			// Try all the labels until one works
			for (String label : labels) {
				Environment result = getRemoteEnvironment(restTemplate,
						properties, label.trim(), state);
				if (result != null) {
					logger.info(String.format("Located environment: name=%s, profiles=%s, label=%s, version=%s, state=%s",
							result.getName(),
							result.getProfiles() == null ? "" : Arrays.asList(result.getProfiles()),
							result.getLabel(), result.getVersion(), result.getState()));

					if (result.getPropertySources() != null) { // result.getPropertySources() can be null if using xml
						for (PropertySource source : result.getPropertySources()) {
							@SuppressWarnings("unchecked")
							Map<String, Object> map = (Map<String, Object>) source
									.getSource();
							composite.addPropertySource(new MapPropertySource(source
									.getName(), map));
						}
					}

					if (StringUtils.hasText(result.getState()) || StringUtils.hasText(result.getVersion())) {
						HashMap<String, Object> map = new HashMap<>();
						putValue(map, "config.client.state", result.getState());
						putValue(map, "config.client.version", result.getVersion());
						composite.addFirstPropertySource(new MapPropertySource("configClient", map));
					}
					return composite;
				}
			}
		}
		catch (HttpServerErrorException e) {
			error = e;
			if (MediaType.APPLICATION_JSON.includes(e.getResponseHeaders()
					.getContentType())) {
				errorBody = e.getResponseBodyAsString();
			}
		}
		catch (Exception e) {
			error = e;
		}
		if (properties.isFailFast()) {
			throw new IllegalStateException(
					"Could not locate PropertySource and the fail fast property is set, failing",
					error);
		}
		logger.warn("Could not locate PropertySource: "
				+ (errorBody == null ? error==null ? "label not found" : error.getMessage() : errorBody));
		return null;

	}

	private void putValue(HashMap<String, Object> map, String key, String value) {
		if (StringUtils.hasText(value)) {
			map.put(key, value);
		}
	}

	private Environment getRemoteEnvironment(RestTemplate restTemplate, ConfigClientProperties properties,
											 String label, String state) {
		String path = "/{name}/{profile}";
		String name = properties.getName();
		String profile = properties.getProfile();
		String token = properties.getToken();
		String uri = properties.getRawUri();

		Object[] args = new String[] { name, profile };
		if (StringUtils.hasText(label)) {
			args = new String[] { name, profile, label };
			path = path + "/{label}";
		}
		ResponseEntity<Environment> response = null;

		try {
			HttpHeaders headers = new HttpHeaders();
			if (StringUtils.hasText(token)) {
				headers.add(TOKEN_HEADER, token);
			}
			if (StringUtils.hasText(state)) { //TODO: opt in to sending state?
				headers.add(STATE_HEADER, state);
			}
			final HttpEntity<Void> entity = new HttpEntity<>((Void) null, headers);
			response = restTemplate.exchange(uri + path, HttpMethod.GET,
					entity, Environment.class, args);
		}
		catch (HttpClientErrorException e) {
			if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
				throw e;
			}
		}

		if (response == null || response.getStatusCode() != HttpStatus.OK) {
			return null;
		}
		Environment result = response.getBody();
		return result;
	}

	public void setRestTemplate(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	private RestTemplate getSecureRestTemplate(ConfigClientProperties client) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setReadTimeout((60 * 1000 * 3) + 5000); //TODO 3m5s, make configurable?
		RestTemplate template = new RestTemplate(requestFactory);
		String username = client.getUsername();
		String password = client.getPassword();
		String authorization = client.getAuthorization();
		Map<String, String> headers = new HashMap<>(client.getHeaders());

		if (password != null && authorization != null) {
			throw new IllegalStateException(
					"You must set either 'password' or 'authorization'");
		}

		if (password != null) {
			byte[] token = Base64Utils.encode((username + ":" + password).getBytes());
			headers.put("Authorization", "Basic " + new String(token));
		}
		else if (authorization != null) {
			headers.put("Authorization", authorization);
		}

		if (!headers.isEmpty()) {
			template.setInterceptors(Arrays.<ClientHttpRequestInterceptor> asList(
					new GenericRequestHeaderInterceptor(headers)));
		}

		return template;
	}

	public static class GenericRequestHeaderInterceptor
			implements ClientHttpRequestInterceptor {

		private final Map<String, String> headers;

		public GenericRequestHeaderInterceptor(Map<String, String> headers) {
			this.headers = headers;
		}

		@Override
		public ClientHttpResponse intercept(HttpRequest request, byte[] body,
				ClientHttpRequestExecution execution) throws IOException {
			for (Entry<String, String> header : headers.entrySet()) {
				request.getHeaders().add(header.getKey(), header.getValue());
			}
			return execution.execute(request, body);
		}
	}
}
