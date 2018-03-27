/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.config.server.environment.git;

import java.util.Optional;

import org.eclipse.jgit.api.TransportConfigCallback;

import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.environment.EnvironmentRepositoryFactory;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * @author Dylan Roberts
 */
public class MultipleJGitEnvironmentRepositoryFactory implements EnvironmentRepositoryFactory<MultipleJGitEnvironmentRepository,
		MultipleJGitEnvironmentProperties> {
	private ConfigurableEnvironment environment;
	private ConfigServerProperties server;
	private Optional<TransportConfigCallback> transportConfigCallback;

	public MultipleJGitEnvironmentRepositoryFactory(ConfigurableEnvironment environment, ConfigServerProperties server,
													Optional<TransportConfigCallback> transportConfigCallback) {
		this.environment = environment;
		this.server = server;
		this.transportConfigCallback = transportConfigCallback;
	}

	@Override
	public MultipleJGitEnvironmentRepository build(MultipleJGitEnvironmentProperties environmentProperties) {
		MultipleJGitEnvironmentRepository repository = new MultipleJGitEnvironmentRepository(environment,
				environmentProperties);
		repository.setTransportConfigCallback(transportConfigCallback.orElse(null));
		if (server.getDefaultLabel() != null) {
			repository.setDefaultLabel(server.getDefaultLabel());
		}
		return repository;
	}
}
