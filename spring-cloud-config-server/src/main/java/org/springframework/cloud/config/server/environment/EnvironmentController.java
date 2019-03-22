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

package org.springframework.cloud.config.server.environment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.cloud.config.server.support.EnvironmentPropertySource.prepareEnvironment;
import static org.springframework.cloud.config.server.support.EnvironmentPropertySource.resolvePlaceholders;

/**
 * @author Dave Syer
 * @author Spencer Gibb
 * @author Roy Clarkson
 * @author Bartosz Wojtkiewicz
 * @author Rafal Zukowski
 * @author Ivan Corrales Solera
 * @author Daniel Frey
 * @author Ian Bondoc
 *
 */
@RestController
@RequestMapping(method = RequestMethod.GET, path = "${spring.cloud.config.server.prefix:}")
public class EnvironmentController {

	private EnvironmentRepository repository;

	private ObjectMapper objectMapper;

	private boolean stripDocument = true;

	private boolean acceptEmpty = true;

	public EnvironmentController(EnvironmentRepository repository) {
		this(repository, new ObjectMapper());
	}

	public EnvironmentController(EnvironmentRepository repository,
			ObjectMapper objectMapper) {
		this.repository = repository;
		this.objectMapper = objectMapper;
	}

	/**
	 * Flag to indicate that YAML documents which are not a map should be stripped of the
	 * "document" prefix that is added by Spring (to facilitate conversion to Properties).
	 * @param stripDocument the flag to set
	 */
	public void setStripDocumentFromYaml(boolean stripDocument) {
		this.stripDocument = stripDocument;
	}

	/**
	 * Flag to indicate that If HTTP 404 needs to be sent if Application is not Found.
	 * @param acceptEmpty the flag to set
	 */
	public void setAcceptEmpty(boolean acceptEmpty) {
		this.acceptEmpty = acceptEmpty;
	}

	@RequestMapping("/{name}/{profiles:.*[^-].*}")
	public Environment defaultLabel(@PathVariable String name,
			@PathVariable String profiles) {
		return labelled(name, profiles, null);
	}

	@RequestMapping("/{name}/{profiles}/{label:.*}")
	public Environment labelled(@PathVariable String name, @PathVariable String profiles,
			@PathVariable String label) {
		if (name != null && name.contains("(_)")) {
			// "(_)" is uncommon in a git repo name, but "/" cannot be matched
			// by Spring MVC
			name = name.replace("(_)", "/");
		}
		if (label != null && label.contains("(_)")) {
			// "(_)" is uncommon in a git branch name, but "/" cannot be matched
			// by Spring MVC
			label = label.replace("(_)", "/");
		}
		Environment environment = this.repository.findOne(name, profiles, label);
		if (!this.acceptEmpty
				&& (environment == null || environment.getPropertySources().isEmpty())) {
			throw new EnvironmentNotFoundException("Profile Not found");
		}
		return environment;
	}

	@RequestMapping("/{name}-{profiles}.properties")
	public ResponseEntity<String> properties(@PathVariable String name,
			@PathVariable String profiles,
			@RequestParam(defaultValue = "true") boolean resolvePlaceholders)
			throws IOException {
		return labelledProperties(name, profiles, null, resolvePlaceholders);
	}

	@RequestMapping("/{label}/{name}-{profiles}.properties")
	public ResponseEntity<String> labelledProperties(@PathVariable String name,
			@PathVariable String profiles, @PathVariable String label,
			@RequestParam(defaultValue = "true") boolean resolvePlaceholders)
			throws IOException {
		validateProfiles(profiles);
		Environment environment = labelled(name, profiles, label);
		Map<String, Object> properties = convertToProperties(environment);
		String propertiesString = getPropertiesString(properties);
		if (resolvePlaceholders) {
			propertiesString = resolvePlaceholders(prepareEnvironment(environment),
					propertiesString);
		}
		return getSuccess(propertiesString);
	}

	@RequestMapping("{name}-{profiles}.json")
	public ResponseEntity<String> jsonProperties(@PathVariable String name,
			@PathVariable String profiles,
			@RequestParam(defaultValue = "true") boolean resolvePlaceholders)
			throws Exception {
		return labelledJsonProperties(name, profiles, null, resolvePlaceholders);
	}

	@RequestMapping("/{label}/{name}-{profiles}.json")
	public ResponseEntity<String> labelledJsonProperties(@PathVariable String name,
			@PathVariable String profiles, @PathVariable String label,
			@RequestParam(defaultValue = "true") boolean resolvePlaceholders)
			throws Exception {
		validateProfiles(profiles);
		Environment environment = labelled(name, profiles, label);
		Map<String, Object> properties = convertToMap(environment);
		String json = this.objectMapper.writeValueAsString(properties);
		if (resolvePlaceholders) {
			json = resolvePlaceholders(prepareEnvironment(environment), json);
		}
		return getSuccess(json, MediaType.APPLICATION_JSON);
	}

