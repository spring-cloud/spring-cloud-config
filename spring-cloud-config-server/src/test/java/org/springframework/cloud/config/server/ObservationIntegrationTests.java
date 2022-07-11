/*
 * Copyright 2017-2022 the original author or authors.
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

package org.springframework.cloud.config.server;

import java.util.List;
import java.util.stream.Collectors;

import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import io.micrometer.common.KeyValues;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.tck.MeterRegistryAssert;
import io.micrometer.tracing.brave.bridge.BraveFinishedSpan;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.test.simple.SpansAssert;
import org.assertj.core.api.Assertions;
import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.util.SystemReader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.cloud.config.server.test.TestConfigServerApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.config.server.test.ConfigServerTestUtils.getV2AcceptEntity;

@AutoConfigureObservability
@SpringBootTest(classes = { TestConfigServerApplication.class, ObservationIntegrationTests.Config.class },
		properties = { "spring.application.name:config-server", "spring.config.name:compositeconfigserver",
				"spring.cloud.config.server.svn.uri:file:///./target/repos/svn-config-repo",
				"spring.cloud.config.server.svn.order:2",
				"spring.cloud.config.server.git.uri:file:./target/repos/config-repo",
				"spring.cloud.config.server.git.order:1" },
		webEnvironment = RANDOM_PORT)
@ActiveProfiles({ "test", "git", "subversion" })
class ObservationIntegrationTests {

	@LocalServerPort
	private int port;

	@BeforeAll
	public static void init() throws Exception {
		// mock Git configuration to make tests independent of local Git configuration
		SystemReader.setInstance(new MockSystemReader());

		ConfigServerTestUtils.prepareLocalRepo();
		ConfigServerTestUtils.prepareLocalSvnRepo("src/test/resources/svn-config-repo", "target/repos/svn-config-repo");
	}

	@Autowired
	TestSpanHandler testSpanHandler;

	@Autowired
	MeterRegistry meterRegistry;

	@Test
	void testSuccessfulObservation() {
		ResponseEntity<Environment> response = new TestRestTemplate().exchange(
				"http://localhost:" + this.port + "/foo/development", HttpMethod.GET, getV2AcceptEntity(),
				Environment.class);

		Environment environment = response.getBody();
		Assertions.assertThat(environment).isNotNull();
		ConfigServerTestUtils.assertConfigEnabled(environment);

		List<FinishedSpan> finishedSpans = finishedSpans();

		SpansAssert.then(finishedSpans).hasASpanWithNameIgnoreCase("env find", spanAssert -> spanAssert
				.hasTag("spring.cloud.config.environment.application", "foo")
				.hasTag("spring.cloud.config.environment.profile", "development")
				.hasTag("spring.cloud.config.environment.class",
						"org.springframework.cloud.config.server.environment.SearchPathCompositeEnvironmentRepository"))
				.hasASpanWithNameIgnoreCase("env find", spanAssert -> spanAssert
						.hasTag("spring.cloud.config.environment.application", "foo")
						.hasTag("spring.cloud.config.environment.profile", "development")
						.hasTag("spring.cloud.config.environment.class",
								"org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentRepository"))
				.hasASpanWithNameIgnoreCase("env find", spanAssert -> spanAssert
						.hasTag("spring.cloud.config.environment.application", "foo")
						.hasTag("spring.cloud.config.environment.profile", "development")
						.hasTag("spring.cloud.config.environment.class",
								"org.springframework.cloud.config.server.environment.SvnKitEnvironmentRepository"))
				.hasASpanWithNameIgnoreCase("env find", spanAssert -> spanAssert
						.hasTag("spring.cloud.config.environment.application", "foo")
						.hasTag("spring.cloud.config.environment.profile", "development")
						.hasTag("spring.cloud.config.environment.class",
								"org.springframework.cloud.config.server.environment.PassthruEnvironmentRepository"));

		MeterRegistryAssert.then(this.meterRegistry)
				.hasTimerWithNameAndTags("spring.cloud.config.environment.find", KeyValues.of(
						"spring.cloud.config.environment.application", "foo", "spring.cloud.config.environment.profile",
						"development", "spring.cloud.config.environment.class",
						"org.springframework.cloud.config.server.environment.SearchPathCompositeEnvironmentRepository"))
				.hasTimerWithNameAndTags("spring.cloud.config.environment.find", KeyValues.of(
						"spring.cloud.config.environment.application", "foo", "spring.cloud.config.environment.profile",
						"development", "spring.cloud.config.environment.class",
						"org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentRepository"))
				.hasTimerWithNameAndTags("spring.cloud.config.environment.find",
						KeyValues.of("spring.cloud.config.environment.application", "foo",
								"spring.cloud.config.environment.profile", "development",
								"spring.cloud.config.environment.class",
								"org.springframework.cloud.config.server.environment.SvnKitEnvironmentRepository"))
				.hasTimerWithNameAndTags("spring.cloud.config.environment.find",
						KeyValues.of("spring.cloud.config.environment.application", "foo",
								"spring.cloud.config.environment.profile", "development",
								"spring.cloud.config.environment.class",
								"org.springframework.cloud.config.server.environment.PassthruEnvironmentRepository"));
	}

	private List<FinishedSpan> finishedSpans() {
		return this.testSpanHandler.spans().stream().map(BraveFinishedSpan::fromBrave).collect(Collectors.toList());
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		TestSpanHandler testSpanHandler() {
			return new TestSpanHandler();
		}

		@Bean
		Sampler sampler() {
			return Sampler.ALWAYS_SAMPLE;
		}

	}

}
