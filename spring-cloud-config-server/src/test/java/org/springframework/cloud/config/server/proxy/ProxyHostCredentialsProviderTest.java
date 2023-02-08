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

import java.util.Map;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.junit.jupiter.api.Test;

import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyHostCredentialsProviderTest {

	@Test
	void should_take_only_proxy_with_connection_and_credentials_information_provided() {
		ProxyHostProperties withoutConnection = proxyHost(null, 0, "user", "password");
		ProxyHostProperties withoutCredentials = proxyHost("bad.proxy", 666, null, null);
		ProxyHostProperties goodProxy = proxyHost("good.proxy", 888, "user", "P@s$W0rd!");

		ProxyHostCredentialsProvider provider = new ProxyHostCredentialsProvider(withoutConnection, withoutCredentials,
				goodProxy);

		Map<AuthScope, Credentials> credentials = (Map<AuthScope, Credentials>) ReflectionTestUtils.getField(provider,
				"credMap");
		assertThat(credentials).hasSize(1);
		Map.Entry<AuthScope, Credentials> entry = credentials.entrySet().iterator().next();
		assertThat(entry.getKey().getHost()).isEqualTo("good.proxy");
		assertThat(entry.getKey().getPort()).isEqualTo(888);
		assertThat(entry.getValue().getUserPrincipal().getName()).isEqualTo("user");
		assertThat(new String(entry.getValue().getPassword())).isEqualTo("P@s$W0rd!");
	}

	private ProxyHostProperties proxyHost(String host, int port, String username, String password) {
		ProxyHostProperties result = new ProxyHostProperties();
		result.setHost(host);
		result.setPort(port);
		result.setUsername(username);
		result.setPassword(password);
		return result;
	}

}
