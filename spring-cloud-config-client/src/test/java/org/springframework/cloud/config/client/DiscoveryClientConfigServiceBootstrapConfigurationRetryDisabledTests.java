/*
 * Copyright 2018-present the original author or authors.
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

import org.junit.jupiter.api.Test;

import static org.springframework.cloud.config.client.ConfigClientProperties.Discovery.DEFAULT_CONFIG_SERVER;

public class DiscoveryClientConfigServiceBootstrapConfigurationRetryDisabledTests
		extends BaseDiscoveryClientConfigServiceBootstrapConfigurationTests {

	private static final String RETRY_DISABLED = "spring.cloud.config.retry.enabled=false";

	@Test
	public void shouldFailWithExceptionGetConfigServerInstanceFromDiscoveryClient() throws Exception {
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
			givenDiscoveryClientReturnsNoInfo();
			setup("spring.cloud.config.discovery.enabled=true", "spring.cloud.config.fail-fast=true", RETRY_DISABLED);
		})
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("No instances found of configserver (" + DEFAULT_CONFIG_SERVER + ")");
		verifyDiscoveryClientCalledOnce();
	}

	@Test
	public void shouldFailWithMessageGetConfigServerInstanceFromDiscoveryClient() throws Exception {
		givenDiscoveryClientReturnsNoInfo();

		setup("spring.cloud.config.discovery.enabled=true", "spring.cloud.config.fail-fast=false", RETRY_DISABLED);

		expectDiscoveryClientConfigServiceBootstrapConfigurationIsSetup();
		expectConfigClientPropertiesHasDefaultConfiguration();
		verifyDiscoveryClientCalledOnce();
	}

	@Test
	public void shouldSucceedGetConfigServerInstanceFromDiscoveryClient() throws Exception {
		givenDiscoveryClientReturnsInfo();

		setup("spring.cloud.config.discovery.enabled=true", "spring.cloud.config.fail-fast=true", RETRY_DISABLED);

		expectDiscoveryClientConfigServiceBootstrapConfigurationIsSetup();
		expectConfigClientPropertiesHasConfigurationFromEureka();
		verifyDiscoveryClientCalledOnce();
	}

}
