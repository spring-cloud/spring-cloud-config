/*
 * Copyright 2013-2019 the original author or authors.
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

import io.micrometer.core.tck.TestObservationRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.environment.Environment;

import static io.micrometer.core.tck.TestObservationRegistryAssert.assertThat;

class ObservationEnvironmentRepositoryWrapperTests {

	@Test
	void should_collect_metrics() {
		TestObservationRegistry registry = TestObservationRegistry.create();
		EnvironmentRepository delegate = new MyEnvRepo();
		EnvironmentRepository wrapper = ObservationEnvironmentRepositoryWrapper.wrap(registry, delegate);

		wrapper.findOne("foo", "bar", "baz");

		assertThat(registry).hasSingleObservationThat().hasNameEqualTo("find").hasBeenStarted().hasBeenStopped()
				.hasLowCardinalityKeyValue("spring.cloud.config.environment.class",
						"org.springframework.cloud.config.server.environment.ObservationEnvironmentRepositoryWrapperTests$MyEnvRepo");
	}

	static class MyEnvRepo implements EnvironmentRepository {

		@Override
		public Environment findOne(String application, String profile, String label) {
			return null;
		}

	}

}
