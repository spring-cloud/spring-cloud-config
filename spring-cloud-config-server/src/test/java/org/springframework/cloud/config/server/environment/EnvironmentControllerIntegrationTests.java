/*
 * Copyright 2014-2022 the original author or authors.
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

package org.springframework.cloud.config.server.environment;

import java.util.HashMap;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Dave Syer
 * @author Roy Clarkson
 * @author Ivan Corrales Solera
 * @author Henning Pöttker
 */
class EnvironmentControllerIntegrationTests {

	abstract static class TestCases {

		@Autowired
		private WebApplicationContext context;

		private MockMvc mvc;

		@Autowired
		private EnvironmentRepository repository;

		private final Environment environment = new Environment("foo", "default");

		@BeforeEach
		public void init() {
			Mockito.reset(this.repository);
			this.mvc = MockMvcBuilders.webAppContextSetup(this.context).build();
			this.environment.add(new PropertySource("foo", new HashMap<>()));
		}

		@Test
		public void environmentNoLabel() throws Exception {
			when(this.repository.findOne("foo", "default", null, false)).thenReturn(this.environment);
			this.mvc.perform(MockMvcRequestBuilders.get("/foo/default"))
					.andExpect(MockMvcResultMatchers.status().isOk());
			verify(this.repository).findOne("foo", "default", null, false);
		}

		@Test
		public void profileWithDash() throws Exception {
			Environment dashEnvironment = new Environment("foo", "dev-db");
			dashEnvironment.add(new PropertySource("foo", new HashMap<>()));
			when(this.repository.findOne("foo", "dev-db", null, false)).thenReturn(dashEnvironment);
			this.mvc.perform(MockMvcRequestBuilders.get("/foo/dev-db"))
					.andExpect(MockMvcResultMatchers.status().isOk());
			verify(this.repository).findOne("foo", "dev-db", null, false);
		}

		@ParameterizedTest
		@ValueSource(strings = { "yml", "yaml", "json", "properties" })
		public void profileContainingExtensionKeyword(String extensionKeyword) throws Exception {
			String profiles = "dev-" + extensionKeyword;
			Environment dashEnvironment = new Environment("foo", profiles);
			dashEnvironment.add(new PropertySource("foo", new HashMap<>()));
			when(this.repository.findOne("foo", profiles, null, false)).thenReturn(dashEnvironment);
			this.mvc.perform(MockMvcRequestBuilders.get("/foo/" + profiles))
					.andExpect(MockMvcResultMatchers.status().isOk());
			verify(this.repository).findOne("foo", profiles, null, false);
		}

		@ParameterizedTest
		@ValueSource(strings = { "yml", "yaml", "json", "properties" })
		public void profileHavingAnExtension(String extensionKeyword) throws Exception {
			String profiles = "dev." + extensionKeyword;
			Environment dashEnvironment = new Environment("foo", profiles);
			dashEnvironment.add(new PropertySource("foo", new HashMap<>()));
			this.mvc.perform(MockMvcRequestBuilders.get("/foo/" + profiles))
					.andExpect(MockMvcResultMatchers.status().isNotFound());
			verifyNoInteractions(this.repository);
		}

		@Test
		public void propertiesNoLabel() throws Exception {
			when(this.repository.findOne("foo", "default", null, false)).thenReturn(this.environment);
			this.mvc.perform(MockMvcRequestBuilders.get("/foo-default.properties"))
					.andExpect(MockMvcResultMatchers.status().isOk());
			verify(this.repository).findOne("foo", "default", null, false);
		}

		@Test
		public void propertiesLabel() throws Exception {
			when(this.repository.findOne("foo", "default", "label", false)).thenReturn(this.environment);
			this.mvc.perform(MockMvcRequestBuilders.get("/label/foo-default.properties"))
					.andExpect(MockMvcResultMatchers.status().isOk());
			verify(this.repository).findOne("foo", "default", "label", false);
		}

