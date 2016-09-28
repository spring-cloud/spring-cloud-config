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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.TestRestTemplate;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.cloud.config.server.config.CustomEnvironmentRepositoryTests.TestApplication;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.junit.Assert.assertFalse;

/**
 * @author Dave Syer
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestApplication.class)
@IntegrationTest({"server.port:0", "spring.config.name:configserver"})
@WebAppConfiguration
@ActiveProfiles("test")
@DirtiesContext
public class CustomEnvironmentRepositoryTests {

	@Value("${local.server.port}")
	private int port;

	@Test
	public void contextLoads() {
		Environment environment = new TestRestTemplate().getForObject("http://localhost:"
				+ port + "/foo/development/", Environment.class);
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
				public Environment findOne(String application, String profile, String label) {
					return new Environment("test", new String[0], "label", "version");
				}
			};
		}

		public static void main(String[] args) throws Exception {
			SpringApplication.run(CustomEnvironmentRepositoryTests.TestApplication.class, args);
		}

	}

}
