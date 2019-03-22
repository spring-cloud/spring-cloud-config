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

import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.SystemDefaultCredentialsProvider;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.ssl.SSLContextBuilder;

import org.springframework.cloud.config.server.proxy.ProxyHostCredentialsProvider;
import org.springframework.cloud.config.server.proxy.ProxyHostProperties;
import org.springframework.cloud.config.server.proxy.SchemeBasedRoutePlanner;
import org.springframework.util.CollectionUtils;

/**
 * @author Dylan Roberts
 */
public final class HttpClientSupport {

	private HttpClientSupport() {
		throw new IllegalStateException("Can't instantiate a utility class");
	}

	public static HttpClientBuilder builder(
			HttpEnvironmentRepositoryProperties environmentProperties)
			throws GeneralSecurityException {
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

			httpClientBuilder
					.setRoutePlanner(new SchemeBasedRoutePlanner(httpsProxy, httpProxy));
			httpClientBuilder.setDefaultCredentialsProvider(
					new ProxyHostCredentialsProvider(httpProxy, httpsProxy));
		}
		else {
			httpClientBuilder.setRoutePlanner(
					new SystemDefaultRoutePlanner(ProxySelector.getDefault()));
			httpClientBuilder.setDefaultCredentialsProvider(
					new SystemDefaultCredentialsProvider());
		}

		int timeout = environmentProperties.getTimeout() * 1000;
		return httpClientBuilder.setSSLContext(sslContextBuilder.build())
				.setDefaultRequestConfig(RequestConfig.custom().setSocketTimeout(timeout)
						.setConnectTimeout(timeout).build());
	}

}
