/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.config.server.config;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.server.environment.HttpClientVaultRestTemplateFactory;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties;
import org.springframework.cloud.config.server.proxy.ProxyHostProperties;
import org.springframework.web.client.RestTemplate;

/**
 * @author Dylan Roberts
 */
public class HttpClientVaultRestTemplateFactoryTest {

	private static final ProxyHostProperties AUTHENTICATED_HTTP_PROXY = new ProxyHostProperties();

	private static final ProxyHostProperties AUTHENTICATED_HTTPS_PROXY = new ProxyHostProperties();

	private static final ProxyHostProperties HTTP_PROXY = new ProxyHostProperties();

	private static final ProxyHostProperties HTTPS_PROXY = new ProxyHostProperties();

	static {
		AUTHENTICATED_HTTP_PROXY.setHost("https://authenticated.http.proxy");
		AUTHENTICATED_HTTP_PROXY.setPort(8080);
		AUTHENTICATED_HTTP_PROXY.setUsername("username");
		AUTHENTICATED_HTTP_PROXY.setPassword("password");
	}

	static {
		AUTHENTICATED_HTTPS_PROXY.setHost("https://authenticated.https.proxy");
		AUTHENTICATED_HTTPS_PROXY.setPort(8081);
		AUTHENTICATED_HTTPS_PROXY.setUsername("username2");
		AUTHENTICATED_HTTPS_PROXY.setPassword("password2");
	}

	static {
		HTTP_PROXY.setHost("https://http.proxy");
		HTTP_PROXY.setPort(8080);
	}

	static {
		HTTPS_PROXY.setHost("https://https.proxy");
		HTTPS_PROXY.setPort(8081);
	}

	private HttpClientVaultRestTemplateFactory factory;

	@BeforeEach
	public void setUp() {
		this.factory = new HttpClientVaultRestTemplateFactory();
	}

	@Test
	public void authenticatedHttpsProxy() throws Exception {
		Assertions.assertThatThrownBy(() -> {
			VaultEnvironmentProperties properties = getVaultEnvironmentProperties(null, AUTHENTICATED_HTTPS_PROXY);
			RestTemplate restTemplate = this.factory.build(properties);
			restTemplate.getForObject("https://somehost", String.class);
		}).hasCauseInstanceOf(UnknownHostException.class).hasMessageContaining(AUTHENTICATED_HTTPS_PROXY.getHost());
	}

	@Test
	public void httpsProxy() throws Exception {
		Assertions.assertThatThrownBy(() -> {
			VaultEnvironmentProperties properties = getVaultEnvironmentProperties(null, HTTPS_PROXY);
			RestTemplate restTemplate = this.factory.build(properties);
			restTemplate.getForObject("https://somehost", String.class);
		}).hasCauseInstanceOf(UnknownHostException.class).hasMessageContaining(HTTPS_PROXY.getHost());
	}

	@Test
	public void httpsProxy_called_for_http_request_when_no_httpProxy_specified() throws Exception {
		Assertions.assertThatThrownBy(() -> {
			VaultEnvironmentProperties properties = getVaultEnvironmentProperties(null, HTTPS_PROXY);
			RestTemplate restTemplate = this.factory.build(properties);
			restTemplate.getForObject("http://somehost", String.class);
		}).hasCauseInstanceOf(UnknownHostException.class).hasMessageContaining(HTTPS_PROXY.getHost());
	}

	@Test
	public void authenticatedHttpProxy() throws Exception {
		Assertions.assertThatThrownBy(() -> {
			VaultEnvironmentProperties properties = getVaultEnvironmentProperties(AUTHENTICATED_HTTP_PROXY, null);
			RestTemplate restTemplate = this.factory.build(properties);
			restTemplate.getForObject("http://somehost", String.class);
		}).hasCauseInstanceOf(UnknownHostException.class).hasMessageContaining(AUTHENTICATED_HTTP_PROXY.getHost());
	}

	@Test
	public void httpProxy() throws Exception {
		Assertions.assertThatThrownBy(() -> {
			VaultEnvironmentProperties properties = getVaultEnvironmentProperties(HTTP_PROXY, null);
			RestTemplate restTemplate = this.factory.build(properties);
			restTemplate.getForObject("http://somehost", String.class);
		}).hasCauseInstanceOf(UnknownHostException.class).hasMessageContaining(HTTP_PROXY.getHost());
	}

	@Test
	public void httpProxy_called_for_https_request_when_no_httpsProxy_specified() throws Exception {
		Assertions.assertThatThrownBy(() -> {
			VaultEnvironmentProperties properties = getVaultEnvironmentProperties(HTTP_PROXY, null);
			RestTemplate restTemplate = this.factory.build(properties);
			restTemplate.getForObject("https://somehost", String.class);
		}).hasCauseInstanceOf(UnknownHostException.class).hasMessageContaining(HTTP_PROXY.getHost());
	}

	private VaultEnvironmentProperties getVaultEnvironmentProperties(ProxyHostProperties httpProxy,
			ProxyHostProperties httpsProxy) {
		Map<ProxyHostProperties.ProxyForScheme, ProxyHostProperties> proxyMap = new HashMap<>();
		proxyMap.put(ProxyHostProperties.ProxyForScheme.HTTP, httpProxy);
		proxyMap.put(ProxyHostProperties.ProxyForScheme.HTTPS, httpsProxy);
		VaultEnvironmentProperties properties = new VaultEnvironmentProperties();
		properties.setProxy(proxyMap);
		return properties;
	}

}
