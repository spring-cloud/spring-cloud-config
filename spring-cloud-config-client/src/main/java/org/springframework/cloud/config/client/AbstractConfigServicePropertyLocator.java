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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.crypto.codec.Base64;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

/**
 * Class that abstracts out the underlying location mechanism used to populate a
 * {@link org.springframework.core.env.CompositePropertySource} from a backing config
 * service.
 * @author Roy Kachouh
 */

public abstract class AbstractConfigServicePropertyLocator implements
		PropertySourceLocator {

	private static Log logger = LogFactory
			.getLog(AbstractConfigServicePropertyLocator.class);

	private RestTemplate restTemplate;

	private ConfigClientProperties defaults;

	public AbstractConfigServicePropertyLocator(ConfigClientProperties defaults) {
		this.defaults = defaults;
	}

	@Override
	public PropertySource<?> locate(Environment environment) {
		final ConfigClientProperties client = defaults.override(environment);
		final CompositePropertySource composite = new CompositePropertySource(
				"configService");

		Exception error;
		String errorBody = null;

		try {
			return tryLocating(client, composite, environment);
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
		if (client != null && client.isFailFast()) {
			throw new IllegalStateException(
					"Could not locate PropertySource and the fail fast property is set, failing",
					error);
		}
		logger.error("Could not locate PropertySource: "
				+ (errorBody == null ? error.getMessage() : errorBody));
		return null;
	}

	protected abstract PropertySource<?> tryLocating(ConfigClientProperties client,
			CompositePropertySource composite, Environment environment) throws Exception;

	CompositePropertySource exchange(ConfigClientProperties client,
			CompositePropertySource composite) {
		RestTemplate restTemplate = this.restTemplate == null ? getSecureRestTemplate(client)
				: this.restTemplate;
		logger.info("Attempting to load environment from source: " + client.getRawUri());
		org.springframework.cloud.config.Environment result = restTemplate.exchange(
				client.getRawUri() + "/{name}/{profile}/{label}", HttpMethod.GET,
				new HttpEntity<Void>((Void) null),
				org.springframework.cloud.config.Environment.class, client.getName(),
				client.getProfile(), client.getLabel()).getBody();
		for (org.springframework.cloud.config.PropertySource source : result
				.getPropertySources()) {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>) source.getSource();
			composite.addPropertySource(new MapPropertySource(source.getName(), map));
		}
		return composite;
	}

	void setRestTemplate(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	ConfigClientProperties getDefaults() {
		return defaults;
	}

	private RestTemplate getSecureRestTemplate(ConfigClientProperties client) {
		RestTemplate template = new RestTemplate();
		String password = client.getPassword();
		if (password != null) {
			template.setInterceptors(Arrays
					.<ClientHttpRequestInterceptor> asList(new BasicAuthorizationInterceptor(
							client.getUsername(), password)));
		}
		return template;
	}

	private static class BasicAuthorizationInterceptor implements
			ClientHttpRequestInterceptor {

		private final String username;

		private final String password;

		public BasicAuthorizationInterceptor(String username, String password) {
			this.username = username;
			this.password = (password == null ? "" : password);
		}

		@Override
		public ClientHttpResponse intercept(HttpRequest request, byte[] body,
				ClientHttpRequestExecution execution) throws IOException {
			byte[] token = Base64
					.encode((this.username + ":" + this.password).getBytes());
			request.getHeaders().add("Authorization", "Basic " + new String(token));
			return execution.execute(request, body);
		}

	}
}
