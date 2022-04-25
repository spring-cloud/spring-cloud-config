/*
 * Copyright 2013-2020 the original author or authors.
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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.tck.MeterRegistryAssert;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.test.SampleTestRunner;
import io.micrometer.tracing.test.simple.SpansAssert;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.config.server.ConfigServerApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

// TODO: We need to improve how this is tested
@SpringBootTest(classes = ObservationEnvironmentRepositoryTests.TestConfiguration.class, properties = {"spring.profiles.active=test,native",
	"spring.main.web-application-type=none",
	"management.tracing.sampling.probability=1.0",
	"spring.autoconfigure.exclude=org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryAutoConfiguration,org.springframework.boot.actuate.autoconfigure.wavefront.WavefrontAutoConfiguration"})
public class ObservationEnvironmentRepositoryTests extends SampleTestRunner {

	@Autowired
	MeterRegistry meterRegistry;

	@Autowired
	ObservationRegistry observationRegistry;

	@Autowired
	EnvironmentRepository environmentRepository;

	@Override
	public SampleTestRunnerConsumer yourCode() throws Exception {
		return (buildingBlocks, mr) -> {
			this.environmentRepository.findOne("foo", "bar", "baz");

			SpansAssert.assertThat(buildingBlocks.getFinishedSpans())
				.haveSameTraceId()
				.hasNumberOfSpansWithNameEqualTo("find", 2)
				.hasSize(2);
			MeterRegistryAssert.assertThat(mr)
				.hasTimerWithNameAndTags("find", Tags.of(
					Tag.of("spring.config.environment.class", "org.springframework.cloud.config.server.environment.NativeEnvironmentRepository"),
					Tag.of("spring.config.environment.method", "findOne")))
				.hasTimerWithNameAndTags("find", Tags.of(
					Tag.of("spring.config.environment.class", "org.springframework.cloud.config.server.environment.SearchPathCompositeEnvironmentRepository"),
					Tag.of("spring.config.environment.method", "findOne")));
		};
	}

	@Override
	public TracingSetup[] getTracingSetup() {
		return new TracingSetup[] {TracingSetup.IN_MEMORY_BRAVE};
	}

	@Override
	protected MeterRegistry getMeterRegistry() {
		return this.meterRegistry;
	}

	@Override
	protected ObservationRegistry getObservationRegistry() {
		return this.observationRegistry;
	}

	@Configuration(proxyBeanMethods = false)
	@Import(ConfigServerApplication.class)
	protected static class TestConfiguration {

	}
}
