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

import org.springframework.beans.factory.DisposableBean;
import org.springframework.cloud.config.server.config.ConfigServerProperties;

/**
 * Factory for creating Google Cloud Storage environment repositories.
 */
public class GcsEnvironmentRepositoryFactory
		implements EnvironmentRepositoryFactory<GcsEnvironmentRepository, GcsEnvironmentProperties>, DisposableBean {

	private final ConfigServerProperties server;

	private final Storage storage;

	private Storage createdStorage;

	public GcsEnvironmentRepositoryFactory(ConfigServerProperties server) {
		this(server, null);
	}

	public GcsEnvironmentRepositoryFactory(ConfigServerProperties server, Storage storage) {
		this.server = server;
		this.storage = storage;
	}

	@Override
	public GcsEnvironmentRepository build(GcsEnvironmentProperties properties) {
		Storage resolvedStorage = this.storage;

		if (resolvedStorage == null) {
			if (this.createdStorage == null) {
				this.createdStorage = createStorage(properties);
			}
			resolvedStorage = this.createdStorage;
		}

		return new GcsEnvironmentRepository(resolvedStorage, properties, this.server);
	}

	Storage createStorage(GcsEnvironmentProperties properties) {
		StorageOptions.Builder builder = StorageOptions.newBuilder();

		if (properties.getProjectId() != null && !properties.getProjectId().isBlank()) {
			builder.setProjectId(properties.getProjectId());
		}

		return builder.build().getService();
	}

	@Override
	public void destroy() throws Exception {
		if (this.createdStorage != null) {
			this.createdStorage.close();
		}
	}

}
