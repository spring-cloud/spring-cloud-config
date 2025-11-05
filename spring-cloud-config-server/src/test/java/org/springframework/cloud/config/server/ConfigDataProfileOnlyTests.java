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

package org.springframework.cloud.config.server;

import java.io.IOException;

import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.util.SystemReader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.cloud.config.server.test.TestConfigServerApplication;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.config.server.test.ConfigServerTestUtils.getV2AcceptEntity;

/**
 * @author Ryan Baxter
 */
@SpringBootTest(classes = TestConfigServerApplication.class,
		properties = { "spring.config.name:configserver",
				"spring.cloud.config.server.git.uri:file:./target/repos/spring-profiles-active" },
		webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
public class ConfigDataProfileOnlyTests {

	@LocalServerPort
	private int port;

	@BeforeAll
	public static void init() throws IOException {
		// mock Git configuration to make tests independent of local Git configuration
		SystemReader.setInstance(new MockSystemReader());

		ConfigServerTestUtils.prepareLocalRepo("spring-profiles-active");
	}

	@Test
	public void noProfileSpecificPropertySources() {
		ResponseEntity<Environment> response = new TestRestTemplate().exchange(
				"http://localhost:" + this.port + "/foo/default", HttpMethod.GET, getV2AcceptEntity(),
				Environment.class);
		Environment environment = response.getBody();
		assertThat(environment.getPropertySources()).isNotEmpty();
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		environment.getPropertySources().forEach(p -> assertThat(p.getName().contains("application-dev")).isFalse());
	}

	@Test
	public void includeProfileSpecificPropertySources() {
		ResponseEntity<Environment> response = new TestRestTemplate().exchange(
				"http://localhost:" + this.port + "/foo/dev,test", HttpMethod.GET, getV2AcceptEntity(),
				Environment.class);
		Environment environment = response.getBody();
		assertThat(environment.getPropertySources()).isNotEmpty();
		assertThat(environment.getPropertySources().size()).isEqualTo(3);
		assertThat(environment.getPropertySources().stream().anyMatch(p -> p.getName().contains("application-dev")))
			.isTrue();
	}

}
