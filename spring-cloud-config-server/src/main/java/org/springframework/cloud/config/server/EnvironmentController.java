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
package org.springframework.cloud.config.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.servlet.http.HttpServletResponse;

import org.springframework.boot.bind.PropertiesConfigurationFactory;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.encryption.EnvironmentEncryptor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * @author Dave Syer
 * @author Spencer Gibb
 * @author Roy Clarkson
 * @author Bartosz Wojtkiewicz
 * @author Rafal Zukowski
 *
 */
@RestController
@RequestMapping("${spring.cloud.config.server.prefix:}")
public class EnvironmentController {

	private static final String MAP_PREFIX = "map";

	private EnvironmentRepository repository;

	private EnvironmentEncryptor environmentEncryptor;

	private String defaultLabel;

	private Map<String, String> overrides = new LinkedHashMap<>();

	private boolean stripDocument = true;

	public EnvironmentController(EnvironmentRepository repository,
			EnvironmentEncryptor environmentEncryptor) {
		super();
		this.repository = repository;
		this.defaultLabel = repository.getDefaultLabel();
		this.environmentEncryptor = environmentEncryptor;
	}

	/**
	 * Flag to indicate that YAML documents which are not a map should be stripped of the
	 * "document" prefix that is added by Spring (to facilitate conversion to Properties).
	 * @param stripDocument the flag to set
	 */
	public void setStripDocumentFromYaml(boolean stripDocument) {
		this.stripDocument = stripDocument;
	}

	@RequestMapping("/{name}/{profiles:.*[^-].*}")
	public Environment defaultLabel(@PathVariable String name,
			@PathVariable String profiles) {
		return labelled(name, profiles, defaultLabel);
	}

	@RequestMapping("/{name}/{profiles}/{label:.*}")
	public Environment labelled(@PathVariable String name, @PathVariable String profiles,
			@PathVariable String label) {
		Environment environment = repository.findOne(name, profiles, label);
		if (environmentEncryptor != null) {
			environment = environmentEncryptor.decrypt(environment);
		}
		if (!overrides.isEmpty()) {
			environment.addFirst(new PropertySource("overrides", overrides));
		}
		return environment;
	}

	@RequestMapping("/{name}-{profiles}.properties")
	public ResponseEntity<String> properties(@PathVariable String name,
			@PathVariable String profiles) throws IOException {
		return labelledProperties(name, profiles, defaultLabel);
	}

	@RequestMapping("/{label}/{name}-{profiles}.properties")
	public ResponseEntity<String> labelledProperties(@PathVariable String name,
			@PathVariable String profiles, @PathVariable String label) throws IOException {
		validateNameAndProfiles(name, profiles);
		Map<String, Object> properties = convertToProperties(labelled(name, profiles,
				label));
		return getSuccess(getPropertiesString(properties));
	}

	@RequestMapping("{name}-{profiles}.json")
	public ResponseEntity<Map<String, Object>> jsonProperties(@PathVariable String name,
			@PathVariable String profiles) throws Exception {
		return labelledJsonProperties(name, profiles, defaultLabel);
	}

	@RequestMapping("/{label}/{name}-{profiles}.json")
	public ResponseEntity<Map<String, Object>> labelledJsonProperties(
			@PathVariable String name, @PathVariable String profiles,
			@PathVariable String label) throws Exception {
		validateNameAndProfiles(name, profiles);
		Map<String, Object> properties = convertToMap(labelled(name, profiles, label));
		return getSuccess(properties, MediaType.APPLICATION_JSON);
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
			@PathVariable String profiles) throws Exception {
		return labelledYaml(name, profiles, defaultLabel);
	}

	@RequestMapping({ "/{label}/{name}-{profiles}.yml", "/{label}/{name}-{profiles}.yaml" })
	public ResponseEntity<String> labelledYaml(@PathVariable String name,
			@PathVariable String profiles, @PathVariable String label) throws Exception {
		validateNameAndProfiles(name, profiles);
		Map<String, Object> result = convertToMap(labelled(name, profiles, label));
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
		return getSuccess(new Yaml().dumpAsMap(result));
	}

