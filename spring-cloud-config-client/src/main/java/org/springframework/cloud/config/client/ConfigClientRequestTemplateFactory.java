/*
 * Copyright 2013-present the original author or authors.
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.apache.commons.logging.Log;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;

import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientPropertiesMapper;
import org.springframework.cloud.configuration.SSLContextFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
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

		final List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
		Map<String, String> headers = new HashMap<>(properties.getHeaders());
		headers.remove(AUTHORIZATION); // To avoid redundant addition of header
		if (!headers.isEmpty()) {
			interceptors.add(new GenericRequestHeaderInterceptor(headers));
		}

		if (properties.getOauth2().isEnabled()) {
			ClientHttpRequestInterceptor oauth2Interceptor = createOauth2Interceptor(properties.getOauth2());
			interceptors.add(oauth2Interceptor);
		}
		template.setInterceptors(interceptors);

		return template;
	}

	private ClientHttpRequestInterceptor createOauth2Interceptor(ConfigClientProperties.OAuth2Properties properties) {
		final OAuth2AuthorizedClientManager authorizedClientManager = createAuthorizedClientManager(properties);
		OAuth2ClientHttpRequestInterceptor oauth2Interceptor = new OAuth2ClientHttpRequestInterceptor(
				authorizedClientManager);
		oauth2Interceptor
			.setClientRegistrationIdResolver(request -> ConfigClientProperties.OAuth2Properties.CLIENT_REGISTRATION_ID);
		return oauth2Interceptor;
	}

	private OAuth2AuthorizedClientManager createAuthorizedClientManager(
			ConfigClientProperties.OAuth2Properties properties) {

		OAuth2AuthorizedClientProvider authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
			.clientCredentials()
			.refreshToken()
			.build();

		ClientRegistrationRepository clientRegistrationRepository = clientRegistrationRepository(properties);

		OAuth2AuthorizedClientService authorizedClientService = new InMemoryOAuth2AuthorizedClientService(
				clientRegistrationRepository);

		AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
				clientRegistrationRepository, authorizedClientService);
		authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);
		return authorizedClientManager;
	}

	private ClientRegistrationRepository clientRegistrationRepository(
			ConfigClientProperties.OAuth2Properties properties) {
		OAuth2ClientProperties oauth2ClientProperties = new OAuth2ClientProperties();
		properties.getRegistration().setProvider(null); // In case it was set in config
														// properties
		oauth2ClientProperties.getRegistration()
			.put(ConfigClientProperties.OAuth2Properties.CLIENT_REGISTRATION_ID, properties.getRegistration());
		oauth2ClientProperties.getProvider()
			.put(ConfigClientProperties.OAuth2Properties.CLIENT_REGISTRATION_ID, properties.getProvider());
		oauth2ClientProperties.afterPropertiesSet();

		List<ClientRegistration> registrations = new ArrayList<>(
				new OAuth2ClientPropertiesMapper(oauth2ClientProperties).asClientRegistrations().values());
		return new InMemoryClientRegistrationRepository(registrations);
	}

	protected ClientHttpRequestFactory createHttpRequestFactory(ConfigClientProperties client) {
		if (client.getTls().isEnabled()) {
			try {
				PoolingHttpClientConnectionManager connectionManager = createConnectionManagerForTls(client);
				HttpClient httpClient = HttpClients.custom().setConnectionManager(connectionManager).build();
				HttpComponentsClientHttpRequestFactory result = new HttpComponentsClientHttpRequestFactory(httpClient);

				result.setConnectionRequestTimeout(client.getRequestConnectTimeout());
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

	protected PoolingHttpClientConnectionManager createConnectionManagerForTls(ConfigClientProperties client)
			throws GeneralSecurityException, IOException {
		SSLContextFactory factory = new SSLContextFactory(client.getTls());
		SSLContext sslContext = factory.createSSLContext();
		SSLConnectionSocketFactoryBuilder sslConnectionSocketFactoryBuilder = SSLConnectionSocketFactoryBuilder
			.create();
		sslConnectionSocketFactoryBuilder.setSslContext(sslContext);
		SocketConfig.Builder socketBuilder = createSocketBuilderForTls(client);
		PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
			.setDefaultSocketConfig(socketBuilder.build())
			.setSSLSocketFactory(sslConnectionSocketFactoryBuilder.build())
			.build();
		return connectionManager;
	}

	protected SocketConfig.Builder createSocketBuilderForTls(ConfigClientProperties client) {
		SocketConfig.Builder socketBuilder = SocketConfig.custom()
			.setSoTimeout(Timeout.of(client.getRequestReadTimeout(), TimeUnit.MILLISECONDS));
		return socketBuilder;
	}

	public void addAuthorizationToken(HttpHeaders httpHeaders, String username, String password) {
		String authorization = properties.getHeaders().get(AUTHORIZATION);

		if (password != null && authorization != null) {
			throw new IllegalStateException("You must set either 'password' or 'authorization'");
		}

		if (password != null) {
			byte[] token = Base64.getEncoder().encode((username + ":" + password).getBytes());
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
