/*
 * Copyright 2013-2019 the original author or authors.
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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.cloud.bootstrap.support.OriginTrackedCompositePropertySource;
import org.springframework.cloud.config.client.ConfigClientProperties.Credentials;
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
import org.springframework.util.Assert;
import org.springframework.util.Base64Utils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.springframework.cloud.config.client.ConfigClientProperties.AUTHORIZATION;
import static org.springframework.cloud.config.client.ConfigClientProperties.STATE_HEADER;
import static org.springframework.cloud.config.client.ConfigClientProperties.TOKEN_HEADER;
import static org.springframework.cloud.config.environment.EnvironmentMediaType.V2_JSON;

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
		CompositePropertySource composite = new OriginTrackedCompositePropertySource(
				"configService");
		RestTemplate restTemplate = this.restTemplate == null
				? getSecureRestTemplate(properties) : this.restTemplate;
		Exception error = null;
		String errorBody = null;
		try {
			String[] labels = new String[] { "" };
			if (StringUtils.hasText(properties.getLabel())) {
				labels = StringUtils
						.commaDelimitedListToStringArray(properties.getLabel());
			}
			String state = ConfigClientStateHolder.getState();
			// Try all the labels until one works
			for (String label : labels) {
				Environment result = getRemoteEnvironment(restTemplate, properties,
						label.trim(), state);
				if (result != null) {
					log(result);

					// result.getPropertySources() can be null if using xml
					if (result.getPropertySources() != null) {
						for (PropertySource source : result.getPropertySources()) {
							@SuppressWarnings("unchecked")
							Map<String, Object> map = translateOrigins(source.getName(),
									(Map<String, Object>) source.getSource());
							composite.addPropertySource(
									new OriginTrackedMapPropertySource(source.getName(),
											map));
						}
					}

					if (StringUtils.hasText(result.getState())
							|| StringUtils.hasText(result.getVersion())) {
						HashMap<String, Object> map = new HashMap<>();
						putValue(map, "config.client.state", result.getState());
						putValue(map, "config.client.version", result.getVersion());
						composite.addFirstPropertySource(
								new MapPropertySource("configClient", map));
					}
					return composite;
				}
			}
			errorBody = String.format("None of labels %s found", Arrays.toString(labels));
		}
		catch (HttpServerErrorException e) {
			error = e;
			if (MediaType.APPLICATION_JSON
					.includes(e.getResponseHeaders().getContentType())) {
				errorBody = e.getResponseBodyAsString();
			}
		}
		catch (Exception e) {
			error = e;
		}
		if (properties.isFailFast()) {
			throw new IllegalStateException(
					"Could not locate PropertySource and the fail fast property is set, failing"
							+ (errorBody == null ? "" : ": " + errorBody),
					error);
		}
		logger.warn("Could not locate PropertySource: "
				+ (error != null ? error.getMessage() : errorBody));
		return null;

	}

	private void log(Environment result) {
		if (logger.isInfoEnabled()) {
			logger.info(String.format(
					"Located environment: name=%s, profiles=%s, label=%s, version=%s, state=%s",
					result.getName(),
					result.getProfiles() == null ? ""
							: Arrays.asList(result.getProfiles()),
					result.getLabel(), result.getVersion(), result.getState()));
		}
		if (logger.isDebugEnabled()) {
			List<PropertySource> propertySourceList = result.getPropertySources();
			if (propertySourceList != null) {
				int propertyCount = 0;
				for (PropertySource propertySource : propertySourceList) {
					propertyCount += propertySource.getSource().size();
				}
				logger.debug(String.format(
						"Environment %s has %d property sources with %d properties.",
						result.getName(), result.getPropertySources().size(),
						propertyCount));
			}

		}
	}

	private Map<String, Object> translateOrigins(String name,
			Map<String, Object> source) {
		Map<String, Object> withOrigins = new HashMap<>();
		for (Map.Entry<String, Object> entry : source.entrySet()) {
			boolean hasOrigin = false;

			if (entry.getValue() instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> value = (Map<String, Object>) entry.getValue();
				if (value.size() == 2 && value.containsKey("origin")
						&& value.containsKey("value")) {
					Origin origin = new ConfigServiceOrigin(name, value.get("origin"));
					OriginTrackedValue trackedValue = OriginTrackedValue
							.of(value.get("value"), origin);
					withOrigins.put(entry.getKey(), trackedValue);
					hasOrigin = true;
				}
			}

			if (!hasOrigin) {
				withOrigins.put(entry.getKey(), entry.getValue());
			}
		}
		return withOrigins;
	}

	private void putValue(HashMap<String, Object> map, String key, String value) {
		if (StringUtils.hasText(value)) {
			map.put(key, value);
		}
	}

	private Environment getRemoteEnvironment(RestTemplate restTemplate,
			ConfigClientProperties properties, String label, String state) {
		String path = "/{name}/{profile}";
		String name = properties.getName();
		String profile = properties.getProfile();
		String token = properties.getToken();
		int noOfUrls = properties.getUri().length;
		if (noOfUrls > 1) {
			logger.info("Multiple Config Server Urls found listed.");
		}

		Object[] args = new String[] { name, profile };
		if (StringUtils.hasText(label)) {
			if (label.contains("/")) {
				label = label.replace("/", "(_)");
			}
			args = new String[] { name, profile, label };
			path = path + "/{label}";
		}
		ResponseEntity<Environment> response = null;

		for (int i = 0; i < noOfUrls; i++) {
			Credentials credentials = properties.getCredentials(i);
			String uri = credentials.getUri();
			String username = credentials.getUsername();
			String password = credentials.getPassword();

			logger.info("Fetching config from server at : " + uri);

			try {
				HttpHeaders headers = new HttpHeaders();
				headers.setAccept(
						Collections.singletonList(MediaType.parseMediaType(V2_JSON)));
				addAuthorizationToken(properties, headers, username, password);
				if (StringUtils.hasText(token)) {
					headers.add(TOKEN_HEADER, token);
				}
				if (StringUtils.hasText(state) && properties.isSendState()) {
					headers.add(STATE_HEADER, state);
				}

				final HttpEntity<Void> entity = new HttpEntity<>((Void) null, headers);
				response = restTemplate.exchange(uri + path, HttpMethod.GET, entity,
						Environment.class, args);
			}
			catch (HttpClientErrorException e) {
				if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
					throw e;
				}
			}
			catch (ResourceAccessException e) {
				logger.info("Connect Timeout Exception on Url - " + uri
						+ ". Will be trying the next url if available");
				if (i == noOfUrls - 1) {
					throw e;
				}
				else {
					continue;
				}
			}

			if (response == null || response.getStatusCode() != HttpStatus.OK) {
				return null;
			}

			Environment result = response.getBody();
			return result;
		}

		return null;
	}

	public void setRestTemplate(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	private RestTemplate getSecureRestTemplate(ConfigClientProperties client) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		if (client.getRequestReadTimeout() < 0) {
			throw new IllegalStateException("Invalid Value for Read Timeout set.");
		}
		if (client.getRequestConnectTimeout() < 0) {
			throw new IllegalStateException("Invalid Value for Connect Timeout set.");
		}
		requestFactory.setReadTimeout(client.getRequestReadTimeout());
		requestFactory.setConnectTimeout(client.getRequestConnectTimeout());
		RestTemplate template = new RestTemplate(requestFactory);
		Map<String, String> headers = new HashMap<>(client.getHeaders());
		if (headers.containsKey(AUTHORIZATION)) {
			headers.remove(AUTHORIZATION); // To avoid redundant addition of header
		}
		if (!headers.isEmpty()) {
			template.setInterceptors(Arrays.<ClientHttpRequestInterceptor>asList(
					new GenericRequestHeaderInterceptor(headers)));
		}

		return template;
	}

	private void addAuthorizationToken(ConfigClientProperties configClientProperties,
			HttpHeaders httpHeaders, String username, String password) {
		String authorization = configClientProperties.getHeaders().get(AUTHORIZATION);

		if (password != null && authorization != null) {
			throw new IllegalStateException(
					"You must set either 'password' or 'authorization'");
		}

		if (password != null) {
			byte[] token = Base64Utils.encode((username + ":" + password).getBytes());
			httpHeaders.add("Authorization", "Basic " + new String(token));
		}
		else if (authorization != null) {
			httpHeaders.add("Authorization", authorization);
		}

	}

	/**
	 * Adds the provided headers to the request.
	 */
	public static class GenericRequestHeaderInterceptor
			implements ClientHttpRequestInterceptor {

		private final Map<String, String> headers;

		public GenericRequestHeaderInterceptor(Map<String, String> headers) {
			this.headers = headers;
		}

		@Override
		public ClientHttpResponse intercept(HttpRequest request, byte[] body,
				ClientHttpRequestExecution execution) throws IOException {
			for (Entry<String, String> header : this.headers.entrySet()) {
				request.getHeaders().add(header.getKey(), header.getValue());
			}
			return execution.execute(request, body);
		}

		protected Map<String, String> getHeaders() {
			return this.headers;
		}

	}

	static class ConfigServiceOrigin implements Origin {

		private final String remotePropertySource;

		private final Object origin;

		ConfigServiceOrigin(String remotePropertySource, Object origin) {
			this.remotePropertySource = remotePropertySource;
			Assert.notNull(origin, "origin may not be null");
			this.origin = origin;

		}

		@Override
		public String toString() {
			return "Config Server " + this.remotePropertySource + ":"
					+ this.origin.toString();
		}

	}

}
