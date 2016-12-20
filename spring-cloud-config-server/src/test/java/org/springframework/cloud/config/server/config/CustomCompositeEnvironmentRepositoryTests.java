/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.config.server.config;

import java.util.HashMap;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Ryan Baxter
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = CustomCompositeEnvironmentRepositoryTests.TestApplication.class, properties = {
		"spring.config.name:compositeconfigserver", "spring.cloud.config.server.git.uri:file:./target/repos/config-repo",
		"spring.cloud.config.server.git.order:1" }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "git"})
@DirtiesContext
public class CustomCompositeEnvironmentRepositoryTests {

	@LocalServerPort
	private int port;

	@BeforeClass
	public static void init() throws Exception {
		ConfigServerTestUtils.prepareLocalRepo();
	}

	@Test
	public void contextLoads() {
		Environment environment = new TestRestTemplate().getForObject(
				"http://localhost:" + port + "/foo/development/", Environment.class);
		List<PropertySource> propertySources = environment.getPropertySources();
		assertEquals(3, propertySources.size());
		assertEquals("overrides", propertySources.get(0).getName());
		assertTrue(propertySources.get(1).getName().contains("config-repo"));
		assertEquals("p", propertySources.get(2).getName());
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableConfigServer
	protected static class TestApplication {

		@Bean
		public EnvironmentRepository environmentRepository() {
			return new CustomEnvironmentRepository();
		}

		public static void main(String[] args) throws Exception {
			SpringApplication.run(CustomEnvironmentRepositoryTests.TestApplication.class,
					args);
		}
	}

	static class CustomEnvironmentRepository implements EnvironmentRepository, Ordered {

		@Override
		public Environment findOne(String application, String profile, String label) {
			Environment e = new Environment("test", new String[0], "label", "version",
					"state");
			PropertySource p = new PropertySource("p", new HashMap<>());
			e.add(p);
			return e;
		}

		@Override
		public int getOrder() {
			return Ordered.LOWEST_PRECEDENCE;
		}
	}
}
