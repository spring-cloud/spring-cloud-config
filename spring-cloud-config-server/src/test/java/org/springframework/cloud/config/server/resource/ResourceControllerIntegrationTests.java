/*
 * Copyright 2013-2015 the original author or authors.
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
package org.springframework.cloud.config.server.resource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.resource.ResourceControllerIntegrationTests.ControllerConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * @author Dave Syer
 *
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ControllerConfiguration.class)
public class ResourceControllerIntegrationTests {

	@Autowired
	private WebApplicationContext context;
	private MockMvc mvc;
	@Autowired
	private EnvironmentRepository repository;
	@Autowired
	private ResourceRepository resources;

	@Before
	public void init() {
		Mockito.reset(this.repository);
		this.mvc = MockMvcBuilders.webAppContextSetup(this.context).build();
	}

	@Test
	public void environmentNoLabel() throws Exception {
		Mockito.when(this.repository.findOne("foo", "default", "master"))
				.thenReturn(new Environment("foo", "default"));
		Mockito.when(this.resources.findOne("foo", "default", "master", "foo.txt"))
				.thenReturn(new ByteArrayResource("hello".getBytes()));
		this.mvc.perform(MockMvcRequestBuilders.get("/foo/default/master/foo.txt"))
				.andExpect(MockMvcResultMatchers.status().isOk());
		Mockito.verify(this.repository).findOne("foo", "default", "master");
		Mockito.verify(this.resources).findOne("foo", "default", "master", "foo.txt");
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
		public ResourceRepository resourceRepository() {
			ResourceRepository repository = Mockito.mock(ResourceRepository.class);
			return repository;
		}

		@Bean
		public ResourceController controller() {
			return new ResourceController(resourceRepository(), environmentRepository());
		}

	}

}
