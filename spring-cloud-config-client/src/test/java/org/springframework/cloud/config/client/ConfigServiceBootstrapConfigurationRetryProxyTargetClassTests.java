/*
 * Copyright 2013-present the original author or authors.
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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The retry targets keep class-based proxies, as they did with
 * {@code @EnableRetry(proxyTargetClass = true)}, even when the application opts out of
 * class-based proxies globally.
 *
 * @author Mahammad Eminov
 */
public class ConfigServiceBootstrapConfigurationRetryProxyTargetClassTests
		extends BaseDiscoveryClientConfigServiceBootstrapConfigurationTests {

	@Test
	public void retryTargetsAreStillProxiedByClassWhenProxyTargetClassIsDisabled() {
		givenDiscoveryClientReturnsInfoOnThirdTry();

		setup("spring.aop.proxy-target-class=false", "spring.cloud.config.discovery.enabled=true",
				"spring.cloud.config.fail-fast=true", "spring.cloud.config.retry.enabled=true",
				"spring.cloud.config.retry.maxAttempts=3", "spring.cloud.config.retry.initialInterval=10");

		assertThat(this.context.getBean(ConfigServerInstanceProvider.class)).isNotNull();
		verifyDiscoveryClientCalledThreeTimes();
		expectConfigClientPropertiesHasConfigurationFromEureka();

		ConfigServicePropertySourceLocator locator = this.context.getBean(ConfigServicePropertySourceLocator.class);
		AtomicInteger requests = new AtomicInteger();
		RestTemplate restTemplate = new RestTemplate();
		restTemplate.getInterceptors().add((request, body, execution) -> {
			requests.incrementAndGet();
			throw new IOException("boom");
		});
		locator.setRestTemplate(restTemplate);

		assertThatThrownBy(() -> locator.locate(this.context.getEnvironment()))
			.isInstanceOf(IllegalStateException.class);
		assertThat(requests).hasValue(3);
	}

}
