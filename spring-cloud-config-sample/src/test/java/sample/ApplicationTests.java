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

package sample;

import java.io.IOException;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.SocketUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class,
		// Normally spring.cloud.config.enabled:true is the default but since we have the
		// config
		// server on the classpath we need to set it explicitly
		properties = { "spring.cloud.config.enabled:true",
				"management.security.enabled=false",
				"management.endpoints.web.exposure.include=*" },
		webEnvironment = RANDOM_PORT)
public class ApplicationTests {

	private static final String BASE_PATH = new WebEndpointProperties().getBasePath();

	private static int configPort = SocketUtils.findAvailableTcpPort();

	private static ConfigurableApplicationContext server;

	@LocalServerPort
	private int port;

	@BeforeClass
	public static void startConfigServer() throws IOException {
		String baseDir = ConfigServerTestUtils
				.getBaseDirectory("spring-cloud-config-sample");
		String repo = ConfigServerTestUtils.prepareLocalRepo(baseDir, "target/repos",
				"config-repo", "target/config");
		server = SpringApplication.run(
				org.springframework.cloud.config.server.ConfigServerApplication.class,
				"--server.port=" + configPort, "--spring.config.name=server",
				"--spring.cloud.config.server.git.uri=" + repo);
		/*
		 * FIXME configPort = ((EmbeddedWebApplicationContext) server)
		 * .getEmbeddedServletContainer().getPort();
		 */
		System.setProperty("config.port", "" + configPort);
	}

	@AfterClass
	public static void close() {
		System.clearProperty("config.port");
		if (server != null) {
			server.close();
		}
	}

	public static void main(String[] args) throws IOException {
		configPort = 8888;
		startConfigServer();
		SpringApplication.run(Application.class, args);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void contextLoads() {
		Map res = new TestRestTemplate().getForObject(
				"http://localhost:" + this.port + BASE_PATH + "/env/info.foo", Map.class);
		assertThat(res).containsKey("propertySources");
		Map<String, Object> property = (Map<String, Object>) res.get("property");
		assertThat(property).containsEntry("value", "bar");
	}

}
