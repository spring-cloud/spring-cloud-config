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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyHostPropertiesTest {

	@Test
	void connectionInformationProvided_should_return_false_when_host_is_null() {
		ProxyHostProperties properties = proxyHost(null, 8080);

		final boolean result = properties.connectionInformationProvided();

		assertThat(result).isFalse();
	}

	@Test
	void connectionInformationProvided_should_return_false_when_host_is_empty() {
		ProxyHostProperties properties = proxyHost("", 8080);

		final boolean result = properties.connectionInformationProvided();

		assertThat(result).isFalse();
	}

	@Test
	void connectionInformationProvided_should_return_false_when_port_is_null() {
		ProxyHostProperties properties = proxyHost("host.address", 0);

		final boolean result = properties.connectionInformationProvided();

		assertThat(result).isFalse();
	}

	@Test
	void connectionInformationProvided_should_return_true_when_port_is_filled_and_port_positive() {
		ProxyHostProperties properties = proxyHost("host.address", 8080);

		final boolean result = properties.connectionInformationProvided();

		assertThat(result).isTrue();
	}

	@Test
	void authenticationProvided_should_return_false_if_username_is_null() {
		ProxyHostProperties properties = proxyHostWithCredentials(null, "P@s$W0rD!");

		final boolean result = properties.authenticationProvided();

		assertThat(result).isFalse();
	}

	@Test
	void authenticationProvided_should_return_false_if_password_is_null() {
		ProxyHostProperties properties = proxyHostWithCredentials("username", null);

		final boolean result = properties.authenticationProvided();

		assertThat(result).isFalse();
	}

	@Test
	void authenticationProvided_should_return_true_if_username_and_password_are_provided() {
		ProxyHostProperties properties = proxyHostWithCredentials("username", "P@s$W0rD!");

		final boolean result = properties.authenticationProvided();

		assertThat(result).isTrue();
	}

	private ProxyHostProperties proxyHost(String host, int port) {
		ProxyHostProperties properties = new ProxyHostProperties();
		properties.setHost(host);
		properties.setPort(port);
		return properties;
	}

	private ProxyHostProperties proxyHostWithCredentials(String username, String password) {
		ProxyHostProperties properties = new ProxyHostProperties();
		properties.setUsername(username);
		properties.setPassword(password);
		return properties;
	}

}
