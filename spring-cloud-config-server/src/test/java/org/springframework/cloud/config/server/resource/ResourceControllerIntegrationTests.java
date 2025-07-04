/*
 * Copyright 2013-2025 the original author or authors.
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

package org.springframework.cloud.config.server.resource;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.test.LocalServerPort;
import org.springframework.boot.web.server.test.client.TestRestTemplate;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.encryption.ResourceEncryptor;
import org.springframework.cloud.config.server.environment.EnvironmentController;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.environment.NoSuchLabelException;
import org.springframework.cloud.config.server.resource.ResourceControllerIntegrationTests.ControllerConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Dave Syer
 * @author Daniel Lavoie
 *
 */
@SpringBootTest(classes = ControllerConfiguration.class, webEnvironment = RANDOM_PORT)
@DirtiesContext
public class ResourceControllerIntegrationTests {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mvc;

	@Autowired
	private EnvironmentRepository repository;

	@Autowired
	private ResourceRepository resources;

	@LocalServerPort
	int port;

	@BeforeEach
	public void init() {
		Mockito.reset(this.repository, this.resources);
		this.mvc = MockMvcBuilders.webAppContextSetup(this.context).build();
	}

	@Test
	public void environmentNoLabel() throws Exception {
		when(this.repository.findOne("foo", "default", "master", false)).thenReturn(new Environment("foo", "default"));
		when(this.resources.findOne("foo", "default", "master", "foo.txt"))
			.thenReturn(new ClassPathResource("resource-controller/foo.txt"));
		this.mvc.perform(MockMvcRequestBuilders.get("/foo/default/master/foo.txt"))
			.andExpect(MockMvcResultMatchers.status().isOk());
		verify(this.repository).findOne("foo", "default", "master", false);
		verify(this.resources).findOne("foo", "default", "master", "foo.txt");
	}

	@Test
	public void resource() throws Exception {
		when(this.repository.findOne("foo", "default", "master", false))
			.thenReturn(new Environment("foo", "default", "master"));
		when(this.resources.findOne("foo", "default", "master", "foo.txt"))
			.thenReturn(new ClassPathResource("resource-controller/foo.txt"));
		this.mvc.perform(MockMvcRequestBuilders.get("/foo/default/master/foo.txt"))
			.andExpect(MockMvcResultMatchers.status().isOk());
		verify(this.repository).findOne("foo", "default", "master", false);
		verify(this.resources).findOne("foo", "default", "master", "foo.txt");
	}

	@Test
	public void resourceHttp() throws Exception {
		when(this.repository.findOne("foo", "default", "master", false))
			.thenReturn(new Environment("foo", "default", "master"));
		when(this.resources.findOne("foo", "default", "master", "foo.txt"))
			.thenReturn(new ClassPathResource("resource-controller/foo.txt"));

		ResponseEntity<String> response = new TestRestTemplate()
			.getForEntity("http://localhost:" + port + "/foo/default/master/foo.txt", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		verify(this.repository).findOne("foo", "default", "master", false);
		verify(this.resources).findOne("foo", "default", "master", "foo.txt");
	}

	@Test
	public void resourceHttpDoesNotExist() throws Exception {
		when(this.resources.findOne("foo", "default", "master", "doesNotExist.txt"))
			.thenThrow(new NoSuchResourceException("Does not exist"));

		ResponseEntity<String> response = new TestRestTemplate()
			.getForEntity("http://localhost:" + port + "/foo/default/master/doesNotExist.txt", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		verify(this.resources).findOne("foo", "default", "master", "doesNotExist.txt");
	}

	@Test
	public void resourceNoLabel() throws Exception {
		when(this.repository.findOne("foo", "default", null, false))
			.thenReturn(new Environment("foo", "default", "master"));
		when(this.resources.findOne("foo", "default", null, "foo.txt"))
			.thenReturn(new ClassPathResource("resource-controller/foo.txt"));
		this.mvc.perform(MockMvcRequestBuilders.get("/foo/default/foo.txt").param("useDefaultLabel", ""))
			.andExpect(MockMvcResultMatchers.status().isOk());
		verify(this.repository).findOne("foo", "default", null, false);
		verify(this.resources).findOne("foo", "default", null, "foo.txt");
	}

	@Test
	public void resourceNoLabelHttp() throws Exception {
		when(this.repository.findOne("foo", "default", null, false))
			.thenReturn(new Environment("foo", "default", "master"));
		when(this.resources.findOne("foo", "default", null, "foo.txt"))
			.thenReturn(new ClassPathResource("resource-controller/foo.txt"));

		ResponseEntity<String> response = new TestRestTemplate()
			.getForEntity("http://localhost:" + port + "/foo/default/foo.txt?useDefaultLabel", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		verify(this.repository).findOne("foo", "default", null, false);
		verify(this.resources).findOne("foo", "default", null, "foo.txt");
	}

	@Test
	public void binaryResourceNoLabel() throws Exception {
		when(this.repository.findOne("foo", "default", null)).thenReturn(new Environment("foo", "default", "master"));
		when(this.resources.findOne("foo", "default", null, "foo.txt"))
			.thenReturn(new ClassPathResource("resource-controller/foo.txt"));
		this.mvc
			.perform(MockMvcRequestBuilders.get("/foo/default/foo.txt")
				.param("useDefaultLabel", "")
				.header(HttpHeaders.ACCEPT, MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE))
			.andExpect(MockMvcResultMatchers.status().isOk());
		verify(this.repository).findOne("foo", "default", null);
		verify(this.resources).findOne("foo", "default", null, "foo.txt");
	}

	@Test
	public void resourceWithMissingLabel() throws Exception {
		when(this.resources.findOne("foo", "default", "missing", "foo.txt"))
			.thenThrow(new NoSuchLabelException("Planned"));
		this.mvc.perform(MockMvcRequestBuilders.get("/foo/default/missing/foo.txt"))
			.andExpect(MockMvcResultMatchers.status().isNotFound());
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	public static class ControllerConfiguration {

		@Autowired(required = false)
		private Map<String, ResourceEncryptor> resourceEncryptorMap = new HashMap<>();

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
		public EnvironmentController environmentController() {
			return new EnvironmentController(environmentRepository());
		}

		@Bean
		public ResourceController resourceController() {
			return new ResourceController(resourceRepository(), environmentRepository(), resourceEncryptorMap);
		}

		@Bean
		public ResourceControllerAdvice resourceControllerAdvice() {
			return new ResourceControllerAdvice();
		}

	}

}
