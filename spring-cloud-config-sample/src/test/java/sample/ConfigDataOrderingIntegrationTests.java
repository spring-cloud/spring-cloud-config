/*
 * Copyright 2018-2025 the original author or authors.
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

package sample;

import java.io.File;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.test.LocalServerPort;
import org.springframework.boot.web.server.test.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.TestSocketUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(classes = Application.class,
		// Normally spring.cloud.config.enabled:true is the default but since we have the
		// config server on the classpath we need to set it explicitly
		// spring.config.import needs to come from orderingtest.yml to test this issue
		// hence no spring.config.import here and config name change
		properties = { "spring.application.name=profilesample", "spring.cloud.config.enabled=true",
				"spring.config.name=orderingtest", "management.security.enabled=false", "spring.profiles.active=dev",
				"management.endpoints.web.exposure.include=*", "management.endpoint.env.show-values=ALWAYS" },
		webEnvironment = RANDOM_PORT)
public class ConfigDataOrderingIntegrationTests {

	private static final String BASE_PATH = new WebEndpointProperties().getBasePath();

	private static final int configPort = TestSocketUtils.findAvailableTcpPort();

	private static ConfigurableApplicationContext server;

	@LocalServerPort
	private int port;

	@BeforeAll
	public static void startConfigServer() {
		String testResourcesDir = new File("src/test/resources").getAbsolutePath();
		server = SpringApplication.run(org.springframework.cloud.config.server.test.TestConfigServerApplication.class,
				"--spring.profiles.active=native", "--server.port=" + configPort, "--spring.config.name=server",
				"--spring.cloud.config.server.native.search-locations=file://" + testResourcesDir + ",file://"
						+ testResourcesDir + "/config");

		System.setProperty("spring.cloud.config.uri", "http://localhost:" + configPort);
	}

	@AfterAll
	public static void close() {
		System.clearProperty("spring.cloud.config.uri");
		if (server != null) {
			server.close();
		}
	}

	@Test
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void contextLoads() {
		ResponseEntity<Map> response = new TestRestTemplate()
			.getForEntity("http://localhost:" + this.port + BASE_PATH + "/env/my.prop", Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map res = response.getBody();
		assertThat(res).containsKey("propertySources");
		Map<String, Object> property = (Map<String, Object>) res.get("property");
		assertThat(property).containsEntry("value", "my value from config server dev profile");
	}

}
