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

import java.util.Set;

import org.springframework.cloud.config.server.environment.EnvironmentRepository;

/**
 * Plugs an {@link EnvironmentRepository} implementation into
 * {@link EnvironmentRepositoryComplianceTests}. A fixture knows how to write
 * {@link ConfigFile}s into the backend in the backend's native shape (files, rows, keys,
 * secrets...) and how to create a repository that reads them.
 *
 * @author Ryan Baxter
 */
public interface EnvironmentRepositoryFixture {

	/**
	 * Remove all configuration from the backend. Called before every scenario.
	 * @throws Exception if the backend cannot be cleared
	 */
	void reset() throws Exception;

	/**
	 * Store a configuration file in the backend.
	 * @param file the file to store
	 * @throws Exception if the file cannot be stored
	 */
	void store(ConfigFile file) throws Exception;

	/**
	 * Called once all files for a scenario have been stored and before the repository is
	 * queried, for example to commit to a Git repository.
	 * @throws Exception if the backend cannot be prepared
	 */
	default void prepare() throws Exception {
	}

	/**
	 * The repository under test.
	 * @return the repository
	 * @throws Exception if the repository cannot be created
	 */
	EnvironmentRepository repository() throws Exception;

	/**
	 * The optional features the backend supports.
	 * @return the supported capabilities
	 */
	Set<Capability> capabilities();

}
