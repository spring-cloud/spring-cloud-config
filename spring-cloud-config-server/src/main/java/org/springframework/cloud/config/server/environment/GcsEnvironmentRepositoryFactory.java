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

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import org.springframework.cloud.config.server.config.ConfigServerProperties;

/**
 * Factory for creating Google Cloud Storage environment repositories.
 */
public class GcsEnvironmentRepositoryFactory
		implements EnvironmentRepositoryFactory<GcsEnvironmentRepository, GcsEnvironmentProperties> {

	private final ConfigServerProperties server;

	public GcsEnvironmentRepositoryFactory(ConfigServerProperties server) {
		this.server = server;
	}

	@Override
	public GcsEnvironmentRepository build(GcsEnvironmentProperties properties) {
		StorageOptions.Builder builder = StorageOptions.newBuilder();

		if (properties.getProjectId() != null && !properties.getProjectId().isBlank()) {
			builder.setProjectId(properties.getProjectId());
		}

		Storage storage = builder.build().getService();

		return new GcsEnvironmentRepository(storage, properties, server);
	}

}
