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

package org.springframework.cloud.config.server.environment;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.assertj.core.api.Assertions;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.eclipse.jgit.transport.http.apache.HttpClientConnection;
import org.junit.jupiter.api.Test;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.config.EnvironmentRepositoryConfiguration;
import org.springframework.cloud.config.server.proxy.ProxyHostProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dylan Roberts
 */
public class ConfigurableHttpConnectionFactoryIntegrationTests {

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

	@Test
	public void authenticatedHttpsProxy() throws Exception {
		Assertions.assertThatThrownBy(() -> {
			String repoUrl = "https://myrepo/repo.git";
			new SpringApplicationBuilder(TestConfiguration.class).web(WebApplicationType.NONE)
					.properties(gitProperties(repoUrl, null, AUTHENTICATED_HTTPS_PROXY)).run();
			HttpClient httpClient = getHttpClientForUrl(repoUrl);
			makeRequest(httpClient, "https://somehost");
		}).hasCauseInstanceOf(UnknownHostException.class).hasMessageContaining(AUTHENTICATED_HTTPS_PROXY.getHost());

	}

	@Test
	public void httpsProxy() throws Exception {
		Assertions.assertThatThrownBy(() -> {
			String repoUrl = "https://myrepo/repo.git";
			new SpringApplicationBuilder(TestConfiguration.class).web(WebApplicationType.NONE)
					.properties(gitProperties(repoUrl, null, HTTPS_PROXY)).run();
			HttpClient httpClient = getHttpClientForUrl(repoUrl);
			makeRequest(httpClient, "https://somehost");
		}).hasCauseInstanceOf(UnknownHostException.class).hasMessageContaining(HTTPS_PROXY.getHost());
	}

	@Test
	public void httpsProxy_placeholderUrl() throws Exception {
		Assertions.assertThatThrownBy(() -> {
			String repoUrl = "https://myrepo/{placeholder1}/{placeholder2}-repo.git";
			new SpringApplicationBuilder(TestConfiguration.class).web(WebApplicationType.NONE)
					.properties(gitProperties(repoUrl, null, HTTPS_PROXY)).run();
			HttpClient httpClient = getHttpClientForUrl(
					"https://myrepo/someplaceholdervalue/anotherplaceholdervalue-repo.git");
			makeRequest(httpClient, "https://somehost");
		}).hasCauseInstanceOf(UnknownHostException.class).hasMessageContaining(HTTPS_PROXY.getHost());
	}

	@Test
	public void httpsProxy_called_for_http_request_when_no_httpProxy_specified() throws Exception {
		Assertions.assertThatThrownBy(() -> {
			String repoUrl = "https://myrepo/repo.git";
			new SpringApplicationBuilder(TestConfiguration.class).web(WebApplicationType.NONE)
					.properties(gitProperties(repoUrl, null, HTTPS_PROXY)).run();
			HttpClient httpClient = getHttpClientForUrl(repoUrl);
			makeRequest(httpClient, "http://somehost");
		}).hasCauseInstanceOf(UnknownHostException.class).hasMessageContaining(HTTPS_PROXY.getHost());
	}

	@Test
	public void authenticatedHttpProxy() throws Exception {
		Assertions.assertThatThrownBy(() -> {
			String repoUrl = "https://myrepo/repo.git";
			new SpringApplicationBuilder(TestConfiguration.class).web(WebApplicationType.NONE)
					.properties(gitProperties(repoUrl, AUTHENTICATED_HTTP_PROXY, null)).run();
			HttpClient httpClient = getHttpClientForUrl(repoUrl);
			makeRequest(httpClient, "http://somehost");
		}).hasCauseInstanceOf(UnknownHostException.class).hasMessageContaining(AUTHENTICATED_HTTP_PROXY.getHost());
	}

	@Test
	public void httpProxy() throws Exception {
		Assertions.assertThatThrownBy(() -> {
			String repoUrl = "https://myrepo/repo.git";
			new SpringApplicationBuilder(TestConfiguration.class).web(WebApplicationType.NONE)
					.properties(gitProperties(repoUrl, HTTP_PROXY, null)).run();
			HttpClient httpClient = getHttpClientForUrl(repoUrl);
			makeRequest(httpClient, "http://somehost");
		}).hasCauseInstanceOf(UnknownHostException.class).hasMessageContaining(HTTP_PROXY.getHost());
	}

