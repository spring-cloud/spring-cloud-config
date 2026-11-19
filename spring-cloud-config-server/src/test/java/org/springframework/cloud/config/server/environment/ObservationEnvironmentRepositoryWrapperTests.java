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

import java.util.Arrays;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.environment.Environment;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;

class ObservationEnvironmentRepositoryWrapperTests {

	@Test
	void shouldCollectMetrics() {
		TestObservationRegistry registry = TestObservationRegistry.create();
		EnvironmentRepository delegate = new MyEnvRepo();
		EnvironmentRepository wrapper = ObservationEnvironmentRepositoryWrapper.wrap(registry, delegate);

		wrapper.findOne("foo", "bar", "baz");

		assertThat(registry).hasSingleObservationThat()
			.hasNameEqualTo("spring.cloud.config.environment.find")
			.hasBeenStarted()
			.hasBeenStopped()
			.hasLowCardinalityKeyValue("spring.cloud.config.environment.class",
					"org.springframework.cloud.config.server.environment.ObservationEnvironmentRepositoryWrapperTests$MyEnvRepo");
	}

	@Test
	void shouldUseCustomConvention() {
		TestObservationRegistry registry = TestObservationRegistry.create();
		EnvironmentRepository delegate = new MyEnvRepo();

		EnvironmentRepository wrapper = ObservationEnvironmentRepositoryWrapper.wrap(registry, delegate,
				customConvention());

		wrapper.findOne("foo", "bar", "baz");

		assertThat(registry).hasSingleObservationThat()
			.hasNameEqualTo("spring.cloud.config.environment.find")
			.hasBeenStarted()
			.hasBeenStopped()
			.hasLowCardinalityKeyValue("custom.tag", "custom-value");
	}

	@Test
	void shouldUseCustomConventionWhenIncludingOrigin() {
		TestObservationRegistry registry = TestObservationRegistry.create();
		EnvironmentRepository delegate = new MyEnvRepo();

		EnvironmentRepository wrapper = ObservationEnvironmentRepositoryWrapper.wrap(registry, delegate,
				customConvention());

		wrapper.findOne("foo", "bar", "baz", true);

		assertThat(registry).hasSingleObservationThat()
			.hasNameEqualTo("spring.cloud.config.environment.find")
			.hasBeenStarted()
			.hasBeenStopped()
			.hasLowCardinalityKeyValue("custom.tag", "custom-value");
	}

	@Test
	void shouldCreateRootObservationForCompositeAndChildObservationsForDelegates() {
		TestObservationRegistry registry = TestObservationRegistry.create();
		EnvironmentRepository delegate = new MyEnvRepo();
		EnvironmentRepository composite = new CompositeEnvironmentRepository(Arrays.asList(delegate), registry, true);
		EnvironmentRepository wrapper = ObservationEnvironmentRepositoryWrapper.wrap(registry, composite);

		wrapper.findOne("foo", "bar", "baz");

		assertThat(registry).hasHandledContextsThatSatisfy(contexts -> {
			contexts.stream()
				.filter(context -> context.getName().equals("spring.cloud.config.environment.find")
						&& contextExists(context, "spring.cloud.config.environment.class",
								"org.springframework.cloud.config.server.environment.CompositeEnvironmentRepository"))
				.findFirst()
				.orElseThrow(
						() -> new AssertionError("There's no observation for the Composite EnvironmentRepository"));
			contexts.stream()
				.filter(context -> context.getName().equals("spring.cloud.config.environment.find") && contextExists(
						context, "spring.cloud.config.environment.class",
						"org.springframework.cloud.config.server.environment.ObservationEnvironmentRepositoryWrapperTests$MyEnvRepo"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("There's no observation for the wrapped EnvironmentRepository"));
		});
	}

	@Test
	void shouldCreateRootObservationForCompositeWithDefaultValues() {
		TestObservationRegistry registry = TestObservationRegistry.create();
		EnvironmentRepository delegate = new MyEnvRepo();
		EnvironmentRepository composite = new CompositeEnvironmentRepository(Arrays.asList(delegate), registry, true);
		EnvironmentRepository wrapper = ObservationEnvironmentRepositoryWrapper.wrap(registry, composite);

		wrapper.findOne("foo", "bar", null);

		assertThat(registry).hasHandledContextsThatSatisfy(contexts -> {
			contexts.stream()
				.filter(context -> context.getName().equals("spring.cloud.config.environment.find")
						&& contextExists(context, "spring.cloud.config.environment.class",
								"org.springframework.cloud.config.server.environment.CompositeEnvironmentRepository")
						&& context.getLowCardinalityKeyValue("spring.cloud.config.environment.label")
							.getValue()
							.equals(KeyValue.NONE_VALUE)
						&& context.getLowCardinalityKeyValue("spring.cloud.config.environment.application")
							.getValue()
							.equals("foo")
						&& context.getLowCardinalityKeyValue("spring.cloud.config.environment.profile")
							.getValue()
							.equals("bar"))
				.findFirst()
				.orElseThrow(
						() -> new AssertionError("There's no observation for the Composite EnvironmentRepository"));
			contexts.stream()
				.filter(context -> context.getName().equals("spring.cloud.config.environment.find") && contextExists(
						context, "spring.cloud.config.environment.class",
						"org.springframework.cloud.config.server.environment.ObservationEnvironmentRepositoryWrapperTests$MyEnvRepo")
						&& context.getLowCardinalityKeyValue("spring.cloud.config.environment.label")
							.getValue()
							.equals(KeyValue.NONE_VALUE)
						&& context.getLowCardinalityKeyValue("spring.cloud.config.environment.application")
							.getValue()
							.equals("foo")
						&& context.getLowCardinalityKeyValue("spring.cloud.config.environment.profile")
							.getValue()
							.equals("bar"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("There's no observation for the wrapped EnvironmentRepository"));
		});
	}

	private ObservationConvention<ObservationEnvironmentRepositoryContext> customConvention() {
		return new ObservationConvention<>() {

			@Override
			public KeyValues getLowCardinalityKeyValues(ObservationEnvironmentRepositoryContext context) {
				return KeyValues.of(KeyValue.of("custom.tag", "custom-value"));
			}

			@Override
			public boolean supportsContext(Observation.Context context) {
				return context instanceof ObservationEnvironmentRepositoryContext;
			}

			@Override
			public String getName() {
				return "spring.cloud.config.environment.find";
			}

			@Override
			public String getContextualName(ObservationEnvironmentRepositoryContext context) {
				return "custom environment repository";
			}

		};
	}

	private boolean contextExists(Observation.Context context, String tagName, String tagValue) {
		return context.getLowCardinalityKeyValues()
			.stream()
			.anyMatch(keyValue -> keyValue.getKey().equals(tagName) && keyValue.getValue().equals(tagValue));
	}

	static class MyEnvRepo implements EnvironmentRepository {

		@Override
		public Environment findOne(String application, String profile, String label) {
			return new Environment("foo", "bar");
		}

	}

}
