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

package org.springframework.cloud.config.client.oauth2;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor.ClientRegistrationIdResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ConfigClientOAuth2BootstrapConfiguration}.
 */
class ConfigClientOAuth2BootstrapConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withPropertyValues(
			"spring.cloud.config.oauth2.enabled=true",
			"spring.security.oauth2.client.registration.config-client.client-id=config-client",
			"spring.security.oauth2.client.registration.config-client.client-secret=secret",
			"spring.security.oauth2.client.registration.config-client.authorization-grant-type=client_credentials",
			"spring.security.oauth2.client.registration.config-client.provider=test",
			"spring.security.oauth2.client.provider.test.token-uri=http://localhost/token");

	@Test
	void userSuppliedClientRegistrationIdResolverWinsWithoutClientRegistrationId() {
		// Bootstrap configurations are regular configurations, so the user's may be
		// processed after ours.
		this.contextRunner
			.withConfiguration(UserConfigurations.of(ConfigClientOAuth2BootstrapConfiguration.class,
					UserResolverConfiguration.class))
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).getBeans(ClientRegistrationIdResolver.class)
					.containsOnlyKeys("userClientRegistrationIdResolver");
				assertThat(context).hasBean(ConfigClientOAuth2BootstrapConfiguration.OAUTH2_INTERCEPTOR_BEAN_NAME);
			});
	}

	@Test
	void failsWhenClientRegistrationIdMissingAndNoResolverSupplied() {
		this.contextRunner.withConfiguration(UserConfigurations.of(ConfigClientOAuth2BootstrapConfiguration.class))
			.run(context -> assertThat(context).hasFailed()
				.getFailure()
				.rootCause()
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("spring.cloud.config.oauth2.client-registration-id"));
	}

	@Configuration(proxyBeanMethods = false)
	static class UserResolverConfiguration {

		@Bean
		ClientRegistrationIdResolver userClientRegistrationIdResolver() {
			return request -> "config-client";
		}

	}

}
