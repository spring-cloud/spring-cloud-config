/*
 * Copyright 2018-2019 the original author or authors.
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
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ConfigServerApplication.class,
		properties = { "spring.config.name:configserver" }, webEnvironment = RANDOM_PORT)
@ActiveProfiles({ "test", "native" })
public class NativeConfigServerIntegrationTests {

	@LocalServerPort
	private int port;

	@BeforeClass
	public static void init() throws IOException {
		// mock Git configuration to make tests independent of local Git configuration
		SystemReader.setInstance(new MockSystemReader());

		ConfigServerTestUtils.prepareLocalRepo();
	}

	@Test
	public void contextLoads() {
		Environment environment = new TestRestTemplate().getForObject(
				"http://localhost:" + this.port + "/foo/development/", Environment.class);
		assertThat(environment.getPropertySources().isEmpty()).isFalse();
		assertThat(environment.getPropertySources().get(0).getName())
				.isEqualTo("overrides");
		ConfigServerTestUtils.assertConfigEnabled(environment);
	}

	@Test
	public void badYaml() {
		ResponseEntity<String> response = new TestRestTemplate().getForEntity(
				"http://localhost:" + this.port + "/bad/default/", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
	}

}
