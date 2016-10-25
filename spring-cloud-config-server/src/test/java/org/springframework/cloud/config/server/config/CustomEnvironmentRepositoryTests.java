/*
 * Copyright 2012-2015 the original author or authors.
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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.cloud.config.server.config.CustomEnvironmentRepositoryTests.TestApplication;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertFalse;

/**
 * @author Dave Syer
 *
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestApplication.class, properties = {
		"spring.config.name:configserver" }, webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext
public class CustomEnvironmentRepositoryTests {

	@LocalServerPort
	private int port;

	@Test
	public void contextLoads() {
		Environment environment = new TestRestTemplate().getForObject(
				"http://localhost:" + port + "/foo/development/", Environment.class);
		assertFalse(environment.getPropertySources().isEmpty());
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableConfigServer
	protected static class TestApplication {

		@Bean
		public EnvironmentRepository environmentRepository() {
			return new EnvironmentRepository() {

				@Override
				public Environment findOne(String application, String profile,
						String label) {
					return new Environment("test", new String[0], "label", "version",
							"state");
				}
			};
		}

		public static void main(String[] args) throws Exception {
			SpringApplication.run(CustomEnvironmentRepositoryTests.TestApplication.class,
					args);
		}

	}

}
