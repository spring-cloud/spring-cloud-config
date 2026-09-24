/*
 * Copyright 2026-present the original author or authors.
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
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.environment.FileResolvingEnvironmentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link FileResolvingEnvironmentRepositoryConfiguration}.
 */
class FileResolvingEnvironmentRepositoryConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(FileResolvingEnvironmentRepositoryConfiguration.class));

	@Test
	void shouldNotConfigureByDefault() {
		this.contextRunner
			.withUserConfiguration(MockRepositoryConfiguration.class)
			.run(context -> assertThat(context).doesNotHaveBean(FileResolvingEnvironmentRepository.class));
	}

	@Test
	void shouldNotConfigureIfExplicitlyDisabled() {
		this.contextRunner
			.withUserConfiguration(MockRepositoryConfiguration.class)
			.withPropertyValues("spring.cloud.config.server.file-resolving.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(FileResolvingEnvironmentRepository.class));
	}

	@Test
	void shouldConfigureIfExplicitlyEnabled() {
		this.contextRunner
			.withUserConfiguration(MockRepositoryConfiguration.class)
			.withPropertyValues("spring.cloud.config.server.file-resolving.enabled=true")
			.run(context -> {
				assertThat(context).hasSingleBean(FileResolvingEnvironmentRepository.class);
				assertThat(context.getBean(EnvironmentRepository.class))
					.isInstanceOf(FileResolvingEnvironmentRepository.class);
			});
	}

	@Test
	void shouldNotConfigureIfDelegateIsMissing() {
		this.contextRunner
			.withPropertyValues("spring.cloud.config.server.file-resolving.enabled=true")
			.run(context -> assertThat(context).doesNotHaveBean(FileResolvingEnvironmentRepository.class));
	}

	@Configuration(proxyBeanMethods = false)
	static class MockRepositoryConfiguration {

		@Bean
		EnvironmentRepository environmentRepository() {
			return mock(EnvironmentRepository.class);
		}

	}

}