		@Test
		public void propertiesLabelWhenApplicationNameContainsHyphen() throws Exception {
			Environment environment = new Environment("foo-bar", "default");
			environment.add(new PropertySource("foo", new HashMap<>()));
			when(this.repository.findOne("foo-bar", "default", "label", false)).thenReturn(this.environment);
			this.mvc.perform(MockMvcRequestBuilders.get("/label/foo-bar-default.properties"))
					.andExpect(MockMvcResultMatchers.status().isOk());
			verify(this.repository).findOne("foo-bar", "default", "label", false);
		}

		@Test
		public void propertiesLabelWithSlash() throws Exception {

			when(this.repository.findOne("foo", "default", "label/spam", false)).thenReturn(this.environment);
			this.mvc.perform(MockMvcRequestBuilders.get("/label(_)spam/foo-default.properties"))
					.andExpect(MockMvcResultMatchers.status().isOk());
			verify(this.repository).findOne("foo", "default", "label/spam", false);
		}

		@Test
		public void environmentWithLabel() throws Exception {
			when(this.repository.findOne("foo", "default", "awesome", false)).thenReturn(this.environment);
			this.mvc.perform(MockMvcRequestBuilders.get("/foo/default/awesome"))
					.andExpect(MockMvcResultMatchers.status().isOk());
		}

		@Test
		public void environmentWithMissingLabel() throws Exception {
			when(this.repository.findOne("foo", "default", "missing", false))
					.thenThrow(new NoSuchLabelException("Planned"));
			this.mvc.perform(MockMvcRequestBuilders.get("/foo/default/missing"))
					.andExpect(MockMvcResultMatchers.status().isNotFound());
		}

		@Test
		public void environmentWithMissingRepo() throws Exception {
			when(this.repository.findOne("foo", "default", "missing", false))
					.thenThrow(new NoSuchRepositoryException("Planned"));
			this.mvc.perform(MockMvcRequestBuilders.get("/foo/default/missing"))
					.andExpect(MockMvcResultMatchers.status().isNotFound());
		}

		@Test
		public void environmentWithLabelContainingPeriod() throws Exception {
			when(this.repository.findOne("foo", "default", "1.0.0", false)).thenReturn(this.environment);
			this.mvc.perform(MockMvcRequestBuilders.get("/foo/default/1.0.0"))
					.andExpect(MockMvcResultMatchers.status().isOk());
		}

		@Test
		public void environmentWithLabelContainingSlash() throws Exception {
			when(this.repository.findOne("foo", "default", "feature/puff", false)).thenReturn(this.environment);
			this.mvc.perform(MockMvcRequestBuilders.get("/foo/default/feature(_)puff"))
					.andExpect(MockMvcResultMatchers.status().isOk())
					.andExpect(MockMvcResultMatchers.content().string(Matchers.containsString("\"propertySources\":")));
		}

		@Test
		public void environmentWithApplicationContainingSlash() throws Exception {
			Environment environment = new Environment("foo/app", "default");
			environment.add(new PropertySource("foo", new HashMap<>()));
			when(this.repository.findOne("foo/app", "default", null, false)).thenReturn(environment);
			this.mvc.perform(MockMvcRequestBuilders.get("/foo(_)app/default"))
					.andExpect(MockMvcResultMatchers.status().isOk())
					.andExpect(MockMvcResultMatchers.content().string(Matchers.containsString("\"propertySources\":")));
		}

	}

	@SpringBootTest(classes = ControllerConfiguration.class)
	static class PathPatternParserTests extends TestCases {

	}

	@SpringBootTest(classes = ControllerConfiguration.class)
	@TestPropertySource(properties = "spring.mvc.pathmatch.matching-strategy=ant_path_matcher")
	static class AntPathMatcherTests extends TestCases {

	}

	@Configuration
	@Import({ PropertyPlaceholderAutoConfiguration.class, WebMvcAutoConfiguration.class })
	static class ControllerConfiguration {

		@Bean
		EnvironmentRepository environmentRepository() {
			return Mockito.mock(EnvironmentRepository.class);
		}

		@Bean
		EnvironmentController controller() {
			return new EnvironmentController(environmentRepository());
		}

	}

}
