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

package org.springframework.cloud.config.server;

import java.io.IOException;

import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.util.SystemReader;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.RefreshableConfigServerIntegrationTests.TestConfiguration;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.resource.ResourceRepository;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.springframework.cloud.config.server.test.ConfigServerTestUtils.assertOriginTrackedValue;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestConfiguration.class,
		properties = { "spring.cloud.config.enabled=true",
				"management.endpoints.web.exposure.include=env, refresh" },
		webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext
public class RefreshableConfigServerIntegrationTests {

	private static String localRepo = null;

	@LocalServerPort
	private int port;

	@BeforeClass
	public static void init() throws IOException {
		// mock Git configuration to make tests independent of local Git configuration
		SystemReader.setInstance(new MockSystemReader());

		localRepo = ConfigServerTestUtils.prepareLocalRepo();
	}

	@AfterClass
	public static void after() throws IOException {
		ConfigServerTestUtils.deleteLocalRepo(localRepo);
	}

	/*
	 * We're emulating an application "foo" which is running with the "development"
	 * profile and is asking for its properties using the REST endpoint. We're also
	 * calling the /env & /refresh actuator endpoints to change the
	 * `spring.cloud.config.server.overrides.foo` property. Since we see that we only get
	 * the overridden "foo" property after the context refresh we are sure that the
	 * properties have been set and the EnvironmentController bean has successfully been
	 * recreated with the new overrides.
	 */
	@Test
	public void refreshOverrides() {
		Environment environment = new TestRestTemplate().getForObject(
				"http://localhost:" + this.port + "/foo/development/", Environment.class);
		assertThat(environment.getPropertySources()).isEmpty();

		String actuatorEndpoint = "http://localhost:" + this.port + "/actuator";
		HttpHeaders headers = new HttpHeaders();
		headers.set("Content-Type", "application/json");
		HttpEntity<String> request = new HttpEntity<>(
				"{\"name\": \"spring.cloud.config.server.overrides.foo\", \"value\": \"bar\"}",
				headers);
		ResponseEntity<Void> response = new TestRestTemplate()
				.postForEntity(actuatorEndpoint + "/env", request, Void.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

		response = new TestRestTemplate().postForEntity(actuatorEndpoint + "/refresh",
				null, Void.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

		environment = new TestRestTemplate().getForObject(
				"http://localhost:" + this.port + "/foo/development/", Environment.class);
		assertThat(environment.getPropertySources()).isNotEmpty();
		assertOriginTrackedValue(environment, 0, "foo", "bar");
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	@EnableConfigServer
	protected static class TestConfiguration {

		@Bean
		public EnvironmentRepository environmentRepository() {
			EnvironmentRepository repository = Mockito.mock(EnvironmentRepository.class);
			Environment environment = new Environment("", "");
			given(repository.findOne(isA(String.class), isA(String.class),
					nullable(String.class), isA(Boolean.class))).willReturn(environment);
			return repository;
		}

		@Bean
		public ResourceRepository resourceRepository() {
			ResourceRepository repository = Mockito.mock(ResourceRepository.class);
			given(repository.findOne(isA(String.class), isA(String.class),
					nullable(String.class), isA(String.class)))
							.willReturn(new ByteArrayResource("".getBytes()));
			return repository;
		}

	}

}
