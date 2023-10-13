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

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.config.server.environment.AwsParameterStoreEnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.ConfigTokenProvider;
import org.springframework.cloud.config.server.environment.EnvironmentConfigTokenProvider;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.support.GitCredentialsProviderFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class EnvironmentRepositoryConfigurationTests {

	@Test
	public void configTokenProviderCanBeOverridden() {
		new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(EnvironmentRepositoryConfiguration.class, TestBeans.class))
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

	@Test
	public void awsParamStoreFactoryBeanExistsWithComposite() {
		new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(EnvironmentRepositoryConfiguration.class, TestBeans.class))
				.withPropertyValues("spring.profiles.active=composite",
						"spring.cloud.config.server.composite[0].type=awsparamstore",
						"spring.cloud.config.server.composite[0].region=us-east-1",
						"spring.cloud.config.server.composite[1].type=git",
						"spring.cloud.config.server.composite[1].uri=https://test.com/Some-Test-Repo.git")
				.run((context) -> {
					assertThat(context.getBean(AwsParameterStoreEnvironmentRepositoryFactory.class)).isNotNull();
				});
	}

	@Test
	public void customGitCredentialsProvider() {
		new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(GitTestBeans.class, TestBeans.class,
						EnvironmentRepositoryConfiguration.class))
				.withPropertyValues("spring.profiles.active=git",
						"spring.cloud.config.server.git.uri=http://github.com/user/test")
				.run((context) -> {
					assertThat(context.getBean(GitCredentialsProviderFactory.class))
							.isInstanceOf(GitTestBeans.CustomGitCredentialsProviderFactory.class);
				});
	}

	@Test
	public void configServerActuatorConfigurationWithCustomHealthStatus() {
		new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(
						EnvironmentRepositoryConfigurationTests.EnableConfigurationPropertiesBeans.class,
						EnvironmentRepositoryConfiguration.ConfigServerActuatorConfiguration.class))
				.withPropertyValues("spring.cloud.config.server.health.down-health-status=CUSTOMIZED")
				.run((context) -> {
					ConfigServerHealthIndicator healthIndicator = context.getBean(ConfigServerHealthIndicator.class);
					assertThat(ReflectionTestUtils.getField(healthIndicator, "downHealthStatus"))
							.isEqualTo("CUSTOMIZED");
				});
	}

	@TestConfiguration
	public static class TestBeans {

		@Bean
		public ConfigServerProperties configServerProperties() {
			return new ConfigServerProperties();
		}

	}

	@TestConfiguration
	@AutoConfigureBefore(EnvironmentRepositoryConfiguration.class)
	public static class GitTestBeans {

		@Bean
		public GitCredentialsProviderFactory customGitCredentialsProviderFactory() {
			return new CustomGitCredentialsProviderFactory();
		}

		public static class CustomGitCredentialsProviderFactory extends GitCredentialsProviderFactory {

		}

	}

	@TestConfiguration
	@EnableConfigurationProperties
	public static class EnableConfigurationPropertiesBeans {

		@Bean
		public EnvironmentRepository environmentRepository() {
			return mock(EnvironmentRepository.class);
		}

	}

}
