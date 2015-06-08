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

package org.springframework.cloud.config.server;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.encryption.CipherEnvironmentEncryptor;
import org.springframework.cloud.config.server.encryption.SingleTextEncryptorLocator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * @author Dave Syer
 * @author Roy Clarkson
 */
public class EnvironmentControllerIntegrationTests {

	private MockMvc mvc;
	private EnvironmentRepository repository = Mockito.mock(EnvironmentRepository.class);;

	@Before
	public void init() {
		Mockito.when(repository.getDefaultLabel()).thenReturn("master");
		mvc = MockMvcBuilders.standaloneSetup(
				new EnvironmentController(repository, new CipherEnvironmentEncryptor(new SingleTextEncryptorLocator())))
				.build();
	}

	@Test
	public void environmentNoLabel() throws Exception {
		Mockito.when(repository.findOne("foo", "default", "master")).thenReturn(
				new Environment("foo", "default"));
		mvc.perform(MockMvcRequestBuilders.get("/foo/default")).andExpect(
				MockMvcResultMatchers.status().isOk());
	}

	@Test
	public void environmentWithLabel() throws Exception {
		Mockito.when(repository.findOne("foo", "default", "awesome")).thenReturn(
				new Environment("foo", "default"));
		mvc.perform(MockMvcRequestBuilders.get("/foo/default/awesome")).andExpect(
				MockMvcResultMatchers.status().isOk());
	}
	@Test
	public void environmentWithLabelContainingPeriod() throws Exception {
		Mockito.when(repository.findOne("foo", "default", "1.0.0")).thenReturn(
				new Environment("foo", "default"));
		mvc.perform(MockMvcRequestBuilders.get("/foo/default/1.0.0")).andExpect(
				MockMvcResultMatchers.status().isOk());
	}

}