	private Map<String, Object> convertToMap(Environment input) throws BindException {
		Map<String, Object> target = new LinkedHashMap<String, Object>();
		PropertiesConfigurationFactory<Map<String, Object>> factory = new PropertiesConfigurationFactory<Map<String, Object>>(
				target);
		Map<String, Object> data = convertToProperties(input);
		LinkedHashMap<String, Object> properties = new LinkedHashMap<String, Object>();
		for (String key : data.keySet()) {
			properties.put(MAP_PREFIX + "." + key, data.get(key));
		}
		addArrays(target, properties);
		MutablePropertySources propertySources = new MutablePropertySources();
		propertySources.addFirst(new MapPropertySource("properties", properties));
		factory.setPropertySources(propertySources);
		factory.bindPropertiesToTarget();
		@SuppressWarnings("unchecked")
		Map<String, Object> result = (Map<String, Object>) target.get(MAP_PREFIX);
		return result == null ? new LinkedHashMap<String, Object>() : result;
	}

	@ExceptionHandler(NoSuchLabelException.class)
	public void noSuchLabel(HttpServletResponse response) throws IOException {
		response.sendError(HttpStatus.NOT_FOUND.value());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public void illegalArgument(HttpServletResponse response) throws IOException {
		response.sendError(HttpStatus.BAD_REQUEST.value());
	}

	private void validateNameAndProfiles(String name, String profiles) {
		if (name.contains("-") || profiles.contains("-")) {
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

	private ResponseEntity<Map<String, Object>> getSuccess(Map<String, Object> body,
			MediaType mediaType) {
		return new ResponseEntity<>(body, getHttpHeaders(mediaType), HttpStatus.OK);
	}

	/**
	 * Create Lists of the right size for any YAML arrays that are going to need to be
	 * bound. Some of this might be do-able in RelaxedDataBinder, but we need to do it
	 * here for now. Only supports arrays at leaf level currently (i.e. the properties
	 * keys end in [*]).
	 *
	 * @param target the target Map
	 * @param properties the properties (with key names to check)
	 */
	private void addArrays(Map<String, Object> target, Map<String, Object> properties) {
		for (String key : properties.keySet()) {
			int index = key.indexOf("[");
			Map<String, Object> current = target;
			if (index > 0) {
				String stem = key.substring(0, index);
				String[] keys = StringUtils.delimitedListToStringArray(stem, ".");
				for (int i = 0; i < keys.length - 1; i++) {
					if (current.get(keys[i]) == null) {
						LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();
						current.put(keys[i], map);
						current = map;
					}
					else {
						@SuppressWarnings("unchecked")
						Map<String, Object> map = (Map<String, Object>) current
								.get(keys[i]);
						current = map;
					}
				}
				String name = keys[keys.length - 1];
				if (current.get(name) == null) {
					current.put(name, new ArrayList<Object>());
				}
				@SuppressWarnings("unchecked")
				List<Object> value = (List<Object>) current.get(name);
				int position = Integer
						.valueOf(key.substring(index + 1, key.indexOf("]")));
				while (position >= value.size()) {
					if (key.indexOf("].", index) > 0) {
						value.add(new LinkedHashMap<String, Object>());
					}
					else {
						value.add("");
					}
				}
			}
		}
	}

	private Map<String, Object> convertToProperties(Environment profiles) {
		Map<String, Object> map = new TreeMap<String, Object>();
		List<PropertySource> sources = new ArrayList<PropertySource>(
				profiles.getPropertySources());
		Collections.reverse(sources);
		for (PropertySource source : sources) {
			@SuppressWarnings("unchecked")
			Map<String, String> value = (Map<String, String>) source.getSource();
			map.putAll(value);
		}
		postProcessProperties(map);
		return map;
	}

	private void postProcessProperties(Map<String, Object> propertiesMap) {
		for (String key : propertiesMap.keySet()) {
			if (key.equals("spring.profiles")) {
				propertiesMap.remove(key);
			}
		}
	}

	/**
	 * @param defaultLabel
	 */
	public void setDefaultLabel(String defaultLabel) {
		this.defaultLabel = defaultLabel;
	}

	/**
	 * @param overrides the overrides to set
	 */
	public void setOverrides(Map<String, String> overrides) {
		this.overrides = new HashMap<String, String>(overrides);
		for (String key : overrides.keySet()) {
			if (overrides.get(key).contains("$\\{")) {
				this.overrides.put(key, overrides.get(key).replace("$\\{", "${"));
			}
		}
	}

}
