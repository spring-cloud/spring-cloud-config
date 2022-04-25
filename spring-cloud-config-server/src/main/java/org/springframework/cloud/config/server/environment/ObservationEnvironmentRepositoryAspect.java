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

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * Aspect wrapping resolution of properties.
 *
 * @author Marcin Grzejszczak
 * @since 4.0.0
 */
@Aspect
public class ObservationEnvironmentRepositoryAspect implements Observation.KeyValuesProviderAware<ObservationEnvironmentRepositoryKeyValuesProvider> {

	private final ObservationRegistry registry;

	private ObservationEnvironmentRepositoryKeyValuesProvider keyValuesProvider = new ObservationEnvironmentRepositoryKeyValuesProvider();

	public ObservationEnvironmentRepositoryAspect(ObservationRegistry tracer) {
		this.registry = tracer;
	}

	@Around("execution (* org.springframework.cloud.config.server.environment.EnvironmentRepository.*(..))")
	public Object observationFindEnvironment(final ProceedingJoinPoint pjp) throws Throwable {
		// @formatter:off
		ObservationEnvironmentRepositoryContext context = new ObservationEnvironmentRepositoryContext(pjp);
		Observation observation = ConfigDocumentedObservation.CONFIG_OBSERVATION.observation(this.registry, context)
			.keyValuesProvider(this.keyValuesProvider).start();
		try (Observation.Scope scope = observation.openScope()) {
			return pjp.proceed();
		}
		catch (Exception exception) {
			observation.error(exception);
			throw exception;
		}
		finally {
			observation.stop();
		}
		// @formatter:on
	}

	@Override
	public void setKeyValuesProvider(ObservationEnvironmentRepositoryKeyValuesProvider keyValuesProvider) {
		this.keyValuesProvider = keyValuesProvider;
	}
}
