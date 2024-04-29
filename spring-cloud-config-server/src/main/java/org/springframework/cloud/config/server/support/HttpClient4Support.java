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

package org.springframework.cloud.config.server.support;

import java.net.ProxySelector;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.SystemDefaultCredentialsProvider;
import org.apache.http.impl.conn.DefaultRoutePlanner;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContextBuilder;

import org.springframework.cloud.config.server.environment.HttpClient4BuilderCustomizer;
import org.springframework.cloud.config.server.proxy.ProxyHostProperties;
import org.springframework.util.CollectionUtils;

/**
 * @author Dylan Roberts
 */
public final class HttpClient4Support {

	private HttpClient4Support() {
		throw new IllegalStateException("Can't instantiate a utility class");
	}

	public static HttpClientBuilder builder(HttpEnvironmentRepositoryProperties environmentProperties)
			throws GeneralSecurityException {
		return builder(environmentProperties, Collections.EMPTY_LIST);
	}

	public static HttpClientBuilder builder(HttpEnvironmentRepositoryProperties environmentProperties,
			List<HttpClient4BuilderCustomizer> customizers) throws GeneralSecurityException {
		SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
		HttpClientBuilder httpClientBuilder = HttpClients.custom();

		if (environmentProperties.isSkipSslValidation()) {
			sslContextBuilder.loadTrustMaterial(null, (certificate, authType) -> true);
			httpClientBuilder.setSSLHostnameVerifier(new NoopHostnameVerifier());
		}

		if (!CollectionUtils.isEmpty(environmentProperties.getProxy())) {
			ProxyHostProperties httpsProxy = environmentProperties.getProxy()
					.get(ProxyHostProperties.ProxyForScheme.HTTPS);
			ProxyHostProperties httpProxy = environmentProperties.getProxy()
					.get(ProxyHostProperties.ProxyForScheme.HTTP);

			httpClientBuilder.setRoutePlanner(new SchemeBasedRoutePlanner4(httpsProxy, httpProxy));
			httpClientBuilder.setDefaultCredentialsProvider(new ProxyHostCredentialsProvider4(httpProxy, httpsProxy));
		}
		else {
			httpClientBuilder.setRoutePlanner(new SystemDefaultRoutePlanner(ProxySelector.getDefault()));
			httpClientBuilder.setDefaultCredentialsProvider(new SystemDefaultCredentialsProvider());
		}

		/*
		 * According to https://git.eclipse.org/c/jgit/jgit.git/commit/?id=
		 * e17bfc96f293744cc5c0cef306e100f53d63bb3d jGit does its own redirect handling
		 * and disables HttpClient's redirect handing.
		 */
		httpClientBuilder.disableRedirectHandling();

		int timeout = environmentProperties.getTimeout() * 1000;
		httpClientBuilder.setSSLContext(sslContextBuilder.build()).setDefaultRequestConfig(
				RequestConfig.custom().setSocketTimeout(timeout).setConnectTimeout(timeout).build());
		customizers.forEach(customizer -> customizer.customize(httpClientBuilder));
		return httpClientBuilder;
	}

	static class SchemeBasedRoutePlanner4 extends DefaultRoutePlanner {

		private final HttpHost httpsProxy;

		private final HttpHost defaultSchemeProxy;

		SchemeBasedRoutePlanner4(ProxyHostProperties httpsProxy, ProxyHostProperties httpProxy) {
			super(null);
			this.httpsProxy = buildProxy(httpsProxy, "https");
			this.defaultSchemeProxy = buildProxy(httpProxy, HttpHost.DEFAULT_SCHEME_NAME);
		}

		@Override
		protected HttpHost determineProxy(HttpHost target, HttpRequest request, HttpContext context) {
			return "https".equals(target.getSchemeName()) ? determineProxy(this.httpsProxy, this.defaultSchemeProxy)
					: determineProxy(this.defaultSchemeProxy, this.httpsProxy);
		}

		private HttpHost determineProxy(HttpHost proxy, HttpHost fallbackProxy) {
			return proxy != null ? proxy : fallbackProxy;
		}

		private HttpHost buildProxy(ProxyHostProperties properties, String scheme) {
			if (properties == null || !properties.connectionInformationProvided()) {
				return null;
			}
			return new HttpHost(properties.getHost(), properties.getPort(), scheme);
		}

	}

	static class ProxyHostCredentialsProvider4 extends BasicCredentialsProvider {

		ProxyHostCredentialsProvider4(ProxyHostProperties... proxyHostProperties) {

			for (ProxyHostProperties proxy : proxyHostProperties) {

				if (proxy != null && proxy.connectionInformationProvided() && proxy.authenticationProvided()) {
					AuthScope authscope = new AuthScope(proxy.getHost(), proxy.getPort());
					UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(proxy.getUsername(),
							proxy.getPassword());
					setCredentials(authscope, credentials);
				}
			}
		}

	}

}
