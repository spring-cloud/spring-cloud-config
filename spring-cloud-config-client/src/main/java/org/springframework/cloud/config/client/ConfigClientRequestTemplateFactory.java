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
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulisesbocchio.jasyptspringboot.encryptor.SimplePBEByteEncryptor;
import com.ulisesbocchio.jasyptspringboot.encryptor.SimplePBEStringEncryptor;
import org.apache.commons.logging.Log;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;
import org.jasypt.salt.RandomSaltGenerator;

import org.springframework.cloud.configuration.SSLContextFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import static org.springframework.cloud.config.client.ConfigClientProperties.AUTHORIZATION;

public class ConfigClientRequestTemplateFactory {

	private SimplePBEStringEncryptor encryptor;

	private final Log log;

	private final ConfigClientProperties properties;

	public ConfigClientRequestTemplateFactory(Log log, ConfigClientProperties properties) {
		this.log = log;
		this.properties = properties;
		this.encryptor = new SimplePBEStringEncryptor(buildEncryptor());
	}

	public Log getLog() {
		return this.log;
	}

	public ConfigClientProperties getProperties() {
		return this.properties;
	}

	public SimplePBEByteEncryptor buildEncryptor() {
		SimplePBEByteEncryptor byteEncryptor = new SimplePBEByteEncryptor();
		byteEncryptor.setPassword(System.getProperty("encryptor-password"));
		byteEncryptor.setSaltGenerator(new RandomSaltGenerator());
		byteEncryptor.setIterations(properties.getEncryptorIterations());
		if (StringUtils.hasText(properties.getEncryptorAlgorithm())) {
			byteEncryptor.setAlgorithm(properties.getEncryptorAlgorithm());
		}
		return byteEncryptor;
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
		if (StringUtils.hasText(properties.getTokenUri())) {
			Optional<AccessTokenResponse> responseOpt = getOAuthToken(template, properties.getTokenUri());
			if (responseOpt.isPresent()) {
				AccessTokenResponse accessTokenResponse = responseOpt.get();
				headers.put(AUTHORIZATION, accessTokenResponse.getBearerHeader());
				properties.setHeaders(headers);
			}
		}

		return template;
	}

	private Optional<AccessTokenResponse> getOAuthToken(RestTemplate template, String tokenUri) {
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
		if (StringUtils.hasText(properties.getGrantType())) {
			map.put("grant_type", List.of(properties.getGrantType()));
		}
		else {
			throw new IllegalStateException("Grant type is required for OAuth2 requests.");
		}
		if (StringUtils.hasText(properties.getClientId())) {
			map.put("client_id", List.of(properties.getClientId()));
			map.put("client_secret", List.of(decryptProperty(properties.getClientSecret())));
		}
		if (StringUtils.hasText(properties.getOauthUsername())) {
			map.put("username", List.of(properties.getOauthUsername()));
			map.put("password", List.of(decryptProperty(properties.getOauthPassword())));
		}
		HttpEntity<MultiValueMap<String, String>> requestBodyFormUrlEncoded = new HttpEntity<>(map, httpHeaders);

		String tokenJson = template.postForObject(tokenUri, requestBodyFormUrlEncoded, String.class);
		return parseTokenResponse(tokenJson);
	}

	private String decryptProperty(String prop) {
		if (prop.startsWith("ENC(")) {
			prop = prop.substring(4, prop.lastIndexOf(")"));
			return encryptor.decrypt(prop);
		}
		return prop;
	}

	private Optional<AccessTokenResponse> parseTokenResponse(String tokenJson) {
		try {
			ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
					false);
			return Optional.of(objectMapper.readValue(tokenJson, AccessTokenResponse.class));
		}
		catch (JsonProcessingException e) {
			return Optional.empty();
		}
	}

	protected ClientHttpRequestFactory createHttpRequestFactory(ConfigClientProperties client) {
		if (client.getTls().isEnabled()) {
			try {
				PoolingHttpClientConnectionManager connectionManager = createConnectionManagerForTls(client);
				HttpClient httpClient = HttpClients.custom().setConnectionManager(connectionManager).build();
				HttpComponentsClientHttpRequestFactory result = new HttpComponentsClientHttpRequestFactory(httpClient);

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
				.setSSLSocketFactory(sslConnectionSocketFactoryBuilder.build()).build();
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
