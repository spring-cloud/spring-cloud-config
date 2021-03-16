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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
import org.springframework.core.env.MutablePropertySources;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.SocketUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * This test fails if the property spring.config.import is set in application.yml. If
 * spring.config.import is not defined in application.yml and passed it with
 * -Dspring.config.import=value, then the test passes as order of config files is
 * maintained.
 *
 * NOTE: to fail the test, we just need to uncomment line 11 to 16 of application.yml
 * under src/main/resources or we need to set "spring.profiles.active=myprofile,baz
 * in @SpringBootTest's properties
 *
 * @author Suchandra Nag
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class,
		properties = { "spring.cloud.config.enabled=true", "spring.config.import=configserver:",
				"spring.application.name=payroll", "spring.profiles.active=myprofile, baz",
				"logging.level.org.application-myprofile.ymlspringframework.boot.context.config=INFO" },
		webEnvironment = RANDOM_PORT)
public class ServerMyTestApplicationTests {

	private Log logger = LogFactory.getLog(getClass());

	private static int configPort = SocketUtils.findAvailableTcpPort();

	private static ConfigurableApplicationContext server;

	@Autowired
	private ConfigurableEnvironment environment;

	@LocalServerPort
	private int port;

	@BeforeClass
	public static void startConfigServer() throws IOException {
		String baseDir = ConfigServerTestUtils.getBaseDirectory("spring-cloud-config-sample");
		String repo = ConfigServerTestUtils.prepareLocalRepo(baseDir, "target/repos", "configmytest-repo",
				"target/config");

		server = SpringApplication.run(org.springframework.cloud.config.server.ConfigServerApplication.class,
				"--server.port=" + configPort, "--spring.config.name=server", "--spring.application.name=payroll",
				"--spring.cloud.config.server.git.uri=" + repo);

		System.setProperty("spring.cloud.config.uri", "http://localhost:" + configPort);
		System.setProperty("config.port", "" + configPort);
	}

	@AfterClass
	public static void close() {
		if (server != null) {
			server.close();
		}
	}

	@Test
	public void contextLoads() {

		MutablePropertySources propertySources = this.environment.getPropertySources();

		propertySources.stream().filter(propertySource -> (propertySource.getName().startsWith("configserver"))
				|| (propertySource.getName().startsWith("Config resource"))).forEach(propertySource -> {
					logger.info(propertySource.getName() + ":" + propertySource.getSource());
				});

		assertThat(environment.getProperty("key")).isEqualTo("remote-payroll-baz-yml");

	}

}
