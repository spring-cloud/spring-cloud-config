/*
 * Copyright 2013-2021 the original author or authors.
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
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.commons.logging.Log;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;

import org.springframework.cloud.configuration.SSLContextFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.Base64Utils;
import org.springframework.web.client.RestTemplate;

import static org.springframework.cloud.config.client.ConfigClientProperties.AUTHORIZATION;

public class ConfigClientRequestTemplateFactory {

	private final Log log;

	private final ConfigClientProperties properties;

	public ConfigClientRequestTemplateFactory(Log log, ConfigClientProperties properties) {
		this.log = log;
		this.properties = properties;
	}

	public Log getLog() {
		return this.log;
	}

	public ConfigClientProperties getProperties() {
		return this.properties;
	}

	public RestTemplate create() {
		if (properties.getRequestReadTimeout() < 0) {
			throw new IllegalStateException("Invalid Value for Read Timeout set.");
		}
		if (properties.getRequestConnectTimeout() < 0) {
			throw new IllegalStateException("Invalid Value for Connect Timeout set.");
		}

		ClientHttpRequestFactory requestFactory = createHttpRequestFactory(properties);
		RestTemplate template = new RestTemplate(requestFactory);
		Map<String, String> headers = new HashMap<>(properties.getHeaders());
		headers.remove(AUTHORIZATION); // To avoid redundant addition of header
		if (!headers.isEmpty()) {
			template.setInterceptors(Arrays.asList(new GenericRequestHeaderInterceptor(headers)));
		}

		return template;
	}

	private ClientHttpRequestFactory createHttpRequestFactory(ConfigClientProperties client) {
		if (client.getTls().isEnabled()) {
			try {
				SSLContextFactory factory = new SSLContextFactory(client.getTls());
				SSLContext sslContext = factory.createSSLContext();
				SSLConnectionSocketFactoryBuilder sslConnectionSocketFactoryBuilder = SSLConnectionSocketFactoryBuilder
						.create();
				sslConnectionSocketFactoryBuilder.setSslContext(sslContext);
				PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder
						.create().setSSLSocketFactory(sslConnectionSocketFactoryBuilder.build()).build();
				HttpClient httpClient = HttpClients.custom().setConnectionManager(connectionManager).build();
				HttpComponentsClientHttpRequestFactory result = new HttpComponentsClientHttpRequestFactory(httpClient);

				result.setReadTimeout(client.getRequestReadTimeout());
				result.setConnectTimeout(client.getRequestConnectTimeout());
				return result;

			}
			catch (GeneralSecurityException | IOException ex) {
				log.error(ex);
				throw new IllegalStateException("Failed to create config client with TLS.", ex);
			}
		}

		SimpleClientHttpRequestFactory result = new SimpleClientHttpRequestFactory();
		result.setReadTimeout(client.getRequestReadTimeout());
		result.setConnectTimeout(client.getRequestConnectTimeout());
		return result;
	}

	public void addAuthorizationToken(HttpHeaders httpHeaders, String username, String password) {
		String authorization = properties.getHeaders().get(AUTHORIZATION);

		if (password != null && authorization != null) {
			throw new IllegalStateException("You must set either 'password' or 'authorization'");
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
	public static class GenericRequestHeaderInterceptor implements ClientHttpRequestInterceptor {

		private final Map<String, String> headers;

		public GenericRequestHeaderInterceptor(Map<String, String> headers) {
			this.headers = headers;
		}

		@Override
		public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
				throws IOException {
			for (Map.Entry<String, String> header : this.headers.entrySet()) {
				request.getHeaders().add(header.getKey(), header.getValue());
			}
			return execution.execute(request, body);
		}

		protected Map<String, String> getHeaders() {
			return this.headers;
		}

	}

}
