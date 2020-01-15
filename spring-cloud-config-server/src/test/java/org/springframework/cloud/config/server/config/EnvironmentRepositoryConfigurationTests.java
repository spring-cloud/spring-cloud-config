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

package org.springframework.cloud.config.server.config;

import org.junit.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.config.server.environment.ConfigTokenProvider;
import org.springframework.cloud.config.server.environment.EnvironmentConfigTokenProvider;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

public class EnvironmentRepositoryConfigurationTests {

	@Test
	public void configTokenProviderCanBeOverridden() {
		new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations
						.of(EnvironmentRepositoryConfiguration.class, TestBeans.class))
				.withPropertyValues("spring.profiles.active=composite",
						"spring.cloud.config.server.vault.authentication=TOKEN",
						"spring.cloud.config.server.vault.token=testTokenValue",
						"spring.cloud.config.server.composite[0].type=vault",
						"spring.cloud.config.server.composite[1].type=git",
						"spring.cloud.config.server.composite[1].uri=https://test.com/Some-Test-Repo.git")
				.run((context) -> {
					assertThat(context.getBean(ConfigTokenProvider.class)).isNotNull();
					assertThat(context.getBean(ConfigTokenProvider.class))
							.isInstanceOf(EnvironmentConfigTokenProvider.class);
					EnvironmentConfigTokenProvider tokenProvider = context
							.getBean(EnvironmentConfigTokenProvider.class);
					assertThat(tokenProvider.getToken()).isEqualTo("testTokenValue");
				});
	}

	@TestConfiguration
	public static class TestBeans {

		@Bean
		public ConfigServerProperties vaultConfigServerProperties() {
			ConfigServerProperties configServerProperties = new ConfigServerProperties();
			return configServerProperties;
		}

	}

}
