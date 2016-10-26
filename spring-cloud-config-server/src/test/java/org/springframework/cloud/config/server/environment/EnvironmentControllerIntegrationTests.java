/*
 * Copyright 2014-2015 the original author or authors.
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

package org.springframework.cloud.config.server.environment;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.environment.EnvironmentControllerIntegrationTests.ControllerConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * @author Dave Syer
 * @author Roy Clarkson
 * @author Ivan Corrales Solera
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ControllerConfiguration.class)
public class EnvironmentControllerIntegrationTests {

	@Autowired
	private WebApplicationContext context;
	private MockMvc mvc;
	@Autowired
	private EnvironmentRepository repository;

	@Before
	public void init() {
		Mockito.reset(this.repository);
		this.mvc = MockMvcBuilders.webAppContextSetup(this.context).build();
	}

	@Test
	public void environmentNoLabel() throws Exception {
		Mockito.when(this.repository.findOne("foo", "default", null)).thenReturn(
				new Environment("foo", "default"));
		this.mvc.perform(MockMvcRequestBuilders.get("/foo/default")).andExpect(
				MockMvcResultMatchers.status().isOk());
		Mockito.verify(this.repository).findOne("foo", "default", null);
	}

	@Test
	public void propertiesNoLabel() throws Exception {
		Mockito.when(this.repository.findOne("foo", "default", null)).thenReturn(
				new Environment("foo", "default"));
		this.mvc.perform(MockMvcRequestBuilders.get("/foo-default.properties")).andExpect(
				MockMvcResultMatchers.status().isOk());
		Mockito.verify(this.repository).findOne("foo", "default", null);
	}

	@Test
	public void propertiesLabel() throws Exception {
		Mockito.when(this.repository.findOne("foo", "default", "label")).thenReturn(
				new Environment("foo", "default"));
		this.mvc.perform(MockMvcRequestBuilders.get("/label/foo-default.properties")).andExpect(
				MockMvcResultMatchers.status().isOk());
		Mockito.verify(this.repository).findOne("foo", "default", "label");
	}

	@Test
	public void propertiesLabelWhenApplicationNameContainsHyphen() throws Exception {
		Mockito.when(this.repository.findOne("foo-bar", "default", "label")).thenReturn(new Environment("foo-bar", "default"));
		this.mvc.perform(MockMvcRequestBuilders.get("/label/foo-bar-default.properties")).andExpect(
				MockMvcResultMatchers.status().isOk());
		Mockito.verify(this.repository).findOne("foo-bar", "default", "label");
	}

	@Test
	public void propertiesLabelWithSlash() throws Exception {
		Mockito.when(this.repository.findOne("foo", "default", "label/spam")).thenReturn(
				new Environment("foo", "default"));
		this.mvc.perform(MockMvcRequestBuilders.get("/label(_)spam/foo-default.properties")).andExpect(
				MockMvcResultMatchers.status().isOk());
		Mockito.verify(this.repository).findOne("foo", "default", "label/spam");
	}

	@Test
	public void environmentWithLabel() throws Exception {
		Mockito.when(this.repository.findOne("foo", "default", "awesome")).thenReturn(
				new Environment("foo", "default"));
		this.mvc.perform(MockMvcRequestBuilders.get("/foo/default/awesome")).andExpect(
				MockMvcResultMatchers.status().isOk());
	}

	@Test
	public void environmentWithLabelContainingPeriod() throws Exception {
		Mockito.when(this.repository.findOne("foo", "default", "1.0.0")).thenReturn(
				new Environment("foo", "default"));
		this.mvc.perform(MockMvcRequestBuilders.get("/foo/default/1.0.0")).andExpect(
				MockMvcResultMatchers.status().isOk());
	}

	@Test
	public void environmentWithLabelContainingSlash() throws Exception {
		Mockito.when(this.repository.findOne("foo", "default", "feature/puff"))
		.thenReturn(new Environment("foo", "default"));
		this.mvc.perform(MockMvcRequestBuilders.get("/foo/default/feature(_)puff"))
		.andExpect(MockMvcResultMatchers.status().isOk())
		.andExpect(MockMvcResultMatchers.content().string(Matchers.containsString("\"propertySources\":")));
	}

	@Configuration
	@EnableWebMvc
	@Import(PropertyPlaceholderAutoConfiguration.class)
	public static class ControllerConfiguration {

		@Bean
		public EnvironmentRepository environmentRepository() {
			EnvironmentRepository repository = Mockito.mock(EnvironmentRepository.class);
			return repository;
		}

		@Bean
		public EnvironmentController controller() {
			return new EnvironmentController(environmentRepository());
		}

	}

}
