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
package org.springframework.cloud.config.server.environment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Ignore;
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
 * @author Roy Clarkson
 */
public class EnvironmentControllerTests {

	@Rule
	public ExpectedException expected = ExpectedException.none();

	private EnvironmentRepository repository = Mockito.mock(EnvironmentRepository.class);

	private EnvironmentController controller;

	private Environment environment = new Environment("foo", "master");

	@Before
	public void init() {
		this.controller = new EnvironmentController(this.repository);
	}

	@Test
	public void vanillaYaml() throws Exception {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("a.b.c", "d");
		this.environment.add(new PropertySource("one", map));
		Mockito.when(this.repository.findOne("foo", "bar", null)).thenReturn(this.environment);
		String yaml = this.controller.yaml("foo", "bar", false).getBody();
		assertEquals("a:\n  b:\n    c: d\n", yaml);
	}

	@Test
	public void propertyOverrideInYaml() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("a.b.c", "d");
		this.environment.add(new PropertySource("one", map));
		this.environment.addFirst(new PropertySource("two", Collections.singletonMap("a.b.c",
				"e")));
		Mockito.when(this.repository.findOne("foo", "bar", null)).thenReturn(this.environment);
		String yaml = this.controller.yaml("foo", "bar", false).getBody();
		assertEquals("a:\n  b:\n    c: e\n", yaml);
	}

	@Test
	public void placeholdersResolvedInYaml() throws Exception {
		whenPlaceholders();
		String yaml = this.controller.yaml("foo", "bar", true).getBody();
		assertEquals("a:\n  b:\n    c: bar\nfoo: bar\n", yaml);
	}

	@Test
	public void arrayInYaml() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("a.b[0]", "c");
		map.put("a.b[1]", "d");
		this.environment.add(new PropertySource("one", map));
		Mockito.when(this.repository.findOne("foo", "bar", null)).thenReturn(this.environment);
		String yaml = this.controller.yaml("foo", "bar", false).getBody();
		assertEquals("a:\n  b:\n  - c\n  - d\n", yaml);
	}

	@Test
	public void textAtTopLevelInYaml() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("document", "blah");
		this.environment.add(new PropertySource("one", map));
		Mockito.when(this.repository.findOne("foo", "bar", null)).thenReturn(this.environment);
		String yaml = this.controller.yaml("foo", "bar", false).getBody();
		assertEquals("blah\n", yaml);
	}

	@Test
	public void arrayAtTopLevelInYaml() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("document[0]", "c");
		map.put("document[1]", "d");
		this.environment.add(new PropertySource("one", map));
		Mockito.when(this.repository.findOne("foo", "bar", null)).thenReturn(this.environment);
		String yaml = this.controller.yaml("foo", "bar", false).getBody();
		assertEquals("- c\n- d\n", yaml);
	}

	@Test
	public void arrayObObjectAtTopLevelInYaml() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("document[0].a", "c");
		map.put("document[1].a", "d");
		this.environment.add(new PropertySource("one", map));
		Mockito.when(this.repository.findOne("foo", "bar", null)).thenReturn(this.environment);
		String yaml = this.controller.yaml("foo", "bar", false).getBody();
		assertEquals("- a: c\n- a: d\n", yaml);
	}

	@Test
	public void arrayOfObjectInYaml() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("a.b[0].c", "d");
		map.put("a.b[0].d", "e");
		map.put("a.b[1].c", "d");
		this.environment.add(new PropertySource("one", map));
		Mockito.when(this.repository.findOne("foo", "bar", null)).thenReturn(this.environment);
		String yaml = this.controller.yaml("foo", "bar", false).getBody();
		assertTrue("Wrong output: " + yaml,
				"a:\n  b:\n  - d: e\n    c: d\n  - c: d\n".equals(yaml)
				|| "a:\n  b:\n  - c: d\n    d: e\n  - c: d\n".equals(yaml));
	}

	@Test
	public void arrayOfObjectAtTopLevelInYaml() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("b[0].c", "d");
		map.put("b[1].c", "d");
		this.environment.add(new PropertySource("one", map));
		Mockito.when(this.repository.findOne("foo", "bar", null)).thenReturn(this.environment);
		String yaml = this.controller.yaml("foo", "bar", false).getBody();
		assertEquals("b:\n- c: d\n- c: d\n", yaml);
	}

	@Test
	public void arrayOfObjectNestedLevelInYaml() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("x.a.b[0].c", "d");
		map.put("x.a.b[1].c", "d");
		this.environment.add(new PropertySource("one", map));
		Mockito.when(this.repository.findOne("foo", "bar", null)).thenReturn(this.environment);
		String yaml = this.controller.yaml("foo", "bar", false).getBody();
		assertEquals("x:\n  a:\n    b:\n    - c: d\n    - c: d\n", yaml);
	}

	@Test
	public void placeholdersResolvedInProperties() throws Exception {
		whenPlaceholders();
		String text = this.controller.properties("foo", "bar", true).getBody();
		assertEquals("a.b.c: bar\nfoo: bar", text);
	}

	@Test
	public void placeholdersResolvedInJson() throws Exception {
		whenPlaceholders();
		String json = this.controller.jsonProperties("foo", "bar", true).getBody();
		assertEquals("{\"a\":{\"b\":{\"c\":\"bar\"}},\"foo\":\"bar\"}", json);
	}

	private void whenPlaceholders() {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("foo", "bar");
		this.environment.add(new PropertySource("one", map));
		this.environment.addFirst(new PropertySource("two", Collections.singletonMap("a.b.c", "${foo}")));
		Mockito.when(this.repository.findOne("foo", "bar", null)).thenReturn(this.environment);
	}

	@Test
	public void mappingForEnvironment() throws Exception {
		Mockito.when(this.repository.findOne("foo", "bar", null)).thenReturn(this.environment);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(this.controller).build();
		mvc.perform(MockMvcRequestBuilders.get("/foo/bar")).andExpect(
				MockMvcResultMatchers.status().isOk());
	}

	@Test
	public void mappingForLabelledEnvironment() throws Exception {
		Mockito.when(this.repository.findOne("foo", "bar", "other")).thenReturn(this.environment);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(this.controller).build();
		mvc.perform(MockMvcRequestBuilders.get("/foo/bar/other")).andExpect(
				MockMvcResultMatchers.status().isOk());
	}

	@Test
	public void mappingForYaml() throws Exception {
		Mockito.when(this.repository.findOne("foo", "bar", null)).thenReturn(this.environment);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(this.controller).build();
		mvc.perform(MockMvcRequestBuilders.get("/foo-bar.yml"))
		.andExpect(
				MockMvcResultMatchers.content().contentType(MediaType.TEXT_PLAIN))
				.andExpect(MockMvcResultMatchers.content().string("{}\n"));
	}

	@Test
	public void mappingForJson() throws Exception {
		Mockito.when(this.repository.findOne("foo", "bar", null)).thenReturn(this.environment);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(this.controller).build();
		mvc.perform(MockMvcRequestBuilders.get("/foo-bar.json"))
		.andExpect(
				MockMvcResultMatchers.content().contentType(
						MediaType.APPLICATION_JSON))
						.andExpect(MockMvcResultMatchers.content().string("{}"));
		;
	}

	@Test
	public void mappingForLabelledYaml() throws Exception {
		Mockito.when(this.repository.findOne("foo", "bar", "other")).thenReturn(this.environment);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(this.controller).build();
		mvc.perform(MockMvcRequestBuilders.get("/other/foo-bar.yml")).andExpect(
				MockMvcResultMatchers.content().contentType(MediaType.TEXT_PLAIN));
	}

	@Test
	public void mappingForLabelledProperties() throws Exception {
		Mockito.when(this.repository.findOne("foo", "bar", "other")).thenReturn(this.environment);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(this.controller).build();
		mvc.perform(MockMvcRequestBuilders.get("/other/foo-bar.properties")).andExpect(
				MockMvcResultMatchers.content().contentType(MediaType.TEXT_PLAIN));
	}

	@Test
	public void mappingForProperties() throws Exception {
		Mockito.when(this.repository.findOne("foo", "bar", null)).thenReturn(this.environment);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(this.controller).build();
		mvc.perform(MockMvcRequestBuilders.get("/foo-bar.properties")).andExpect(
				MockMvcResultMatchers.content().contentType(MediaType.TEXT_PLAIN));
	}

	@Test
	public void mappingForLabelledYamlWithHyphen() throws Exception {
		Mockito.when(this.repository.findOne("foo", "bar-spam", "other")).thenReturn(
				this.environment);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(this.controller).build();
		mvc.perform(MockMvcRequestBuilders.get("/other/foo-bar-spam.yml")).andExpect(
				MockMvcResultMatchers.status().isBadRequest());
	}

	@Test
	public void mappingforLabelledJsonProperties() throws Exception {
		Mockito.when(this.repository.findOne("foo", "bar", "other")).thenReturn(this.environment);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(this.controller).build();
		mvc.perform(MockMvcRequestBuilders.get("/other/foo-bar.json")).andExpect(
				MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON));
	}

	@Test
	public void mappingforJsonProperties() throws Exception {
		Mockito.when(this.repository.findOne("foo", "bar", null)).thenReturn(this.environment);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(this.controller).build();
		mvc.perform(MockMvcRequestBuilders.get("/foo-bar.json")).andExpect(
				MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON));
	}

	@Test
	public void mappingForLabelledJsonPropertiesWithHyphen() throws Exception {
		Mockito.when(this.repository.findOne("foo", "bar-spam", "other")).thenReturn(
				this.environment);
		MockMvc mvc = MockMvcBuilders.standaloneSetup(this.controller).build();
		mvc.perform(MockMvcRequestBuilders.get("/other/foo-bar-spam.json")).andExpect(
				MockMvcResultMatchers.status().isBadRequest());
	}

}
