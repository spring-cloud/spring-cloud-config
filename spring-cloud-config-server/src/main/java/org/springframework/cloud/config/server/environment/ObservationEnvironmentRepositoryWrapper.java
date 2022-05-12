/*
 * Copyright 2013-2021 the original author or authors.
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

import io.micrometer.observation.ObservationRegistry;

import org.springframework.cloud.config.environment.Environment;

/**
 * Wraps a {@link EnvironmentRepository} execution with observation.
 *
 * @author Marcin Grzejszczak
 * @since 4.0.0
 */
public final class ObservationEnvironmentRepositoryWrapper implements EnvironmentRepository {

	private final ObservationRegistry registry;

	private final EnvironmentRepository delegate;

	private final ObservationEnvironmentRepositoryKeyValuesProvider keyValuesProvider = new ObservationEnvironmentRepositoryKeyValuesProvider();

	/**
	 * Creates an instance of {@link ObservationEnvironmentRepositoryWrapper}.
	 * @param registry observation registry
	 * @param delegate environment repository delegate
	 */
	private ObservationEnvironmentRepositoryWrapper(ObservationRegistry registry, EnvironmentRepository delegate) {
		this.registry = registry;
		this.delegate = delegate;
	}

	/**
	 * Wraps the {@link EnvironmentRepository} in an observation representation.
	 * @param observationRegistry observation registry
	 * @param delegate repository to wrap
	 * @return wrapped repository
	 */
	public static EnvironmentRepository wrap(ObservationRegistry observationRegistry, EnvironmentRepository delegate) {
		if (delegate instanceof ObservationEnvironmentRepositoryWrapper) {
			return delegate;
		}
		return new ObservationEnvironmentRepositoryWrapper(observationRegistry, delegate);
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		ObservationEnvironmentRepositoryContext context = new ObservationEnvironmentRepositoryContext(
				this.delegate.getClass());
		return DocumentedConfigObservation.CONFIG_OBSERVATION.observation(this.registry, context)
				.keyValuesProvider(this.keyValuesProvider)
				.observe(() -> this.delegate.findOne(application, profile, label));
	}

	@Override
	public Environment findOne(String application, String profile, String label, boolean includeOrigin) {
		ObservationEnvironmentRepositoryContext context = new ObservationEnvironmentRepositoryContext(
				this.delegate.getClass());
		return DocumentedConfigObservation.CONFIG_OBSERVATION.observation(this.registry, context)
				.keyValuesProvider(this.keyValuesProvider)
				.observe(() -> this.delegate.findOne(application, profile, label, includeOrigin));
	}

	/**
	 * Returns the actual delegate.
	 * @return delegate
	 */
	public EnvironmentRepository getDelegate() {
		return delegate;
	}

}
