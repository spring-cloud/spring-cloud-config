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

package org.springframework.cloud.config.server.environment;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.support.GitCredentialsProviderFactory;
import org.springframework.cloud.config.server.support.TransportConfigCallbackFactory;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
			.getRepos()
			.get("pattenMatchingGitRepo1");

		assertThat(pattenMatchingGitRepo.getGitCredentialsProviderFactory()).isSameAs(gitCredentialsProviderFactory);
	}

	@Test
	public void buildTransportConfigCallbacksForEachRepository() throws Exception {
		multipleJGitEnvironmentRepositoryFactory = new MultipleJGitEnvironmentRepositoryFactory(environment, server,
				connectionFactory, transportConfigCallbackFactory, gitCredentialsProviderFactory);

		MultipleJGitEnvironmentProperties multipleJGitEnvironmentProperties = new MultipleJGitEnvironmentProperties();
		multipleJGitEnvironmentProperties.setUri("git@github.com:main/config.git");
		multipleJGitEnvironmentProperties.setPrivateKey("main-private-key");

		MultipleJGitEnvironmentProperties.PatternMatchingJGitEnvironmentProperties nestedProperties = new MultipleJGitEnvironmentProperties.PatternMatchingJGitEnvironmentProperties();
		nestedProperties.setUri("git@github.com:other/config.git");
		nestedProperties.setPrivateKey("nested-private-key");

		Map<String, MultipleJGitEnvironmentProperties.PatternMatchingJGitEnvironmentProperties> repos = new HashMap<>();
		repos.put("nested", nestedProperties);
		multipleJGitEnvironmentProperties.setRepos(repos);

		TransportConfigCallback parentCallback = mock(TransportConfigCallback.class);
		TransportConfigCallback nestedCallback = mock(TransportConfigCallback.class);

		when(transportConfigCallbackFactory.build(multipleJGitEnvironmentProperties)).thenReturn(parentCallback);
		when(transportConfigCallbackFactory.build(nestedProperties)).thenReturn(nestedCallback);

		MultipleJGitEnvironmentRepository repository = multipleJGitEnvironmentRepositoryFactory
			.build(multipleJGitEnvironmentProperties);

		assertThat(repository.getTransportConfigCallback()).isSameAs(parentCallback);

		MultipleJGitEnvironmentRepository.PatternMatchingJGitEnvironmentRepository nestedRepository = repository
			.getRepos()
			.get("nested");

		assertThat(nestedRepository.getTransportConfigCallback()).isSameAs(nestedCallback);

		ArgumentCaptor<JGitEnvironmentProperties> captor = ArgumentCaptor.forClass(JGitEnvironmentProperties.class);

		verify(transportConfigCallbackFactory, times(2)).build(captor.capture());

		assertThat(captor.getAllValues()).containsExactly(multipleJGitEnvironmentProperties, nestedProperties);
	}

}