	private String getPropertiesString(Map<String, Object> properties) {
		StringBuilder output = new StringBuilder();
		for (Entry<String, Object> entry : properties.entrySet()) {
			if (output.length() > 0) {
				output.append("\n");
			}
			String line = entry.getKey() + ": " + entry.getValue();
			output.append(line);
		}
		return output.toString();
	}

	@RequestMapping({ "/{name}-{profiles}.yml", "/{name}-{profiles}.yaml" })
	public ResponseEntity<String> yaml(@PathVariable String name,
			@PathVariable String profiles,
			@RequestParam(defaultValue = "true") boolean resolvePlaceholders)
			throws Exception {
		return labelledYaml(name, profiles, null, resolvePlaceholders);
	}

	@RequestMapping({ "/{label}/{name}-{profiles}.yml",
			"/{label}/{name}-{profiles}.yaml" })
	public ResponseEntity<String> labelledYaml(@PathVariable String name,
			@PathVariable String profiles, @PathVariable String label,
			@RequestParam(defaultValue = "true") boolean resolvePlaceholders)
			throws Exception {
		validateProfiles(profiles);
		Environment environment = labelled(name, profiles, label);
		Map<String, Object> result = convertToMap(environment);
		if (this.stripDocument && result.size() == 1
				&& result.keySet().iterator().next().equals("document")) {
			Object value = result.get("document");
			if (value instanceof Collection) {
				return getSuccess(new Yaml().dumpAs(value, Tag.SEQ, FlowStyle.BLOCK));
			}
			else {
				return getSuccess(new Yaml().dumpAs(value, Tag.STR, FlowStyle.BLOCK));
			}
		}
		String yaml = new Yaml().dumpAsMap(result);

		if (resolvePlaceholders) {
			yaml = resolvePlaceholders(prepareEnvironment(environment), yaml);
		}

		return getSuccess(yaml);
	}

