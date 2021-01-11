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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.config.server.test.ConfigServerTestUtils.assertOriginTrackedValue;
import static org.springframework.cloud.config.server.test.ConfigServerTestUtils.getV2AcceptEntity;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ConfigServerApplication.class, properties = { "spring.cloud.bootstrap.enabled=true",
		"logging.level.org.springframework.boot.context.config=TRACE", "spring.cloud.bootstrap.name:enable-bootstrap",
		"encrypt.rsa.algorithm=DEFAULT", "encrypt.rsa.strong=false" },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({ "test", "encrypt" })
public class BootstrapConfigServerIntegrationTests {

	@LocalServerPort
	private int port;

	@Autowired
	ConfigurableEnvironment env;

	@BeforeClass
	public static void init() throws IOException {
		// mock Git configuration to make tests independent of local Git configuration
		SystemReader.setInstance(new MockSystemReader());

		ConfigServerTestUtils.prepareLocalRepo("encrypt-repo");
	}

	@Test
	public void contextLoads() {
		ResponseEntity<Environment> response = new TestRestTemplate().exchange(
				"http://localhost:" + this.port + "/foo/development/", HttpMethod.GET, getV2AcceptEntity(),
				Environment.class);
		Environment environment = response.getBody();
		assertThat(environment.getPropertySources()).hasSize(2);
		assertOriginTrackedValue(environment, 0, "bar", "foo");
		assertOriginTrackedValue(environment, 1, "info.foo", "bar");
	}

	@Test
	@Ignore // FIXME: configdata
	public void environmentBootstraps() throws Exception {
		assertThat(this.env.getProperty("info.foo", "")).isEqualTo("bar");
		assertThat(this.env.getProperty("config.foo", "")).isEqualTo("foo");
	}

}