	@Test
	public void httpProxy_placeholderUrl() throws Exception {
		Assertions.assertThatThrownBy(() -> {
			String repoUrl = "https://myrepo/{placeholder}-repo.git";
			new SpringApplicationBuilder(TestConfiguration.class).web(WebApplicationType.NONE)
					.properties(gitProperties(repoUrl, HTTP_PROXY, null)).run();
			HttpClient httpClient = getHttpClientForUrl("https://myrepo/someplaceholdervalue-repo.git");
			makeRequest(httpClient, "http://somehost");
		}).hasCauseInstanceOf(UnknownHostException.class).hasMessageContaining(HTTP_PROXY.getHost());
	}

	@Test
	public void httpProxy_called_for_https_request_when_no_httpsProxy_specified() throws Exception {
		Assertions.assertThatThrownBy(() -> {
			String repoUrl = "https://myrepo/repo.git";
			new SpringApplicationBuilder(TestConfiguration.class).web(WebApplicationType.NONE)
					.properties(gitProperties(repoUrl, HTTP_PROXY, null)).run();
			HttpClient httpClient = getHttpClientForUrl(repoUrl);
			makeRequest(httpClient, "https://somehost");
		}).hasCauseInstanceOf(UnknownHostException.class).hasMessageContaining(HTTP_PROXY.getHost());
	}

	@Test
	public void httpProxy_fromSystemProperty() throws Exception {
		ProxySelector defaultProxySelector = ProxySelector.getDefault();
		try {
			Assertions.assertThatThrownBy(() -> {
				ProxySelector.setDefault(new ProxySelector() {
					@Override
					public List<Proxy> select(URI uri) {
						InetSocketAddress address = new InetSocketAddress(HTTP_PROXY.getHost(), HTTP_PROXY.getPort());
						Proxy proxy = new Proxy(Proxy.Type.HTTP, address);
						return Collections.singletonList(proxy);
					}

					@Override
					public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {

					}
				});
				String repoUrl = "https://myrepo/repo.git";
				new SpringApplicationBuilder(TestConfiguration.class).web(WebApplicationType.NONE)
						.properties(new String[] { "spring.cloud.config.server.git.uri=" + repoUrl }).run();
				HttpClient httpClient = getHttpClientForUrl(repoUrl);
				makeRequest(httpClient, "http://somehost");
			}).hasCauseInstanceOf(UnknownHostException.class).hasMessageContaining(HTTP_PROXY.getHost());
		}
		finally {
			ProxySelector.setDefault(defaultProxySelector);
		}
	}

	private String[] gitProperties(String repoUrl, ProxyHostProperties httpProxy, ProxyHostProperties httpsProxy) {
		List<String> result = new ArrayList<>();
		result.add("spring.cloud.config.server.git.uri=" + repoUrl);
		if (httpProxy != null) {
			result.addAll(Arrays.asList("spring.cloud.config.server.git.proxy.http.host=" + httpProxy.getHost(),
					"spring.cloud.config.server.git.proxy.http.port=" + httpProxy.getPort()));
			if (httpProxy.getUsername() != null && httpProxy.getPassword() != null) {
				result.addAll(
						Arrays.asList("spring.cloud.config.server.git.proxy.http.username=" + httpProxy.getUsername(),
								"spring.cloud.config.server.git.proxy.http.password=" + httpProxy.getPassword()));
			}
		}
		if (httpsProxy != null) {
			result.addAll(Arrays.asList("spring.cloud.config.server.git.proxy.https.host=" + httpsProxy.getHost(),
					"spring.cloud.config.server.git.proxy.https.port=" + httpsProxy.getPort()));
			if (httpsProxy.getUsername() != null && httpsProxy.getPassword() != null) {
				result.addAll(
						Arrays.asList("spring.cloud.config.server.git.proxy.https.username=" + httpsProxy.getUsername(),
								"spring.cloud.config.server.git.proxy.https.password=" + httpsProxy.getPassword()));
			}
		}
		return result.toArray(new String[0]);
	}

	private void makeRequest(HttpClient httpClient, String url) {
		try {
			httpClient.execute(new HttpGet(url));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private HttpClient getHttpClientForUrl(String repoUrl) throws IOException {
		HttpConnectionFactory connectionFactory = HttpTransport.getConnectionFactory();
		URL url = new URL(repoUrl);
		HttpConnection httpConnection = connectionFactory.create(url);
		assertThat(httpConnection).isInstanceOf(HttpClientConnection.class);
		return (HttpClient) ReflectionTestUtils.getField(httpConnection, "client");
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(ConfigServerProperties.class)
	@Import({ PropertyPlaceholderAutoConfiguration.class, EnvironmentRepositoryConfiguration.class })
	protected static class TestConfiguration {

	}

}
