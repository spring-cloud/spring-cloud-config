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

package org.springframework.cloud.config.server.proxy;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchemeBasedRoutePlannerTest {

	private static final ProxyHostProperties SECURED_PROXY_PROPERTIES = buildProxyProperties("http.host", 8080);

	private static final ProxyHostProperties UNSECURED_PROXY_PROPERTIES = buildProxyProperties("https.host", 8443);

	@Test
	void determineProxy_should_return_https_proxy_when_target_scheme_name_is_https_and_https_proxy_provided() {
		SchemeBasedRoutePlanner planner = new SchemeBasedRoutePlanner(SECURED_PROXY_PROPERTIES,
				UNSECURED_PROXY_PROPERTIES);

		final HttpHost result = planner.determineProxy(target("https"), anyContext());

		assertThat(result.getSchemeName()).isEqualTo("https");
		assertThat(result.getHostName()).isEqualTo(SECURED_PROXY_PROPERTIES.getHost());
		assertThat(result.getPort()).isEqualTo(SECURED_PROXY_PROPERTIES.getPort());
	}

	@Test
	void determineProxy_should_return_https_proxy_when_target_scheme_name_is_http_and_no_http_proxy_specified() {
		SchemeBasedRoutePlanner planner = new SchemeBasedRoutePlanner(SECURED_PROXY_PROPERTIES, null);

		final HttpHost result = planner.determineProxy(target("http"), anyContext());

		assertThat(result.getSchemeName()).isEqualTo("https");
		assertThat(result.getHostName()).isEqualTo(SECURED_PROXY_PROPERTIES.getHost());
		assertThat(result.getPort()).isEqualTo(SECURED_PROXY_PROPERTIES.getPort());
	}

	@Test
	void determineProxy_should_return_http_proxy_when_target_scheme_name_is_http_and_http_proxy_provided() {
		SchemeBasedRoutePlanner planner = new SchemeBasedRoutePlanner(SECURED_PROXY_PROPERTIES,
				UNSECURED_PROXY_PROPERTIES);

		final HttpHost result = planner.determineProxy(target("http"), anyContext());

		assertThat(result.getSchemeName()).isEqualTo("http");
		assertThat(result.getHostName()).isEqualTo(UNSECURED_PROXY_PROPERTIES.getHost());
		assertThat(result.getPort()).isEqualTo(UNSECURED_PROXY_PROPERTIES.getPort());
	}

	@Test
	void determineProxy_should_return_http_proxy_when_target_scheme_name_is_https_and_https_proxy_provided() {
		SchemeBasedRoutePlanner planner = new SchemeBasedRoutePlanner(null, UNSECURED_PROXY_PROPERTIES);

		final HttpHost result = planner.determineProxy(target("https"), anyContext());

		assertThat(result.getSchemeName()).isEqualTo("http");
		assertThat(result.getHostName()).isEqualTo(UNSECURED_PROXY_PROPERTIES.getHost());
		assertThat(result.getPort()).isEqualTo(UNSECURED_PROXY_PROPERTIES.getPort());
	}

	@Test
	void determineProxy_should_return_null_when_provided_proxies_are_incomplete() {
		SchemeBasedRoutePlanner planner = new SchemeBasedRoutePlanner(buildProxyProperties("", 777),
				buildProxyProperties("host", 0));

		final HttpHost result = planner.determineProxy(target("https"), anyContext());

		assertThat(result).isNull();
	}

	private HttpHost target(String scheme) {
		HttpHost host = mock(HttpHost.class);
		when(host.getSchemeName()).thenReturn(scheme);
		return host;
	}

	private HttpContext anyContext() {
		return mock(HttpContext.class);
	}

	private static ProxyHostProperties buildProxyProperties(String host, int port) {
		ProxyHostProperties properties = new ProxyHostProperties();
		properties.setHost(host);
		properties.setPort(port);
		return properties;
	}

}
