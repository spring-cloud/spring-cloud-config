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
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.SystemDefaultCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.ssl.SSLContextBuilder;

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

	public static HttpClientBuilder builder(HttpEnvironmentRepositoryProperties environmentProperties)
			throws GeneralSecurityException {
		HttpClientBuilder httpClientBuilder = HttpClients.custom();
		PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder = PoolingHttpClientConnectionManagerBuilder
				.create();

		if (environmentProperties.isSkipSslValidation()) {
			SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
			sslContextBuilder.loadTrustMaterial(null, (certificate, authType) -> true);
			SSLConnectionSocketFactory sslConnectionSocketFactory = SSLConnectionSocketFactoryBuilder.create()
					.setSslContext(sslContextBuilder.build()).setHostnameVerifier(new NoopHostnameVerifier()).build();
			connectionManagerBuilder.setSSLSocketFactory(sslConnectionSocketFactory);
		}

		if (!CollectionUtils.isEmpty(environmentProperties.getProxy())) {
			ProxyHostProperties httpsProxy = environmentProperties.getProxy()
					.get(ProxyHostProperties.ProxyForScheme.HTTPS);
			ProxyHostProperties httpProxy = environmentProperties.getProxy()
					.get(ProxyHostProperties.ProxyForScheme.HTTP);

			httpClientBuilder.setRoutePlanner(new SchemeBasedRoutePlanner(httpsProxy, httpProxy));
			httpClientBuilder.setDefaultCredentialsProvider(new ProxyHostCredentialsProvider(httpProxy, httpsProxy));
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
		connectionManagerBuilder
				.setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(timeout, TimeUnit.MILLISECONDS).build());
		return httpClientBuilder.setConnectionManager(connectionManagerBuilder.build()).setDefaultRequestConfig(
				RequestConfig.custom().setConnectTimeout(timeout, TimeUnit.MILLISECONDS).build());
	}

}
