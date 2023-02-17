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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.origin.Origin;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.cloud.bootstrap.support.OriginTrackedCompositePropertySource;
import org.springframework.cloud.config.client.ConfigClientProperties.Credentials;
import org.springframework.cloud.config.client.validation.InvalidApplicationNameException;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Retryable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.springframework.cloud.config.client.ConfigClientProperties.NAME_PLACEHOLDER;
import static org.springframework.cloud.config.client.ConfigClientProperties.STATE_HEADER;
import static org.springframework.cloud.config.client.ConfigClientProperties.TOKEN_HEADER;

/**
 * @author Dave Syer
 * @author Mathieu Ouellet
 *
 */
@Order(0)
public class ConfigServicePropertySourceLocator implements PropertySourceLocator {

	private static Log logger = LogFactory.getLog(ConfigServicePropertySourceLocator.class);

	private RestTemplate restTemplate;

	private ConfigClientProperties defaultProperties;

	private RemoteEnvironmentFetcher remoteEnvironmentFetcher;

	public ConfigServicePropertySourceLocator(ConfigClientProperties defaultProperties) {
		this.defaultProperties = defaultProperties;
		this.remoteEnvironmentFetcher = new RemoteEnvironmentFetcher();
	}

	@Override
	@Retryable(interceptor = "configServerRetryInterceptor")
	public org.springframework.core.env.PropertySource<?> locate(org.springframework.core.env.Environment environment) {
		ConfigClientProperties properties = this.defaultProperties.override(environment);
		// TODO this logic needs to apply in both methods or make the below locate private
		if (StringUtils.startsWithIgnoreCase(properties.getName(), "application-")) {
			InvalidApplicationNameException exception = new InvalidApplicationNameException(properties.getName());
			if (properties.isFailFast()) {
				throw exception;
			}
			else {
				logger.warn(NAME_PLACEHOLDER + " resolved to " + properties.getName()
						+ ", not going to load remote properties. Ensure application name doesn't start with 'application-'");
				return null;
			}
		}
		return locate(properties);
	}

	private org.springframework.core.env.PropertySource<?> locate(ConfigClientProperties properties) {
		CompositePropertySource composite = new OriginTrackedCompositePropertySource("configService");
		ConfigClientRequestTemplateFactory requestTemplateFactory = new ConfigClientRequestTemplateFactory(logger,
				properties);

		Exception error = null;
		String errorBody = null;
		try {
			String[] labels = new String[] { "" };
			if (StringUtils.hasText(properties.getLabel())) {
				labels = StringUtils.commaDelimitedListToStringArray(properties.getLabel());
			}
			String state = ConfigClientStateHolder.getState();
			// Try all the labels until one works
			for (String label : labels) {
				remoteEnvironmentFetcher.fetch(() -> getRemoteEnvironment(requestTemplateFactory, label.trim(), state),
						properties, propertySource -> composite.addFirstPropertySource(propertySource));

				if (!composite.getPropertySources().isEmpty()) {
					return composite;
				}
			}
			errorBody = String.format("None of labels %s found", Arrays.toString(labels));
		}
		catch (HttpServerErrorException e) {
			error = e;
			if (MediaType.APPLICATION_JSON.includes(e.getResponseHeaders().getContentType())) {
				errorBody = e.getResponseBodyAsString();
			}
		}
		catch (Exception e) {
			error = e;
		}
		if (properties.isFailFast()) {
			throw new IllegalStateException("Could not locate PropertySource and the fail fast property is set, failing"
					+ (errorBody == null ? "" : ": " + errorBody), error);
		}
		logger.warn("Could not locate PropertySource: " + (error != null ? error.getMessage() : errorBody));
		return null;

	}

	@Override
	@Retryable(interceptor = "configServerRetryInterceptor")
	public Collection<org.springframework.core.env.PropertySource<?>> locateCollection(
			org.springframework.core.env.Environment environment) {
		return PropertySourceLocator.locateCollection(this, environment);
	}

	private Environment getRemoteEnvironment(ConfigClientRequestTemplateFactory requestTemplateFactory, String label,
			String state) {
		RestTemplate restTemplate = this.restTemplate == null ? requestTemplateFactory.create() : this.restTemplate;
		ConfigClientProperties properties = requestTemplateFactory.getProperties();
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
			// workaround for Spring MVC matching / in paths
			label = Environment.denormalize(label);
			args = new String[] { name, profile, label };
			path = path + "/{label}";
		}
		ResponseEntity<Environment> response = null;
		List<MediaType> acceptHeader = Collections.singletonList(MediaType.parseMediaType(properties.getMediaType()));

		for (int i = 0; i < noOfUrls; i++) {
			Credentials credentials = properties.getCredentials(i);
			String uri = credentials.getUri();
			String username = credentials.getUsername();
			String password = credentials.getPassword();

			logger.info("Fetching config from server at : " + uri);

			try {
				HttpHeaders headers = new HttpHeaders();
				headers.setAccept(acceptHeader);
				requestTemplateFactory.addAuthorizationToken(headers, username, password);
				if (StringUtils.hasText(token)) {
					headers.add(TOKEN_HEADER, token);
				}
				if (StringUtils.hasText(state) && properties.isSendState()) {
					headers.add(STATE_HEADER, state);
				}

				final HttpEntity<Void> entity = new HttpEntity<>((Void) null, headers);
				response = restTemplate.exchange(uri + path, HttpMethod.GET, entity, Environment.class, args);
			}
			catch (HttpClientErrorException e) {
				if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
					throw e;
				}
			}
			catch (ResourceAccessException e) {
				logger.info("Connect Timeout Exception on Url - " + uri + ". Will be trying the next url if available");
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

	/**
	 * Adds the provided headers to the request.
	 */
	@Deprecated
	public static class GenericRequestHeaderInterceptor
			extends ConfigClientRequestTemplateFactory.GenericRequestHeaderInterceptor {

		public GenericRequestHeaderInterceptor(Map<String, String> headers) {
			super(headers);
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
			return "Config Server " + this.remotePropertySource + ":" + this.origin.toString();
		}

	}

}
