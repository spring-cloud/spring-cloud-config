/*
 * Copyright 2013-2022 the original author or authors.
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

import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.yaml.snakeyaml.Yaml;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import org.springframework.web.util.pattern.PathPatternParser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Dave Syer
 * @author Roy Clarkson
 * @author Ivan Corrales Solera
 * @author Daniel Frey
 * @author Ian Bondoc
 * @author Henning Pöttker
 */
class EnvironmentControllerTests {

	private final EnvironmentRepository repository = mock(EnvironmentRepository.class);

	private EnvironmentController controller;

	private final Environment environment = new Environment("foo", "master");

	@BeforeEach
	public void init() {
		this.controller = new EnvironmentController(this.repository);
		this.environment.add(new PropertySource("foo", new HashMap<>()));
	}

	@AfterEach
	public void clean() {
		System.clearProperty("foo");
	}

	@Test
	public void vanillaYaml() throws Exception {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("a.b.c", "d");
		this.environment.add(new PropertySource("one", map));
		when(this.repository.findOne("foo", "bar", null, false)).thenReturn(this.environment);
		String yaml = this.controller.yaml("foo", "bar", false).getBody();
		assertThat(yaml).isEqualTo("a:\n  b:\n    c: d\n");
	}

	@Test
	public void propertyOverrideInYaml() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("a.b.c", "d");
		this.environment.add(new PropertySource("one", map));
		this.environment.addFirst(new PropertySource("two", Collections.singletonMap("a.b.c", "e")));
		when(this.repository.findOne("foo", "bar", null, false)).thenReturn(this.environment);
		String yaml = this.controller.yaml("foo", "bar", false).getBody();
		assertThat(yaml).isEqualTo("a:\n  b:\n    c: e\n");
	}

	@Test
	public void propertyOverrideInYamlMultipleValues() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("A", "Y");
		map.put("S", 2);
		map.put("Y", 0);
		this.environment.add(new PropertySource("one", map));
		map = new LinkedHashMap<String, Object>();
		map.put("A", "Z");
		map.put("S", 3);
		this.environment.addFirst(new PropertySource("two", map));
		when(this.repository.findOne("foo", "bar", null, false)).thenReturn(this.environment);
		String yaml = this.controller.yaml("foo", "bar", false).getBody();
		assertThat(yaml).isEqualTo("A: Z\nS: 3\nY: 0\n");
	}

	@Test
	public void placeholdersResolvedInYaml() throws Exception {
		whenPlaceholders();
		String yaml = this.controller.yaml("foo", "bar", true).getBody();
		Map<String, Object> map = new Yaml().load(yaml);
		assertThat(map).containsOnlyKeys("a", "foo");
		assertThat(map).containsEntry("foo", "bar");
		Map<String, Object> a = (Map<String, Object>) map.get("a");
		assertThat(a).containsOnlyKeys("b");
		Map<String, Object> b = (Map<String, Object>) a.get("b");
		assertThat(b).containsOnlyKeys("c").containsEntry("c", "bar");
	}

	@Test
	public void placeholdersNotResolvedInYaml() throws Exception {
		whenPlaceholders();
		String yaml = this.controller.yaml("foo", "bar", false).getBody();
		Map<String, Object> map = new Yaml().load(yaml);
		assertThat(map).containsOnlyKeys("a", "foo");
		assertThat(map).containsEntry("foo", "bar");
		Map<String, Object> a = (Map<String, Object>) map.get("a");
		assertThat(a).containsOnlyKeys("b");
		Map<String, Object> b = (Map<String, Object>) a.get("b");
		assertThat(b).containsOnlyKeys("c").containsEntry("c", "${foo}");
	}

	@Test
	public void placeholdersNotResolvedInYamlFromSystemProperties() throws Exception {
		whenPlaceholdersSystemProps();
		String yaml = this.controller.yaml("foo", "bar", true).getBody();
		assertThat(yaml).isEqualTo("a:\n  b:\n    c: ${foo}\n");
	}

	@Test
	public void placeholdersNotResolvedInYamlFromSystemPropertiesWhenNotFlagged() throws Exception {
		whenPlaceholdersSystemProps();
		String yaml = this.controller.yaml("foo", "bar", false).getBody();
		assertThat(yaml).isEqualTo("a:\n  b:\n    c: ${foo}\n");
	}

	@Test
	public void placeholdersNotResolvedInYamlFromSystemPropertiesWhenNotFlaggedWithDefault() throws Exception {
		whenPlaceholdersSystemPropsWithDefault();
		String yaml = this.controller.yaml("foo", "bar", false).getBody();
		// If there is a default value we prevent the placeholder being resolved
		assertThat(yaml).isEqualTo("a:\n  b:\n    c: ${foo:spam}\n");
	}

	@Test
	public void placeholdersResolvedInYamlFromSystemPropertiesWhenFlagged() throws Exception {
		whenPlaceholdersSystemPropsWithDefault();
		String yaml = this.controller.yaml("foo", "bar", true).getBody();
		// If there is a default value we do not prevent the placeholder being resolved
		assertThat(yaml).isEqualTo("a:\n  b:\n    c: spam\n");
	}

	@Test
	public void arrayInYaml() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("a.b[0]", "c");
		map.put("a.b[1]", "d");
		this.environment.add(new PropertySource("one", map));
		when(this.repository.findOne("foo", "bar", null, false)).thenReturn(this.environment);
		String yaml = this.controller.yaml("foo", "bar", false).getBody();
		assertThat(yaml).isEqualTo("a:\n  b:\n  - c\n  - d\n");
	}

	@Test
	public void yamlWithBrackets() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("a.test", "e");
		map.put("a.b[hello]", "c");
		map.put("a.b[world]", "d");
		map.put("a.b[world]d", "f");
		this.environment.add(new PropertySource("one", map));
		when(this.repository.findOne("foo", "bar", null, false)).thenReturn(this.environment);
		String yaml = this.controller.yaml("foo", "bar", false).getBody();
		assertThat(yaml).isEqualTo("a:\n  test: e\n  b[hello]: c\n  b[world]: d\n  b[world]d: f\n");
	}

	@Test
	public void arrayOverridenInEnvironment() throws Exception {
		// Add original values first source
		Map<String, Object> oneMap = new LinkedHashMap<String, Object>();
		oneMap.put("a.b[0]", "c");
		oneMap.put("a.b[1]", "d");
		oneMap.put("a.b[2]", "z");
		this.environment.add(new PropertySource("one", oneMap));

		// Add overridden values in second source
		Map<String, Object> twoMap = new LinkedHashMap<String, Object>();
		twoMap.put("a.b[0]", "f");
		twoMap.put("a.b[1]", "h");
		this.environment.addFirst(new PropertySource("two", twoMap));

		when(this.repository.findOne("foo", "bar", "two", false)).thenReturn(this.environment);
		Environment environment = this.controller.labelled("foo", "bar", "two");
		assertThat(environment).isNotNull();
		assertThat(environment.getName()).isEqualTo("foo");
		assertThat(environment.getProfiles()).isEqualTo(new String[] { "master" });
		assertThat(environment.getLabel()).isEqualTo("master");
		assertThat(environment.getVersion()).isNull();
		assertThat(environment.getPropertySources()).hasSize(3);
		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("two");
		assertThat(environment.getPropertySources().get(0).getSource().entrySet()).hasSize(2);
		assertThat(environment.getPropertySources().get(2).getName()).isEqualTo("one");
		assertThat(environment.getPropertySources().get(2).getSource().entrySet()).hasSize(3);
	}

	@Test
	public void testNameWithSlash() {
		when(this.repository.findOne("foo/spam", "bar", "two", false)).thenReturn(this.environment);

		Environment returnedEnvironment = this.controller.labelled("foo(_)spam", "bar", "two");

		assertThat(returnedEnvironment.getLabel()).isEqualTo(this.environment.getLabel());
		assertThat(returnedEnvironment.getName()).isEqualTo(this.environment.getName());

	}

	@Test
	public void testEnvironmentNotFound() {
		this.controller.setAcceptEmpty(false);
		assertThatThrownBy(() -> this.controller.labelled("foo", "bar", null))
				.isInstanceOf(EnvironmentNotFoundException.class);
	}

	@Test
	public void testwithValidEnvironment() {
		when(this.repository.findOne("foo", "bar", null, false)).thenReturn(this.environment);
		Environment environment = this.controller.labelled("foo", "bar", null);
		assertThat(environment).isNotNull();

	}

	@Test
	public void testLabelWithSlash() {

		when(this.repository.findOne("foo", "bar", "two/spam", false)).thenReturn(this.environment);

		Environment returnedEnvironment = this.controller.labelled("foo", "bar", "two(_)spam");

		assertThat(returnedEnvironment.getLabel()).isEqualTo(this.environment.getLabel());
		assertThat(returnedEnvironment.getName()).isEqualTo(this.environment.getName());
	}

	@Test
	public void arrayOverridenInYaml() throws Exception {
		// Add original values first source
		Map<String, Object> oneMap = new LinkedHashMap<String, Object>();
		oneMap.put("a.b[0]", "c");
		oneMap.put("a.b[1]", "d");
		oneMap.put("a.b[2]", "z");
		this.environment.add(new PropertySource("one", oneMap));

		// Add overridden values in second source
		Map<String, Object> twoMap = new LinkedHashMap<String, Object>();
		twoMap.put("a.b[0]", "f");
		twoMap.put("a.b[1]", "h");
		this.environment.addFirst(new PropertySource("two", twoMap));

		when(this.repository.findOne("foo", "bar", null, false)).thenReturn(this.environment);
		String yaml = this.controller.yaml("foo", "bar", false).getBody();

		// Result will not contain original, extra values from oneMap
		assertThat(yaml).isEqualTo("a:\n  b:\n  - f\n  - h\n");
	}

	@Test
	public void textAtTopLevelInYaml() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("document", "blah");
		this.environment.add(new PropertySource("one", map));
		when(this.repository.findOne("foo", "bar", null, false)).thenReturn(this.environment);
		String yaml = this.controller.yaml("foo", "bar", false).getBody();
		assertThat(yaml).isEqualTo("blah\n");
	}

	@Test
	public void arrayAtTopLevelInYaml() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("document[0]", "c");
		map.put("document[1]", "d");
		this.environment.add(new PropertySource("one", map));
		when(this.repository.findOne("foo", "bar", null, false)).thenReturn(this.environment);
		String yaml = this.controller.yaml("foo", "bar", false).getBody();
		assertThat(yaml).isEqualTo("- c\n- d\n");
	}

	@Test
	public void arrayObObjectAtTopLevelInYaml() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("document[0].a", "c");
		map.put("document[1].a", "d");
		this.environment.add(new PropertySource("one", map));
		when(this.repository.findOne("foo", "bar", null, false)).thenReturn(this.environment);
		String yaml = this.controller.yaml("foo", "bar", false).getBody();
		assertThat(yaml).isEqualTo("- a: c\n- a: d\n");
	}

	@Test
	public void yamlWithProperties() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("org.springframework", "WARN");
		map.put("org.springframework.cloud", "ERROR");
		this.environment.add(new PropertySource("abo", map));
		when(this.repository.findOne("ay", "äzöq", null, false)).thenReturn(this.environment);
		System.out.println("this.controller = " + this.controller);
		String yaml = this.controller.yaml("ay", "äzöq", false).getBody();
		assertThat(yaml).isEqualTo("org:\n  springframework: WARN\n  springframework.cloud: ERROR\n");
	}

	@Test
	public void arrayOfObjectInYaml() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("a.b[0].c", "d");
		map.put("a.b[0].d", "e");
		map.put("a.b[1].c", "d");
		this.environment.add(new PropertySource("one", map));
		when(this.repository.findOne("foo", "bar", null, false)).thenReturn(this.environment);
		String yaml = this.controller.yaml("foo", "bar", false).getBody();
		assertThat("a:\n  b:\n  - d: e\n    c: d\n  - c: d\n".equals(yaml)
				|| "a:\n  b:\n  - c: d\n    d: e\n  - c: d\n".equals(yaml)).as("Wrong output: " + yaml).isTrue();
	}

	@Test
	public void nestedArraysOfObjectInYaml() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("a.b[0].c", "x");
		map.put("a.b[2].e[0].d", "z");
		map.put("a.b[0].d[2]", "yy");
		map.put("a.b[0].d[0]", "xx");
		map.put("a.b[2].c", "y");
		map.put("a.b[3][0]", "r");
		map.put("a.b[3][1]", "s");
		this.environment.add(new PropertySource("one", map));
		when(this.repository.findOne("foo", "bar", null, false)).thenReturn(this.environment);
		String yaml = this.controller.yaml("foo", "bar", false).getBody();

		Map<String, Object> level1 = new Yaml().load(yaml);
		assertThat(level1).containsOnlyKeys("a");

		Map<String, Object> level2 = (Map<String, Object>) level1.get("a");
		assertThat(level2).containsOnlyKeys("b");

		List<Object> level3 = (List<Object>) level2.get("b");
		assertThat(level3).hasSize(4);
		assertThat(level3.get(1)).isNull();

		Map<String, Object> item0 = (Map<String, Object>) level3.get(0);
		assertThat(item0).containsOnlyKeys("c", "d");
		assertThat(item0).containsEntry("c", "x");
		List<Object> itemd = (List<Object>) item0.get("d");
		assertThat(itemd).containsExactly("xx", null, "yy");

		Map<String, Object> item2 = (Map<String, Object>) level3.get(2);
		assertThat(item2).containsOnlyKeys("c", "e");
		assertThat(item2).containsEntry("c", "y");
		List<Object> iteme = (List<Object>) item2.get("e");
		assertThat(iteme).hasSize(1);
		Map<String, Object> item_e0 = (Map<String, Object>) iteme.get(0);
		assertThat(item_e0).containsExactly(entry("d", "z"));

		List<Object> item3 = (List<Object>) level3.get(3);
		assertThat(item3).containsExactly("r", "s");
	}

	@Test
	public void nestedArraysOfObjectInJson() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("a.b[0].c", "x");
		map.put("a.b[0].d[0]", "xx");
		map.put("a.b[0].d[1]", "yy");
		map.put("a.b[1].c", "y");
		map.put("a.b[1].e[0].d", "z");
		this.environment.add(new PropertySource("one", map));
		when(this.repository.findOne("foo", "bar", null, false)).thenReturn(this.environment);
		String json = this.controller.jsonProperties("foo", "bar", false).getBody();
		assertThat(json).as("Wrong output: " + json)
				.isEqualTo("{\"a\":{\"b\":[{\"c\":\"x\",\"d\":[\"xx\",\"yy\"]},{\"c\":\"y\",\"e\":[{\"d\":\"z\"}]}]}}");
	}

	@Test
	public void arrayOfObjectAtTopLevelInYaml() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("b[0].c", "d");
		map.put("b[1].c", "d");
		this.environment.add(new PropertySource("one", map));
		when(this.repository.findOne("foo", "bar", null, false)).thenReturn(this.environment);
		String yaml = this.controller.yaml("foo", "bar", false).getBody();
		assertThat(yaml).isEqualTo("b:\n- c: d\n- c: d\n");
	}

	@Test
	public void arrayOfObjectNestedLevelInYaml() throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("x.a.b[0].c", "d");
		map.put("x.a.b[1].c", "d");
		this.environment.add(new PropertySource("one", map));
		when(this.repository.findOne("foo", "bar", null, false)).thenReturn(this.environment);
		String yaml = this.controller.yaml("foo", "bar", false).getBody();
		assertThat(yaml).isEqualTo("x:\n  a:\n    b:\n    - c: d\n    - c: d\n");
	}

	@Test
	public void placeholdersResolvedInProperties() throws Exception {
		whenPlaceholders();
		String text = this.controller.properties("foo", "bar", true).getBody();
		Properties properties = new Properties();
		properties.load(new StringReader(text));
		assertThat(properties).containsOnly(entry("a.b.c", "bar"), entry("foo", "bar"));
	}

	@Test
	public void placeholdersNotResolvedInProperties() throws Exception {
		whenPlaceholders();
		String text = this.controller.properties("foo", "bar", false).getBody();
		Properties properties = new Properties();
		properties.load(new StringReader(text));
		assertThat(properties).containsOnly(entry("a.b.c", "${foo}"), entry("foo", "bar"));
	}

	@Test
	public void placeholdersNotResolvedInPropertiesFromSystemProperties() throws Exception {
		whenPlaceholdersSystemProps();
		String text = this.controller.properties("foo", "bar", true).getBody();
		assertThat(text).isEqualTo("a.b.c: ${foo}");
	}

	@Test
	public void placeholdersNotResolvedInPropertiesFromSystemPropertiesWhenNotFlagged() throws Exception {
		whenPlaceholdersSystemProps();
		String text = this.controller.properties("foo", "bar", false).getBody();
		assertThat(text).isEqualTo("a.b.c: ${foo}");
	}

	@Test
	public void placeholdersNotResolvedInPropertiesFromSystemPropertiesWhenNotFlaggedWithDefault() throws Exception {
		whenPlaceholdersSystemPropsWithDefault();
		String text = this.controller.properties("foo", "bar", false).getBody();
		assertThat(text).isEqualTo("a.b.c: ${foo:spam}");
	}

	@Test
	public void placeholdersResolvedInJson() throws Exception {
		whenPlaceholders();
		String json = this.controller.jsonProperties("foo", "bar", true).getBody();
		JSONAssert.assertEquals("{\"a\":{\"b\":{\"c\":\"bar\"}},\"foo\":\"bar\"}", json, JSONCompareMode.STRICT);
	}

	@Test
	public void placeholdersNotResolvedInJson() throws Exception {
		whenPlaceholders();
		String json = this.controller.jsonProperties("foo", "bar", false).getBody();
		JSONAssert.assertEquals("{\"a\":{\"b\":{\"c\":\"${foo}\"}},\"foo\":\"bar\"}", json, JSONCompareMode.STRICT);
	}

	@Test
	public void placeholdersNotResolvedInJsonFromSystemProperties() throws Exception {
		whenPlaceholdersSystemProps();
		String json = this.controller.jsonProperties("foo", "bar", true).getBody();
		assertThat(json).isEqualTo("{\"a\":{\"b\":{\"c\":\"${foo}\"}}}");
	}

	@Test
	public void placeholdersNotResolvedInJsonFromSystemPropertiesWhenNotFlagged() throws Exception {
		whenPlaceholdersSystemProps();
		String json = this.controller.jsonProperties("foo", "bar", false).getBody();
		assertThat(json).isEqualTo("{\"a\":{\"b\":{\"c\":\"${foo}\"}}}");
	}

	@Test
	public void placeholdersNotResolvedInJsonFromSystemPropertiesWhenNotFlaggedWithDefault() throws Exception {
		whenPlaceholdersSystemPropsWithDefault();
		String json = this.controller.jsonProperties("foo", "bar", false).getBody();
		// If there is a default value we prevent the placeholder being resolved
		assertThat(json).isEqualTo("{\"a\":{\"b\":{\"c\":\"${foo:spam}\"}}}");
	}

	@Test
	public void placeholdersResolvedInJsonFromSystemPropertiesWhenFlagged() throws Exception {
		whenPlaceholdersSystemPropsWithDefault();
		String json = this.controller.jsonProperties("foo", "bar", true).getBody();
		// If there is a default value we do not prevent the placeholder being resolved
		assertThat(json).isEqualTo("{\"a\":{\"b\":{\"c\":\"spam\"}}}");
	}

	private void whenPlaceholders() {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("foo", "bar");
		this.environment.add(new PropertySource("one", map));
		this.environment.addFirst(new PropertySource("two", Collections.singletonMap("a.b.c", "${foo}")));
		when(this.repository.findOne("foo", "bar", null, false)).thenReturn(this.environment);
	}

	private void whenPlaceholdersSystemProps() {
		System.setProperty("foo", "bar");
		this.environment.addFirst(new PropertySource("two", Collections.singletonMap("a.b.c", "${foo}")));
		when(this.repository.findOne("foo", "bar", null, false)).thenReturn(this.environment);
	}

	private void whenPlaceholdersSystemPropsWithDefault() {
		System.setProperty("foo", "bar");
		this.environment.addFirst(new PropertySource("two", Collections.singletonMap("a.b.c", "${foo:spam}")));
		when(this.repository.findOne("foo", "bar", null, false)).thenReturn(this.environment);
	}

	@Test
	public void labelWithPreviousDirectory() {
		assertThatThrownBy(() -> this.controller.labelled("foo", "bar", "..(_).."))
				.isInstanceOf(InvalidEnvironmentRequestException.class);
	}

	@Test
	public void labelWithPreviousDirectoryEncodedParenthesis() {
		assertThatThrownBy(() -> this.controller.labelled("foo", "bar", "..%28_%29.."))
				.isInstanceOf(InvalidEnvironmentRequestException.class);
	}

	@Test
	public void labelWithPreviousDirectoryAllEncoded() {
		assertThatThrownBy(() -> this.controller.labelled("foo", "bar", "%2E%2E%28%5F%29%2E%2E"))
				.isInstanceOf(InvalidEnvironmentRequestException.class);
	}

	@Test
	public void nameWithPound() {
		assertThatThrownBy(() -> this.controller.labelled("foo#", "bar", "mylabel"))
				.isInstanceOf(InvalidEnvironmentRequestException.class);
	}

	@Test
	public void nameWithPoundEncoded() {
		assertThatThrownBy(() -> this.controller.labelled("foo%23", "bar", "mylabel"))
				.isInstanceOf(InvalidEnvironmentRequestException.class);
	}

	abstract class MockMvcTestCases {

		protected MockMvc mvc;

		@Test
		public void mappingForEnvironment() throws Exception {
			when(EnvironmentControllerTests.this.repository.findOne("foo", "bar", null, false))
					.thenReturn(EnvironmentControllerTests.this.environment);
			this.mvc.perform(MockMvcRequestBuilders.get("/foo/bar")).andExpect(MockMvcResultMatchers.status().isOk());
		}

		@Test
		public void mappingForLabelledEnvironment() throws Exception {
			when(EnvironmentControllerTests.this.repository.findOne("foo", "bar", "other", false))
					.thenReturn(EnvironmentControllerTests.this.environment);
			this.mvc.perform(MockMvcRequestBuilders.get("/foo/bar/other"))
					.andExpect(MockMvcResultMatchers.status().isOk());
		}

		@Test
		public void environmentMissing() throws Exception {
			when(EnvironmentControllerTests.this.repository.findOne("foo1", "notfound", null, false))
					.thenThrow(new EnvironmentNotFoundException("Missing Environment"));
			this.mvc.perform(MockMvcRequestBuilders.get("/foo1/notfound"))
					.andExpect(MockMvcResultMatchers.status().isNotFound());
		}

		@Test
		public void mappingForYaml() throws Exception {
			when(EnvironmentControllerTests.this.repository.findOne("foo", "bar", null, false))
					.thenReturn(EnvironmentControllerTests.this.environment);
			this.mvc.perform(MockMvcRequestBuilders.get("/foo-bar.yml"))
					.andExpect(MockMvcResultMatchers.content().contentType(MediaType.TEXT_PLAIN))
					.andExpect(MockMvcResultMatchers.content().string("{}\n"));
		}

		@Test
		public void mappingForJson() throws Exception {
			when(EnvironmentControllerTests.this.repository.findOne("foo", "bar", null, false))
					.thenReturn(EnvironmentControllerTests.this.environment);
			this.mvc.perform(MockMvcRequestBuilders.get("/foo-bar.json"))
					.andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
					.andExpect(MockMvcResultMatchers.content().string("{}"));
		}

		@Test
		public void mappingForLabelledYaml() throws Exception {
			when(EnvironmentControllerTests.this.repository.findOne("foo", "bar", "other", false))
					.thenReturn(EnvironmentControllerTests.this.environment);
			this.mvc.perform(MockMvcRequestBuilders.get("/other/foo-bar.yml"))
					.andExpect(MockMvcResultMatchers.content().contentType(MediaType.TEXT_PLAIN));
		}

		@Test
		public void mappingForLabelledProperties() throws Exception {
			when(EnvironmentControllerTests.this.repository.findOne("foo", "bar", "other", false))
					.thenReturn(EnvironmentControllerTests.this.environment);
			this.mvc.perform(MockMvcRequestBuilders.get("/other/foo-bar.properties"))
					.andExpect(MockMvcResultMatchers.content().contentType(MediaType.TEXT_PLAIN));
		}

		@Test
		public void mappingForProperties() throws Exception {
			when(EnvironmentControllerTests.this.repository.findOne("foo", "bar", null, false))
					.thenReturn(EnvironmentControllerTests.this.environment);
			this.mvc.perform(MockMvcRequestBuilders.get("/foo-bar.properties"))
					.andExpect(MockMvcResultMatchers.content().contentType(MediaType.TEXT_PLAIN));
		}

		@Test
		public void mappingForLabelledYamlWithHyphen() throws Exception {
			when(EnvironmentControllerTests.this.repository.findOne("foo-bar-foo2-bar2", "spam", "other", false))
					.thenReturn(EnvironmentControllerTests.this.environment);
			this.mvc.perform(MockMvcRequestBuilders.get("/other/foo-bar-foo2-bar2-spam.yml"))
					.andExpect(MockMvcResultMatchers.content().contentType(MediaType.TEXT_PLAIN));
		}

		@Test
		public void mappingforLabelledJsonProperties() throws Exception {
			when(EnvironmentControllerTests.this.repository.findOne("foo", "bar", "other", false))
					.thenReturn(EnvironmentControllerTests.this.environment);
			this.mvc.perform(MockMvcRequestBuilders.get("/other/foo-bar.json"))
					.andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON));
		}

		@Test
		public void mappingforJsonProperties() throws Exception {
			when(EnvironmentControllerTests.this.repository.findOne("foo", "bar", null, false))
					.thenReturn(EnvironmentControllerTests.this.environment);
			this.mvc.perform(MockMvcRequestBuilders.get("/foo-bar.json"))
					.andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON));
		}

		@Test
		public void mappingForLabelledJsonPropertiesWithHyphen() throws Exception {
			when(EnvironmentControllerTests.this.repository.findOne("foo-bar-foo2-bar2", "spam", "other", false))
					.thenReturn(EnvironmentControllerTests.this.environment);
			this.mvc.perform(MockMvcRequestBuilders.get("/other/foo-bar-foo2-bar2-spam.json"))
					.andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON));

		}

		@Test
		public void handleEnvironmentException() throws Exception {
			when(EnvironmentControllerTests.this.repository.findOne(eq("exception"), eq("bad_syntax.ext"), any(),
					eq(false)))
							.thenThrow(new FailedToConstructEnvironmentException("Cannot construct",
									new RuntimeException("underlier")));
			MvcResult result = this.mvc.perform(MockMvcRequestBuilders.get("/exception/bad_syntax.ext"))
					.andExpect(MockMvcResultMatchers.status().is(500)).andReturn();
			assertThat(result.getResponse().getErrorMessage()).isEqualTo("Cannot construct");
		}

	}

	@Nested
	class PathPatternParserTests extends MockMvcTestCases {

		@BeforeEach
		public void initMockMvc() {
			StandaloneMockMvcBuilder builder = MockMvcBuilders
					.standaloneSetup(EnvironmentControllerTests.this.controller);
			builder.setPatternParser(new PathPatternParser());
			this.mvc = builder.build();
		}

	}

	@Nested
	class AntPathMatcherTests extends MockMvcTestCases {

		@BeforeEach
		public void initMockMvc() {
			this.mvc = MockMvcBuilders.standaloneSetup(EnvironmentControllerTests.this.controller).build();
		}

	}

}
