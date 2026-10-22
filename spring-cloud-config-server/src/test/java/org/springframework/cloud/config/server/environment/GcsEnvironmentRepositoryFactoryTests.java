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
import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.server.config.ConfigServerProperties;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GcsEnvironmentRepositoryFactoryTests {

	@Test
	void doesNotCloseExternallySuppliedStorage() throws Exception {
		Storage storage = mock(Storage.class);
		GcsEnvironmentRepositoryFactory factory = new GcsEnvironmentRepositoryFactory(new ConfigServerProperties(),
				storage);

		factory.destroy();

		verify(storage, never()).close();
	}

	@Test
	void closesFactoryCreatedStorageWhenDestroyed() throws Exception {
		Storage storage = mock(Storage.class);
		GcsEnvironmentRepositoryFactory factory = new GcsEnvironmentRepositoryFactory(new ConfigServerProperties()) {
			@Override
			Storage createStorage(GcsEnvironmentProperties properties) {
				return storage;
			}
		};

		factory.build(new GcsEnvironmentProperties() {
			{
				setBucket("test-bucket");
			}
		});

		factory.destroy();

		verify(storage).close();
	}

	@Test
	void reusesFactoryCreatedStorageAcrossBuildCalls() throws Exception {
		Storage storage = mock(Storage.class);
		GcsEnvironmentRepositoryFactory factory = new GcsEnvironmentRepositoryFactory(new ConfigServerProperties()) {
			@Override
			Storage createStorage(GcsEnvironmentProperties properties) {
				return storage;
			}
		};

		GcsEnvironmentProperties properties = new GcsEnvironmentProperties();
		properties.setBucket("test-bucket");

		factory.build(properties);
		factory.build(properties);

		factory.destroy();

		verify(storage).close();
	}

}
