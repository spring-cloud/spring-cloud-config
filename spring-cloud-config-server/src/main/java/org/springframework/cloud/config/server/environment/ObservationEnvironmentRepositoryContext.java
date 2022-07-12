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

/**
 * {@link Observation.Context} for Spring Cloud Config.
 *
 * @author Marcin Grzejszczak
 * @since 4.0.0
 */
public class ObservationEnvironmentRepositoryContext extends Observation.Context {

	private final Class<? extends EnvironmentRepository> clazz;

	private final String application;

	private final String profile;

	private final String label;

	public ObservationEnvironmentRepositoryContext(Class<? extends EnvironmentRepository> clazz, String application,
			String profile, String label) {
		this.clazz = clazz;
		this.application = application;
		this.profile = profile;
		this.label = label;
	}

	public Class<? extends EnvironmentRepository> getEnvironmentRepositoryClass() {
		return this.clazz;
	}

	public String getApplication() {
		return this.application;
	}

	public String getProfile() {
		return this.profile;
	}

	public String getLabel() {
		return this.label;
	}

}
