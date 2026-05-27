/*
 * Copyright 2018-present the original author or authors.
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

package org.springframework.cloud.config.server.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.config.server.support.EnvironmentPropertySource.prepareEnvironment;
import static org.springframework.cloud.config.server.support.EnvironmentPropertySource.resolveMapPlaceholders;
import static org.springframework.cloud.config.server.support.EnvironmentPropertySource.resolvePlaceholders;

public class EnvironmentPropertySourceTest {

	private final StandardEnvironment env = new StandardEnvironment();

	@Test
	public void testEscapedPlaceholdersRemoved() {
		assertThat(resolvePlaceholders(this.env, "\\${abc}")).isEqualTo("${abc}");
		// JSON generated from jackson will be double escaped
		assertThat(resolvePlaceholders(this.env, "\\\\${abc}")).isEqualTo("${abc}");
	}

	@Test
	public void singleValuePlaceholderResolved() {
		Environment environment = new Environment("test", "default");
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("foo", "bar");
		map.put("ref", "${foo}");
		environment.add(new PropertySource("one", map));
		StandardEnvironment prepared = prepareEnvironment(environment);

		Map<String, Object> input = new LinkedHashMap<>();
		input.put("foo", "bar");
		input.put("ref", "${foo}");

		Map<String, Object> resolved = resolveMapPlaceholders(prepared, input);

		assertThat(resolved.get("ref")).isEqualTo("bar");
	}

	@Test
	public void multilineValuePlaceholderResolved() {
		Environment environment = new Environment("test", "default");
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("greeting", "hello\nworld\n");
		map.put("ref", "${greeting}");
		environment.add(new PropertySource("one", map));
		StandardEnvironment prepared = prepareEnvironment(environment);

		Map<String, Object> input = new LinkedHashMap<>();
		input.put("greeting", "hello\nworld\n");
		input.put("ref", "${greeting}");

		Map<String, Object> resolved = resolveMapPlaceholders(prepared, input);

		assertThat(resolved.get("ref")).isEqualTo("hello\nworld\n");
	}

	@Test
	public void nestedMapPlaceholderResolved() {
		Environment environment = new Environment("test", "default");
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("greeting", "hello\nworld\n");
		map.put("nested.ref", "${greeting}");
		environment.add(new PropertySource("one", map));
		StandardEnvironment prepared = prepareEnvironment(environment);

		Map<String, Object> nested = new LinkedHashMap<>();
		nested.put("ref", "${greeting}");
		Map<String, Object> input = new LinkedHashMap<>();
		input.put("greeting", "hello\nworld\n");
		input.put("nested", nested);

		Map<String, Object> resolved = resolveMapPlaceholders(prepared, input);

		Map<String, Object> resolvedNested = (Map<String, Object>) resolved.get("nested");
		assertThat(resolvedNested.get("ref")).isEqualTo("hello\nworld\n");
	}

	@Test
	public void listPlaceholderResolved() {
		Environment environment = new Environment("test", "default");
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("greeting", "hello\nworld\n");
		map.put("items[0]", "${greeting}");
		environment.add(new PropertySource("one", map));
		StandardEnvironment prepared = prepareEnvironment(environment);

		Map<String, Object> input = new LinkedHashMap<>();
		input.put("greeting", "hello\nworld\n");
		input.put("items", List.of("${greeting}"));

		Map<String, Object> resolved = resolveMapPlaceholders(prepared, input);

		List<Object> items = (List<Object>) resolved.get("items");
		assertThat(items.get(0)).isEqualTo("hello\nworld\n");
	}

	@Test
	public void escapedPlaceholderPreservedInMap() {
		Environment environment = new Environment("test", "default");
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("literal", "\\${not.a.ref}");
		environment.add(new PropertySource("one", map));
		StandardEnvironment prepared = prepareEnvironment(environment);

		Map<String, Object> input = new LinkedHashMap<>();
		input.put("literal", "\\${not.a.ref}");

		Map<String, Object> resolved = resolveMapPlaceholders(prepared, input);

		assertThat(resolved.get("literal")).isEqualTo("${not.a.ref}");
	}

	@Test
	public void endToEndMultilineYaml() {
		// Source YAML has a multiline value and a reference to it
		String sourceYaml = "greeting: |\n  hello\n  world\nref: ${greeting}\n";
		Map<String, Object> parsed = new Yaml().load(sourceYaml);

		Environment environment = new Environment("test", "default");
		environment.add(new PropertySource("one", parsed));
		StandardEnvironment prepared = prepareEnvironment(environment);

		Map<String, Object> resolved = resolveMapPlaceholders(prepared, parsed);
		String yaml = new Yaml().dumpAsMap(resolved);

		assertThat(yaml).contains("ref: |\n  hello\n  world");
	}

	@Test
	public void endToEndMultilinePlaceholderSpanningLines() {
		// Source YAML has placeholder spanning multiple lines (SnakeYAML folds to spaces)
		String sourceYaml = "greeting: |\n  hello\n  world\nref: ${\n       greeting\n     }\n";
		@SuppressWarnings("unchecked")
		Map<String, Object> parsed = new Yaml().load(sourceYaml);

		Environment environment = new Environment("test", "default");
		environment.add(new PropertySource("one", parsed));
		StandardEnvironment prepared = prepareEnvironment(environment);

		Map<String, Object> resolved = resolveMapPlaceholders(prepared, parsed);
		String yaml = new Yaml().dumpAsMap(resolved);

		assertThat(yaml).contains("ref: |\n  hello\n  world");
	}

	@Test
	public void endToEndSequenceWithMultilineValue() {
		String sourceYaml = "greeting: |\n  hello\n  world\nitems:\n- ${greeting}\n- ref: ${greeting}\n";
		Map<String, Object> parsed = new Yaml().load(sourceYaml);

		Environment environment = new Environment("test", "default");
		environment.add(new PropertySource("one", parsed));
		StandardEnvironment prepared = prepareEnvironment(environment);

		Map<String, Object> resolved = resolveMapPlaceholders(prepared, parsed);
		String yaml = new Yaml().dumpAsMap(resolved);

		// Verify YAML is valid and values are resolved
		Map<String, Object> reparsed = new Yaml().load(yaml);
		java.util.List<Object> items = (java.util.List<Object>) reparsed.get("items");
		assertThat(items.get(0)).isEqualTo("hello\nworld\n");
		Map<String, Object> secondItem = (Map<String, Object>) items.get(1);
		assertThat(secondItem.get("ref")).isEqualTo("hello\nworld\n");
	}

	@Test
	public void defaultValueUsedWhenKeyMissing() {
		Environment environment = new Environment("test", "default");
		java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
		map.put("ref", "${MISSING:fallback value}");
		environment.add(new PropertySource("one", map));
		StandardEnvironment prepared = prepareEnvironment(environment);

		Map<String, Object> input = new java.util.LinkedHashMap<>();
		input.put("ref", "${MISSING:fallback value}");

		Map<String, Object> resolved = resolveMapPlaceholders(prepared, input);

		assertThat(resolved.get("ref")).isEqualTo("fallback value");
	}

	@Test
	public void defaultValueOverriddenWhenKeyExists() {
		Environment environment = new Environment("test", "default");
		java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
		map.put("greeting", "hello");
		map.put("ref", "${greeting:fallback}");
		environment.add(new PropertySource("one", map));
		StandardEnvironment prepared = prepareEnvironment(environment);

		Map<String, Object> input = new java.util.LinkedHashMap<>();
		input.put("greeting", "hello");
		input.put("ref", "${greeting:fallback}");

		Map<String, Object> resolved = resolveMapPlaceholders(prepared, input);

		assertThat(resolved.get("ref")).isEqualTo("hello");
	}

	@Test
	public void unresolvablePlaceholderLeftIntact() {
		Environment environment = new Environment("test", "default");
		java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
		map.put("ref", "${MISSING}");
		environment.add(new PropertySource("one", map));
		StandardEnvironment prepared = prepareEnvironment(environment);

		Map<String, Object> input = new java.util.LinkedHashMap<>();
		input.put("ref", "${MISSING}");

		Map<String, Object> resolved = resolveMapPlaceholders(prepared, input);

		assertThat(resolved.get("ref")).isEqualTo("${MISSING}");
	}

}
