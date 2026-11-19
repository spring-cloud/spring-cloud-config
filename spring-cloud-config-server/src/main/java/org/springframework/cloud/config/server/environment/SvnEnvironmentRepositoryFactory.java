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

package org.springframework.cloud.config.server.environment;

import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * @author Dylan Roberts
 */
public class SvnEnvironmentRepositoryFactory
		implements EnvironmentRepositoryFactory<SvnKitEnvironmentRepository, SvnKitEnvironmentProperties> {

	private ConfigurableEnvironment environment;

	private ConfigServerProperties server;

	private final ObservationRegistry observationRegistry;

	private final ObservationConvention<ObservationEnvironmentRepositoryContext> convention;

	public SvnEnvironmentRepositoryFactory(ConfigurableEnvironment environment, ConfigServerProperties server,
			ObservationRegistry observationRegistry) {
		this(environment, server, observationRegistry, null);
	}

	public SvnEnvironmentRepositoryFactory(ConfigurableEnvironment environment, ConfigServerProperties server,
			ObservationRegistry observationRegistry,
			ObservationConvention<ObservationEnvironmentRepositoryContext> convention) {
		this.environment = environment;
		this.server = server;
		this.observationRegistry = observationRegistry;
		this.convention = convention;
	}

	@Override
	public SvnKitEnvironmentRepository build(SvnKitEnvironmentProperties environmentProperties) {
		SvnKitEnvironmentRepository repository = new SvnKitEnvironmentRepository(this.environment,
				environmentProperties, this.observationRegistry, this.convention);
		if (this.server.getDefaultLabel() != null) {
			repository.setDefaultLabel(this.server.getDefaultLabel());
		}
		return repository;
	}

}
