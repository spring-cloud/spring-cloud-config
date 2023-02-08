/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.config.server.environment;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.support.GitCredentialsProviderFactory;
import org.springframework.cloud.config.server.support.TransportConfigCallbackFactory;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class MultipleJGitEnvironmentRepositoryFactoryTests {

	private MultipleJGitEnvironmentRepositoryFactory multipleJGitEnvironmentRepositoryFactory;

	ConfigurableEnvironment environment = mock(ConfigurableEnvironment.class);

	ConfigServerProperties server = mock(ConfigServerProperties.class);

	Optional<ConfigurableHttpConnectionFactory> connectionFactory = Optional.empty();

	TransportConfigCallbackFactory transportConfigCallbackFactory = mock(TransportConfigCallbackFactory.class);

	GitCredentialsProviderFactory gitCredentialsProviderFactory;

	@Test
	public void buildGitCredentialsFactory() throws Exception {

		multipleJGitEnvironmentRepositoryFactory = new MultipleJGitEnvironmentRepositoryFactory(environment, server,
				connectionFactory, transportConfigCallbackFactory, gitCredentialsProviderFactory);
		MultipleJGitEnvironmentProperties multipleJGitEnvironmentProperties = new MultipleJGitEnvironmentProperties();
		Map<String, MultipleJGitEnvironmentProperties.PatternMatchingJGitEnvironmentProperties> patternMatchingJGitEnvironmentPropertiesMap = new HashMap<>();
		patternMatchingJGitEnvironmentPropertiesMap.put("pattenMatchingGitRepo1",
				new MultipleJGitEnvironmentProperties.PatternMatchingJGitEnvironmentProperties());
		multipleJGitEnvironmentProperties.setRepos(patternMatchingJGitEnvironmentPropertiesMap);
		MultipleJGitEnvironmentRepository multipleJGitEnvironmentRepository = multipleJGitEnvironmentRepositoryFactory
				.build(multipleJGitEnvironmentProperties);
		assertThat(multipleJGitEnvironmentRepository.getGitCredentialsProviderFactory())
				.isSameAs(gitCredentialsProviderFactory);
		MultipleJGitEnvironmentRepository.PatternMatchingJGitEnvironmentRepository pattenMatchingGitRepo = multipleJGitEnvironmentRepository
				.getRepos().get("pattenMatchingGitRepo1");
		assertThat(pattenMatchingGitRepo.getGitCredentialsProviderFactory()).isSameAs(gitCredentialsProviderFactory);

	}

}
