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
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.config.Environment;
import org.springframework.cloud.config.PropertySource;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.crypto.codec.Base64;
import org.springframework.web.client.RestTemplate;

/**
 * @author Dave Syer
 *
 */
@Order(0)
public class ConfigServicePropertySourceLocator implements PropertySourceLocator {

	private static Log logger = LogFactory
			.getLog(ConfigServicePropertySourceLocator.class);

	private RestTemplate restTemplate;
	private ConfigClientProperties defaults;

	public ConfigServicePropertySourceLocator(ConfigClientProperties defaults) {
		this.defaults = defaults;
	}

	@Override
	public org.springframework.core.env.PropertySource<?> locate(
			org.springframework.core.env.Environment environment) {
		ConfigClientProperties client = defaults.override(environment);
		CompositePropertySource composite = new CompositePropertySource("configService");
		RestTemplate restTemplate = this.restTemplate == null ? getSecureRestTemplate(client)
				: this.restTemplate;
		try {
			Environment result = restTemplate.exchange(
					client.getUri() + "/{name}/{profile}/{label}", HttpMethod.GET,
					new HttpEntity<Void>((Void) null), Environment.class,
					client.getName(), client.getProfile(), client.getLabel()).getBody();
			for (PropertySource source : result.getPropertySources()) {
				@SuppressWarnings("unchecked")
				Map<String, Object> map = (Map<String, Object>) source.getSource();
				composite.addPropertySource(new MapPropertySource(source.getName(), map));
			}
			return composite;
		}
		catch (Exception e) {
			if (client != null && client.isFailFast()) {
				throw new IllegalStateException(
						"Could not locate PropertySource. The fail fast property is set, failing",
						e);
			}
			logger.error("Could not locate PropertySource: " + e.getMessage());
			return null;
		}

	}

	public void setRestTemplate(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
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
