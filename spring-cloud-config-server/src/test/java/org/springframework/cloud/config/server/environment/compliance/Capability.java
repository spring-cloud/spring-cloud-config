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

package org.springframework.cloud.config.server.environment.compliance;

/**
 * Optional features of an
 * {@link org.springframework.cloud.config.server.environment.EnvironmentRepository}.
 * Scenarios that need a capability a fixture does not declare are skipped rather than
 * failed. Use capabilities for behavior a backend does not support by design; use
 * {@code @Disabled} overrides for behavior a backend supports but gets wrong.
 *
 * @author Ryan Baxter
 */
public enum Capability {

	/**
	 * A label selects a distinct set of configuration.
	 */
	LABELS,

	/**
	 * A comma-separated label resolves every label, with the last label taking
	 * precedence.
	 */
	MULTIPLE_LABELS,

	/**
	 * A comma-separated application name resolves every application, with the last
	 * application taking precedence.
	 */
	MULTIPLE_APPLICATIONS,

	/**
	 * A YAML file can contain several documents, activated with
	 * {@code spring.config.activate.on-profile}.
	 */
	MULTI_DOCUMENT_YAML,

	/**
	 * {@code spring.config.activate.on-profile} supports profile expressions such as
	 * {@code !dev}.
	 */
	PROFILE_EXPRESSIONS,

	/**
	 * Configuration for the {@code default} profile (for example
	 * {@code orders-default.yml}) is stored separately from configuration without a
	 * profile (for example {@code orders.yml}).
	 */
	DISTINCT_DEFAULT_PROFILE,

	/**
	 * Configuration for the {@code default} profile is applied even when another profile
	 * is active, as a layer shared by all profiles. Spring Boot only applies it when no
	 * other profile is active, so this is a deliberate, documented difference. Only
	 * meaningful together with {@link #DISTINCT_DEFAULT_PROFILE}.
	 */
	DEFAULT_PROFILE_ALWAYS_ACTIVE,

	/**
	 * A {@code .properties} and a {@code .yml} file with the same name can exist at the
	 * same location.
	 */
	MIXED_FORMATS

}
