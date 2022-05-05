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

import java.util.List;
import java.util.stream.Collectors;

import brave.handler.SpanHandler;
import brave.test.TestSpanHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.tck.MeterRegistryAssert;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.brave.bridge.BraveFinishedSpan;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.otel.bridge.ArrayListSpanProcessor;
import io.micrometer.tracing.otel.bridge.OtelFinishedSpan;
import io.micrometer.tracing.test.simple.SpansAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.config.server.ConfigServerApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@DisplayName("Observation with Metrics and ")
public class ObservationEnvironmentRepositoryTests {

	@Nested
	@SpringBootTest(classes = BraveTests.BraveConfiguration.class,
			properties = "spring.autoconfigure.exclude=org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryAutoConfiguration")
	@DisplayName("Brave Tracer")
	class BraveTests extends AbstractObservationEnvironmentRepositoryTests {

		@Autowired
		TestSpanHandler testSpanHandler;

		@Override
		List<FinishedSpan> finishedSpans() {
			return this.testSpanHandler.spans().stream().map(BraveFinishedSpan::fromBrave).collect(Collectors.toList());
		}

		@Configuration(proxyBeanMethods = false)
		protected static class BraveConfiguration {

			@Bean
			SpanHandler braveTestSpanHandler() {
				return new TestSpanHandler();
			}

		}

	}

	@Nested
	@SpringBootTest(classes = OtelTests.OtelConfiguration.class,
			properties = "spring.autoconfigure.exclude=org.springframework.boot.actuate.autoconfigure.tracing.BraveAutoConfiguration")
	@DisplayName("OTel Tracer")
	class OtelTests extends AbstractObservationEnvironmentRepositoryTests {

		@Autowired
		ArrayListSpanProcessor arrayListSpanProcessor;

		@Override
		List<FinishedSpan> finishedSpans() {
			return this.arrayListSpanProcessor.spans().stream().map(OtelFinishedSpan::fromOtel)
					.collect(Collectors.toList());
		}

		@Configuration(proxyBeanMethods = false)
		protected static class OtelConfiguration {

			@Bean
			ArrayListSpanProcessor otelArrayListSpanProcessor() {
				return new ArrayListSpanProcessor();
			}

		}

	}

	@ContextConfiguration(classes = AbstractObservationEnvironmentRepositoryTests.TestConfiguration.class)
	@TestPropertySource(properties = { "spring.profiles.active=test,native", "spring.main.web-application-type=none",
			"management.tracing.sampling.probability=1.0" })
	static abstract class AbstractObservationEnvironmentRepositoryTests {

		@Autowired
		MeterRegistry meterRegistry;

		@Autowired
		ObservationRegistry observationRegistry;

		@Autowired
		EnvironmentRepository environmentRepository;

		@Test
		void should_collect_measurements() {
			this.environmentRepository.findOne("foo", "bar", "baz");

			SpansAssert.assertThat(finishedSpans()).haveSameTraceId().hasNumberOfSpansWithNameEqualTo("find", 2)
					.hasSize(2);
			MeterRegistryAssert.assertThat(meterRegistry)
					.hasTimerWithNameAndTags("find",
							Tags.of(Tag.of("spring.config.environment.class",
									"org.springframework.cloud.config.server.environment.NativeEnvironmentRepository"),
									Tag.of("spring.config.environment.method", "findOne")))
					.hasTimerWithNameAndTags("find", Tags.of(Tag.of("spring.config.environment.class",
							"org.springframework.cloud.config.server.environment.SearchPathCompositeEnvironmentRepository"),
							Tag.of("spring.config.environment.method", "findOne")));
		}

		abstract List<FinishedSpan> finishedSpans();

		@Configuration(proxyBeanMethods = false)
		@Import(ConfigServerApplication.class)
		protected static class TestConfiguration {

		}

	}

}
