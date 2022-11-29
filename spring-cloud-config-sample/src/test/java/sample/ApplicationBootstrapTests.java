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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentProperties;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.TestSocketUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Test for gh-975. org.springframework.beans.factory.UnsatisfiedDependencyException:
 * Error creating bean with name 'vaultEnvironmentRepositoryFactory' defined in
 * org.springframework.cloud.config.server.config.CompositeRepositoryConfiguration:
 * Unsatisfied dependency No qualifying bean of type
 * 'javax.servlet.http.HttpServletRequest' available.
 *
 * @author Spencer Gibb
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class,
		// Normally spring.cloud.config.enabled:true is the default but since we have the
		// config
		// server on the classpath we need to set it explicitly
		properties = { "spring.cloud.config.enabled:true", "", "spring.config.use-legacy-processing=true",
				"management.security.enabled=false", "management.endpoints.web.exposure.include=*",
				"management.endpoint.env.show-values=ALWAYS" },
		webEnvironment = RANDOM_PORT)
public class ApplicationBootstrapTests {

	private static final String BASE_PATH = new WebEndpointProperties().getBasePath();

	private static int configPort = TestSocketUtils.findAvailableTcpPort();

	private static ConfigurableApplicationContext server;

	@LocalServerPort
	private int port;

	@BeforeClass
	public static void startConfigServer() throws IOException {
		System.setProperty("spring.cloud.bootstrap.name", "bootstrapservercomposite");
		String baseDir = ConfigServerTestUtils.getBaseDirectory("spring-cloud-config-sample");
		String repo = ConfigServerTestUtils.prepareLocalRepo(baseDir, "target/repos", "config-repo", "target/config");
		System.setProperty("repo1", repo);
		server = SpringApplication.run(org.springframework.cloud.config.server.test.TestConfigServerApplication.class,
				// FIXME: configdata why is use legacy needed here and above?
				"--spring.config.use-legacy-processing=true", "--server.port=" + configPort,
				"--spring.config.name=compositeserver", "--repo1=" + repo);
		System.setProperty("config.port", "" + configPort);
	}

	@AfterClass
	public static void close() {
		System.clearProperty("config.port");
		System.clearProperty("spring.cloud.bootstrap.name");
		System.clearProperty("repo1");
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
		Map res = new TestRestTemplate().getForObject("http://localhost:" + this.port + BASE_PATH + "/env/info.foo",
				Map.class);
		assertThat(res).containsKey("propertySources");
		Map<String, Object> property = (Map<String, Object>) res.get("property");
		assertThat(property).containsEntry("value", "bar");
	}

	@Test
	public void propertiesBeansRegisterByCompositeEnvBeanFactoryPostProcessor() {
		String[] beanNames = server.getBeanNamesForType(MultipleJGitEnvironmentProperties.class);
		assertThat(beanNames).isNotNull().anyMatch(s -> s.matches("git-env-repo-properties\\d"));
	}

}
