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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Dave Syer
 *
 */
@ConfigurationProperties("spring.cloud.config")
@Order(0)
public class ConfigServicePropertySourceLocator implements PropertySourceLocator {

	private String env = "default";

	@Value("${spring.application.name:'application'}")
	private String name;

	private String label = "master";

	private String username;

	private String password;

	private String uri = "http://localhost:8888";

	private Discovery discovery = new Discovery();

	private RestTemplate restTemplate;

	@Override
	public org.springframework.core.env.PropertySource<?> locate() {
		CompositePropertySource composite = new CompositePropertySource("configService");
		RestTemplate restTemplate = this.restTemplate == null ? getSecureRestTemplate()
				: this.restTemplate;
		Environment result = restTemplate.exchange(getUri() + "/{name}/{env}/{label}",
				HttpMethod.GET, new HttpEntity<Void>((Void) null), Environment.class,
				name, env, label).getBody();
		for (PropertySource source : result.getPropertySources()) {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>) source.getSource();
			composite.addPropertySource(new MapPropertySource(source.getName(), map));
		}
		return composite;
	}

	public void setRestTemplate(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	public String getUri() {
		return extractCredentials()[2];
	}

	public void setUri(String url) {
		this.uri = url;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEnv() {
		return env;
	}

	public void setEnv(String env) {
		this.env = env;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getUsername() {
		return extractCredentials()[0];
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return extractCredentials()[1];
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public Discovery getDiscovery() {
		return discovery;
	}

	private RestTemplate getSecureRestTemplate() {
		RestTemplate template = new RestTemplate();
		String[] userInfo = extractCredentials();
		if (userInfo[1] != null) {
			template.setInterceptors(Arrays
					.<ClientHttpRequestInterceptor> asList(new BasicAuthorizationInterceptor(
							userInfo[0], userInfo[1])));
		}
		return template;
	}

	private String[] extractCredentials() {
		String[] result = new String[3];
		String uri = this.uri;
		result[2] = uri;
		String[] creds = getUsernamePassword();
		result[0] = creds[0];
		result[1] = creds[1];
		try {
			URL url = new URL(uri);
			String userInfo = url.getUserInfo();
			if (StringUtils.isEmpty(userInfo) || ":".equals(userInfo)) {
				return result;
			}
			String bare = UriComponentsBuilder.fromHttpUrl(uri).userInfo(null).build()
					.toUriString();
			result[2] = bare;
			if (!userInfo.contains(":")) {
				userInfo = userInfo + ":";
			}
			String[] split = userInfo.split(":");
			result[0] = split[0];
			result[1] = split[1];
			if (creds[1] != null) {
				// Explicit username / password takes precedence
				result[1] = creds[1];
				if ("user".equals(creds[0])) {
					// But the username can be overridden
					result[0] = split[0];
				}
			}
			return result;
		}
		catch (MalformedURLException e) {
			throw new IllegalStateException("Invalid URL: " + uri);
		}
	}

	private String[] getUsernamePassword() {
		if (StringUtils.hasText(password)) {
			return new String[] {
					StringUtils.hasText(username) ? username.trim() : "user",
					password.trim() };
		}
		return new String[2];
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

	public static class Discovery {
		public static final String DEFAULT_CONFIG_SERVER = "CONFIGSERVER";

		private boolean enabled;
		private String serviceId = DEFAULT_CONFIG_SERVER;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getServiceId() {
			return serviceId;
		}

		public void setServiceId(String serviceId) {
			this.serviceId = serviceId;
		}
	}

}
