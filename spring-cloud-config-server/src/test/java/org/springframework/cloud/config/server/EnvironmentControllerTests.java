/*
 * Copyright 2013-2014 the original author or authors.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * @author Dave Syer
 *
 */
public class EnvironmentControllerTests {

	@Rule
	public ExpectedException expected = ExpectedException.none();

	private EnvironmentRepository repository = Mockito.mock(EnvironmentRepository.class);

	private EnvironmentController controller = new EnvironmentController(repository,
			new EncryptionController());

	private Environment environment = new Environment("foo", "master");

	@Test
	public void vanillaYaml() throws Exception {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("a.b.c", "d");
		environment.add(new PropertySource("one", map));
		Mockito.when(repository.findOne("foo", "bar", "master")).thenReturn(environment);
		String yaml = controller.yaml("foo", "bar").getBody();
		assertEquals("a:\n  b:\n    c: d\n", yaml);
	}

	@Test
	public void propertyOverrideInYaml() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("a.b.c", "d");
		environment.add(new PropertySource("one", map));
		environment.addFirst(new PropertySource("two", Collections.singletonMap("a.b.c",
				"e")));
		Mockito.when(repository.findOne("foo", "bar", "master")).thenReturn(environment);
		String yaml = controller.yaml("foo", "bar").getBody();
		assertEquals("a:\n  b:\n    c: e\n", yaml);
	}

	@Test
	public void arrayInYaml() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("a.b[0]", "c");
		map.put("a.b[1]", "d");
		environment.add(new PropertySource("one", map));
		Mockito.when(repository.findOne("foo", "bar", "master")).thenReturn(environment);
		String yaml = controller.yaml("foo", "bar").getBody();
		assertEquals("a:\n  b:\n  - c\n  - d\n", yaml);
	}

	@Test
	public void arrayOfObjectInYaml() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("a.b[0].c", "d");
		map.put("a.b[0].d", "e");
		map.put("a.b[1].c", "d");
		environment.add(new PropertySource("one", map));
		Mockito.when(repository.findOne("foo", "bar", "master")).thenReturn(environment);
		String yaml = controller.yaml("foo", "bar").getBody();
		assertTrue("Wrong output: " + yaml,
				"a:\n  b:\n  - d: e\n    c: d\n  - c: d\n".equals(yaml)
						|| "a:\n  b:\n  - c: d\n    d: e\n  - c: d\n".equals(yaml));
	}

	@Test
	public void arrayOfObjectAtTopLevelInYaml() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("b[0].c", "d");
		map.put("b[1].c", "d");
		environment.add(new PropertySource("one", map));
		Mockito.when(repository.findOne("foo", "bar", "master")).thenReturn(environment);
		String yaml = controller.yaml("foo", "bar").getBody();
		assertEquals("b:\n- c: d\n- c: d\n", yaml);
	}

	@Test
	public void arrayOfObjectNestedLevelInYaml() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("x.a.b[0].c", "d");
		map.put("x.a.b[1].c", "d");
		environment.add(new PropertySource("one", map));
		Mockito.when(repository.findOne("foo", "bar", "master")).thenReturn(environment);
		String yaml = controller.yaml("foo", "bar").getBody();
		assertEquals("x:\n  a:\n    b:\n    - c: d\n    - c: d\n", yaml);
	}

	@Test
	public void mappingForEnvironment() throws Exception {
		Mockito.when(repository.findOne("foo", "bar", "master")).thenReturn(environment);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
		mvc.perform(MockMvcRequestBuilders.get("/foo/bar")).andExpect(
				MockMvcResultMatchers.status().isOk());
	}

	@Test
	public void mappingForLabelledEnvironment() throws Exception {
		Mockito.when(repository.findOne("foo", "bar", "other")).thenReturn(environment);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
		mvc.perform(MockMvcRequestBuilders.get("/foo/bar/other")).andExpect(
				MockMvcResultMatchers.status().isOk());
	}

	@Test
	public void mappingForYaml() throws Exception {
		Mockito.when(repository.findOne("foo", "bar", "master")).thenReturn(environment);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
		mvc.perform(MockMvcRequestBuilders.get("/foo-bar.yml")).andExpect(
				MockMvcResultMatchers.content().contentType(MediaType.TEXT_PLAIN));
	}

	@Test
	public void mappingForLabelledYaml() throws Exception {
		Mockito.when(repository.findOne("foo", "bar", "other")).thenReturn(environment);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
		mvc.perform(MockMvcRequestBuilders.get("/other/foo-bar.yml")).andExpect(
				MockMvcResultMatchers.content().contentType(MediaType.TEXT_PLAIN));
	}

	@Test
	public void mappingForLabelledProperties() throws Exception {
		Mockito.when(repository.findOne("foo", "bar", "other")).thenReturn(environment);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
		mvc.perform(MockMvcRequestBuilders.get("/other/foo-bar.properties")).andExpect(
				MockMvcResultMatchers.content().contentType(MediaType.TEXT_PLAIN));
	}

	@Test
	public void mappingForProperties() throws Exception {
		Mockito.when(repository.findOne("foo", "bar", "master")).thenReturn(environment);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
		mvc.perform(MockMvcRequestBuilders.get("/foo-bar.properties")).andExpect(
				MockMvcResultMatchers.content().contentType(MediaType.TEXT_PLAIN));
	}

	@Test
	public void mappingForLabelledYamlWithHyphen() throws Exception {
		Mockito.when(repository.findOne("foo", "bar-spam", "other")).thenReturn(
				environment);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
		mvc.perform(MockMvcRequestBuilders.get("/other/foo-bar-spam.yml")).andExpect(
				MockMvcResultMatchers.status().isBadRequest());
	}

	@Test
	public void mappingforLabelledJsonProperties() throws Exception {
		Mockito.when(repository.findOne("foo", "bar", "other")).thenReturn(environment);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
		mvc.perform(MockMvcRequestBuilders.get("/other/foo-bar.json")).andExpect(
				MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON));
	}

	@Test
	public void mappingforJsonProperties() throws Exception {
		Mockito.when(repository.findOne("foo", "bar", "master")).thenReturn(environment);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
		mvc.perform(MockMvcRequestBuilders.get("/foo-bar.json")).andExpect(
				MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON));
	}

	@Test
	public void mappingForLabelledJsonPropertiesWithHyphen() throws Exception {
		Mockito.when(repository.findOne("foo", "bar-spam", "other")).thenReturn(
				environment);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
		mvc.perform(MockMvcRequestBuilders.get("/other/foo-bar-spam.json")).andExpect(
				MockMvcResultMatchers.status().isBadRequest());
	}

	@Test
	public void allowOverrideFalse() throws Exception {
		controller.setOverrides(Collections.singletonMap("foo", "bar"));
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("a.b.c", "d");
		environment.add(new PropertySource("one", map));
		Mockito.when(repository.findOne("foo", "bar", "master")).thenReturn(environment);
		assertEquals("{foo=bar}", controller.master("foo", "bar").getPropertySources()
				.get(0).getSource().toString());
	}

}