	/**
	 * Method {@code convertToMap} converts an {@code Environment} to a nested Map which
	 * represents a yml/json structure.
	 * @param input the environment to be converted
	 * @return the nested map containing the environment's properties
	 */
	private Map<String, Object> convertToMap(Environment input) {
		// First use the current convertToProperties to get a flat Map from the
		// environment
		Map<String, Object> properties = convertToProperties(input);

		// The root map which holds all the first level properties
		Map<String, Object> rootMap = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : properties.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			PropertyNavigator nav = new PropertyNavigator(key);
			nav.setMapValue(rootMap, value);
		}
		return rootMap;
	}

	@ExceptionHandler(RepositoryException.class)
	public void noSuchLabel(HttpServletResponse response) throws IOException {
		response.sendError(HttpStatus.NOT_FOUND.value());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public void illegalArgument(HttpServletResponse response) throws IOException {
		response.sendError(HttpStatus.BAD_REQUEST.value());
	}

	private void validateProfiles(String profiles) {
		if (profiles.contains("-")) {
			throw new IllegalArgumentException(
					"Properties output not supported for name or profiles containing hyphens");
		}
	}

	private HttpHeaders getHttpHeaders(MediaType mediaType) {
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(mediaType);
		return httpHeaders;
	}

	private ResponseEntity<String> getSuccess(String body) {
		return new ResponseEntity<>(body, getHttpHeaders(MediaType.TEXT_PLAIN),
				HttpStatus.OK);
	}

	private ResponseEntity<String> getSuccess(String body, MediaType mediaType) {
		return new ResponseEntity<>(body, getHttpHeaders(mediaType), HttpStatus.OK);
	}

	private Map<String, Object> convertToProperties(Environment profiles) {

		// Map of unique keys containing full map of properties for each unique
		// key
		Map<String, Map<String, Object>> map = new LinkedHashMap<>();
		List<PropertySource> sources = new ArrayList<>(profiles.getPropertySources());
		Collections.reverse(sources);
		Map<String, Object> combinedMap = new TreeMap<>();
		for (PropertySource source : sources) {

			@SuppressWarnings("unchecked")
			Map<String, Object> value = (Map<String, Object>) source.getSource();
			for (String key : value.keySet()) {

				if (!key.contains("[")) {

					// Not an array, add unique key to the map
					combinedMap.put(key, value.get(key));

				}
				else {

					// An existing array might have already been added to the property map
					// of an unequal size to the current array. Replace the array key in
					// the current map.
					key = key.substring(0, key.indexOf("["));
					Map<String, Object> filtered = new TreeMap<>();
					for (String index : value.keySet()) {
						if (index.startsWith(key + "[")) {
							filtered.put(index, value.get(index));
						}
					}
					map.put(key, filtered);
				}
			}

		}

		// Combine all unique keys for array values into the combined map
		for (Entry<String, Map<String, Object>> entry : map.entrySet()) {
			combinedMap.putAll(entry.getValue());
		}

		postProcessProperties(combinedMap);
		return combinedMap;
	}

	private void postProcessProperties(Map<String, Object> propertiesMap) {
		for (Iterator<String> iter = propertiesMap.keySet().iterator(); iter.hasNext();) {
			String key = iter.next();
			if (key.equals("spring.profiles")) {
				iter.remove();
			}
		}
	}

	/**
	 * Class {@code PropertyNavigator} is used to navigate through the property key and
	 * create necessary Maps and Lists making up the nested structure to finally set the
	 * property value at the leaf node.
	 * <p>
	 * The following rules in yml/json are implemented: <pre>
	 * 1. an array element can be:
	 *    - a value (leaf)
	 *    - a map
	 *    - a nested array
	 * 2. a map value can be:
	 *    - a value (leaf)
	 *    - a nested map
	 *    - an array
	 * </pre>
	 */
	private static final class PropertyNavigator {

		private final String propertyKey;

		private int currentPos;

		private NodeType valueType;

		private PropertyNavigator(String propertyKey) {
			this.propertyKey = propertyKey;
			this.currentPos = -1;
			this.valueType = NodeType.MAP;
		}

		private void setMapValue(Map<String, Object> map, Object value) {
			String key = getKey();
			if (NodeType.MAP.equals(this.valueType)) {
				@SuppressWarnings("unchecked")
				Map<String, Object> nestedMap = (Map<String, Object>) map.get(key);
				if (nestedMap == null) {
					nestedMap = new LinkedHashMap<>();
					map.put(key, nestedMap);
				}
				setMapValue(nestedMap, value);
			}
			else if (NodeType.ARRAY.equals(this.valueType)) {
				@SuppressWarnings("unchecked")
				List<Object> list = (List<Object>) map.get(key);
				if (list == null) {
					list = new ArrayList<>();
					map.put(key, list);
				}
				setListValue(list, value);
			}
			else {
				map.put(key, value);
			}
		}

		private void setListValue(List<Object> list, Object value) {
			int index = getIndex();
			// Fill missing elements if needed
			while (list.size() <= index) {
				list.add(null);
			}
			if (NodeType.MAP.equals(this.valueType)) {
				@SuppressWarnings("unchecked")
				Map<String, Object> map = (Map<String, Object>) list.get(index);
				if (map == null) {
					map = new LinkedHashMap<>();
					list.set(index, map);
				}
				setMapValue(map, value);
			}
			else if (NodeType.ARRAY.equals(this.valueType)) {
				@SuppressWarnings("unchecked")
				List<Object> nestedList = (List<Object>) list.get(index);
				if (nestedList == null) {
					nestedList = new ArrayList<>();
					list.set(index, nestedList);
				}
				setListValue(nestedList, value);
			}
			else {
				list.set(index, value);
			}
		}

		private int getIndex() {
			// Consider [
			int start = this.currentPos + 1;

			for (int i = start; i < this.propertyKey.length(); i++) {
				char c = this.propertyKey.charAt(i);
				if (c == ']') {
					this.currentPos = i;
					break;
				}
				else if (!Character.isDigit(c)) {
					throw new IllegalArgumentException(
							"Invalid key: " + this.propertyKey);
				}
			}
			// If no closing ] or if '[]'
			if (this.currentPos < start || this.currentPos == start) {
				throw new IllegalArgumentException("Invalid key: " + this.propertyKey);
			}
			else {
				int index = Integer
						.parseInt(this.propertyKey.substring(start, this.currentPos));
				// Skip the closing ]
				this.currentPos++;
				if (this.currentPos == this.propertyKey.length()) {
					this.valueType = NodeType.LEAF;
				}
				else {
					switch (this.propertyKey.charAt(this.currentPos)) {
					case '.':
						this.valueType = NodeType.MAP;
						break;
					case '[':
						this.valueType = NodeType.ARRAY;
						break;
					default:
						throw new IllegalArgumentException(
								"Invalid key: " + this.propertyKey);
					}
				}
				return index;
			}
		}

		private String getKey() {
			// Consider initial value or previous char '.' or '['
			int start = this.currentPos + 1;
			for (int i = start; i < this.propertyKey.length(); i++) {
				char currentChar = this.propertyKey.charAt(i);
				if (currentChar == '.') {
					this.valueType = NodeType.MAP;
					this.currentPos = i;
					break;
				}
				else if (currentChar == '[') {
					this.valueType = NodeType.ARRAY;
					this.currentPos = i;
					break;
				}
			}
			// If there's no delimiter then it's a key of a leaf
			if (this.currentPos < start) {
				this.currentPos = this.propertyKey.length();
				this.valueType = NodeType.LEAF;
				// Else if we encounter '..' or '.[' or start of the property is . or [
				// then it's invalid
			}
			else if (this.currentPos == start) {
				throw new IllegalArgumentException("Invalid key: " + this.propertyKey);
			}
			return this.propertyKey.substring(start, this.currentPos);
		}

		private enum NodeType {

			LEAF, MAP, ARRAY

		}

	}

}
