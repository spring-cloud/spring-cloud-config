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

package org.springframework.cloud.config.client;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.cloud.test.ClassPathExclusions;
import org.springframework.cloud.test.ModifiedClassPathRunner;

@RunWith(ModifiedClassPathRunner.class)
@ClassPathExclusions({ "spring-retry-*.jar", "spring-boot-starter-aop-*.jar" })
public class DiscoveryClientConfigServiceBootstrapConfigurationNoSpringRetryTests
		extends BaseDiscoveryClientConfigServiceBootstrapConfigurationTests {

	@Test
	public void shouldFailWithExceptionGetConfigServerInstanceFromDiscoveryClient()
			throws Exception {
		givenDiscoveryClientReturnsNoInfo();

		expectNoInstancesOfConfigServerException();

		setup("spring.cloud.config.discovery.enabled=true",
				"spring.cloud.config.fail-fast=true");
	}

	@Test
	public void shouldFailWithMessageGetConfigServerInstanceFromDiscoveryClient()
			throws Exception {
		givenDiscoveryClientReturnsNoInfo();

		setup("spring.cloud.config.discovery.enabled=true",
				"spring.cloud.config.fail-fast=false");

		expectDiscoveryClientConfigServiceBootstrapConfigurationIsSetup();
		expectConfigClientPropertiesHasDefaultConfiguration();
		verifyDiscoveryClientCalledOnce();
	}

	@Test
	public void shouldSucceedGetConfigServerInstanceFromDiscoveryClient()
			throws Exception {
		givenDiscoveryClientReturnsInfo();

		setup("spring.cloud.config.discovery.enabled=true",
				"spring.cloud.config.fail-fast=true");

		expectDiscoveryClientConfigServiceBootstrapConfigurationIsSetup();
		expectConfigClientPropertiesHasConfigurationFromEureka();
		verifyDiscoveryClientCalledOnce();
	}

}
