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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class, properties = "spring.application.name:bad",
		webEnvironment = RANDOM_PORT)
public class ServerNativeApplicationTests {

	private static int configPort = 0;

	private static ConfigurableApplicationContext server;

	@Autowired
	private ConfigurableEnvironment environment;

	@LocalServerPort
	private int port;

	@BeforeClass
	public static void startConfigServer() throws IOException {
		String repo = ConfigServerTestUtils.prepareLocalRepo();
		server = SpringApplication.run(
				org.springframework.cloud.config.server.ConfigServerApplication.class,
				"--server.port=" + configPort, "--spring.config.name=server",
				"--spring.cloud.config.server.git.uri=" + repo,
				"--spring.profiles.active=native");
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
	public void contextLoads() {
		// The remote config was bad so there is no bootstrap
		assertThat(this.environment.getPropertySources().contains("bootstrap")).isFalse();
	}

}
