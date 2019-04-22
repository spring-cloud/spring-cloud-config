/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.config.server.config;

import java.util.HashMap;
import java.util.List;

import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.util.SystemReader;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.environment.EnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.support.EnvironmentRepositoryProperties;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ryan Baxter
 */
public class CustomCompositeEnvironmentRepositoryTests {

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = CustomCompositeEnvironmentRepositoryTests.StaticTests.Config.class, properties = {
			"spring.config.name:compositeconfigserver",
			"spring.cloud.config.server.git.uri:file:./target/repos/config-repo",
			"spring.cloud.config.server.git.order:1" }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	@ActiveProfiles({ "test", "git" })
	@DirtiesContext
	public static class StaticTests {

		@LocalServerPort
		private int port;

		@BeforeClass
		public static void init() throws Exception {
			// mock Git configuration to make tests independent of local Git configuration
			SystemReader.setInstance(new MockSystemReader());

			ConfigServerTestUtils.prepareLocalRepo();
		}

		@Test
		public void contextLoads() {
			Environment environment = new TestRestTemplate().getForObject(
					"http://localhost:" + this.port + "/foo/development/",
					Environment.class);
			List<PropertySource> propertySources = environment.getPropertySources();
			assertThat(3).isEqualTo(propertySources.size());
			assertThat("overrides").isEqualTo(propertySources.get(0).getName());
			assertThat(propertySources.get(1).getName().contains("config-repo")).isTrue();
			assertThat("p").isEqualTo(propertySources.get(2).getName());
		}

		@Configuration
		@EnableAutoConfiguration
		@EnableConfigServer
		protected static class Config {

			public static void main(String[] args) throws Exception {
				SpringApplication.run(
						CustomEnvironmentRepositoryTests.TestApplication.class, args);
			}

			@Bean
			public EnvironmentRepository environmentRepository() {
				return new CustomEnvironmentRepository(
						new CustomEnvironmentProperties("p"));
			}

		}

	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = CustomCompositeEnvironmentRepositoryTests.ListTests.Config.class, properties = {
			"spring.config.name:compositeconfigserver",
			"spring.cloud.config.server.composite[0].type:git",
			"spring.cloud.config.server.composite[0].uri:file:./target/repos/config-repo",
			"spring.cloud.config.server.composite[1].type:custom",
			"spring.cloud.config.server.composite[1].propertySourceName:p" }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	@ActiveProfiles({ "test", "composite" })
	@DirtiesContext
	public static class ListTests {

		@LocalServerPort
		private int port;

		@BeforeClass
		public static void init() throws Exception {
			// mock Git configuration to make tests independent of local Git configuration
			SystemReader.setInstance(new MockSystemReader());

			ConfigServerTestUtils.prepareLocalRepo();
		}

		@Test
		public void contextLoads() {
			Environment environment = new TestRestTemplate().getForObject(
					"http://localhost:" + this.port + "/foo/development/",
					Environment.class);
			List<PropertySource> propertySources = environment.getPropertySources();
			assertThat(3).isEqualTo(propertySources.size());
			assertThat("overrides").isEqualTo(propertySources.get(0).getName());
			assertThat(propertySources.get(1).getName().contains("config-repo")).isTrue();
			assertThat("p").isEqualTo(propertySources.get(2).getName());
		}

		@Configuration
		@EnableAutoConfiguration
		@EnableConfigServer
		protected static class Config {

			public static void main(String[] args) throws Exception {
				SpringApplication.run(
						CustomEnvironmentRepositoryTests.TestApplication.class, args);
			}

			@Bean
			public CustomEnvironmentRepositoryFactory customEnvironmentRepositoryFactory(
					ConfigurableEnvironment environment) {
				return new CustomEnvironmentRepositoryFactory();
			}

		}

	}

	static class CustomEnvironmentRepositoryFactory implements
			EnvironmentRepositoryFactory<CustomEnvironmentRepository, CustomEnvironmentProperties> {

		@Override
		public CustomEnvironmentRepository build(
				CustomEnvironmentProperties environmentProperties) throws Exception {
			return new CustomEnvironmentRepository(environmentProperties);
		}

	}

	static class CustomEnvironmentProperties implements EnvironmentRepositoryProperties {

		private String propertySourceName;

		CustomEnvironmentProperties() {
		}

		CustomEnvironmentProperties(String propertySourceName) {
			this.propertySourceName = propertySourceName;
		}

		public String getPropertySourceName() {
			return this.propertySourceName;
		}

		public void setPropertySourceName(String propertySourceName) {
			this.propertySourceName = propertySourceName;
		}

		@Override
		public void setOrder(int order) {

		}

	}

	static class CustomEnvironmentRepository implements EnvironmentRepository, Ordered {

		private final CustomEnvironmentProperties properties;

		CustomEnvironmentRepository(CustomEnvironmentProperties properties) {
			this.properties = properties;
		}

		@Override
		public Environment findOne(String application, String profile, String label) {
			Environment e = new Environment("test", new String[0], "label", "version",
					"state");
			PropertySource p = new PropertySource(this.properties.getPropertySourceName(),
					new HashMap<>());
			e.add(p);
			return e;
		}

		@Override
		public int getOrder() {
			return Ordered.LOWEST_PRECEDENCE;
		}

	}

}
