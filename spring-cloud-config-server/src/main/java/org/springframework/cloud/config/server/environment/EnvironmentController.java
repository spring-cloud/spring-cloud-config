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

import static org.springframework.cloud.config.server.support.EnvironmentPropertySource.prepareEnvironment;
import static org.springframework.cloud.config.server.support.EnvironmentPropertySource.resolvePlaceholders;

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

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Dave Syer
 * @author Spencer Gibb
 * @author Roy Clarkson
 * @author Bartosz Wojtkiewicz
 * @author Rafal Zukowski
 * @author Ivan Corrales Solera
 * @author Daniel Frey
 * @author Ryan Lynch
 *
 */
@RestController
@RequestMapping(method = RequestMethod.GET, path = "${spring.cloud.config.server.prefix:}")
public class EnvironmentController {
	
	private EnvironmentRepository repository;
	private ObjectMapper objectMapper;

	private boolean stripDocument = true;

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
	 *
	 * @param stripDocument the flag to set
	 */
	public void setStripDocumentFromYaml(boolean stripDocument) {
		this.stripDocument = stripDocument;
	}

	@RequestMapping("/{name}/{profiles:.*[^-].*}")
	public Environment defaultLabel(@PathVariable String name,
			@PathVariable String profiles) {
		return labelled(name, profiles, null);
	}

	@RequestMapping("/{name}/{profiles}/{label:.*}")
	public Environment labelled(@PathVariable String name, @PathVariable String profiles,
			@PathVariable String label) {
		if (label != null && label.contains("(_)")) {
			// "(_)" is uncommon in a git branch name, but "/" cannot be matched
			// by Spring MVC
			label = label.replace("(_)", "/");
		}
		Environment environment = this.repository.findOne(name, profiles, label);
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
	 * Converts the environment properties into a Map
	 * @param input The environment from which the properties will be converted to a map
	 * @return A map of the properties
	 */
	private Map<String, Object> convertToMap(Environment input) {
		Map<String, Object> target = new LinkedHashMap<>();
		Map<String, Object> data = convertToProperties(input);
		for(String key: data.keySet()) {
			Object value = data.get(key);
			recursiveKeyValueToMap(target, key, value);
		}
		return target;
	}

	private void recursiveKeyValueToMap(Map<String, Object> parent, String key, Object value) {
		//will hold the root of the key if nested and/or part of an array
		//for example foo.bar->rootKey=foo  foo[1].bar->rootKey=foo
		String rootKey;

		//determine if we have a nested key and assign rootKey variable, will nested will cause recursion to happen
		int periodIndex = key.indexOf('.');
		if(periodIndex > 0) {
			rootKey = key.substring(0, periodIndex);
		}else{
			rootKey = key;
		}

		//if rootKey key is an array then remove array annotation from  rootKey value
		// and also determine index position
		// if there is an array then arrayIndexPosition will be 0 or greater
		// also note that for it to be an array it must be proper
		// in that it ends with an [INTERGER] and the number must be a 0 or greater
		int arrayIndexPosition = -1;
		int beginBracketIndex = rootKey.indexOf('[');
		if (beginBracketIndex > 0) {
			int endBracketIndex = rootKey.indexOf(']');
			if (endBracketIndex > 0) {
				//get position
				String positionStr = rootKey.substring(beginBracketIndex + 1, endBracketIndex);
				try {
					int tempPosition = Integer.parseInt(positionStr);
					if (tempPosition >= 0) {
						//we have a live one, treat as an array
						arrayIndexPosition = tempPosition;
						//assign the proper rootKey
						rootKey = rootKey.substring(0,beginBracketIndex);
					}
				} catch (NumberFormatException nfe) {
					//do nothing, don't treat this as an array. Should we error out?
				}
			}
		}

		//get the existing value from the parent if it exists
		Object existingRootFromParent = parent.get(rootKey);

		//now work on this
		if(periodIndex > 0) { //if key contains a period after element 0, then we will need to act recursive
			Map<String, Object> newParent = null; //this will hold the new parent we will pass back into the recursive call
			if (arrayIndexPosition > -1) {
				//we have an array item so lets to that logic
				//get the rootKey item, without the array part if it exists.
				if (existingRootFromParent != null && existingRootFromParent instanceof ArrayList) {
					//we have an existing new parent and it is an array
					//just add (or replace) the new item in the array
					@SuppressWarnings("unchecked")
					ArrayList<Map<String, Object>> listItem = (ArrayList<Map<String, Object>>) existingRootFromParent;
					listItem.ensureCapacity(arrayIndexPosition);
					try {
						newParent = listItem.get(arrayIndexPosition);
					}catch(IndexOutOfBoundsException ioobe) {
						//do nothing
					}
					if (newParent == null) {
						newParent = new TreeMap<>();
						listItem.add(arrayIndexPosition, newParent);
					}
					parent.put(rootKey, listItem);
				}
				if (newParent == null) {
					//an existing new parent was not found or it isn't an array, create new or replace
					newParent = new TreeMap<>();
					@SuppressWarnings("unchecked")
					ArrayList<Map<String, Object>> listItem = new ArrayList<>(arrayIndexPosition > 10 ? arrayIndexPosition : 10);
					listItem.add(arrayIndexPosition, newParent);
					parent.put(rootKey, listItem);
				}

			} else if (existingRootFromParent != null && existingRootFromParent instanceof TreeMap) {
				//this is not an array and existing value is a hashmap so just use it.
				@SuppressWarnings("unchecked")
				Map<String, Object> newParentTemp = (Map<String, Object>) existingRootFromParent;
				newParent = newParentTemp; //just to avoid compiler warnings
				parent.put(rootKey, newParent);
			} else {
				//no existing value so create a new one
				newParent = new TreeMap<>();
				parent.put(rootKey, newParent);
			}

			//okay, prep work done, lets get recursive!!!
			//first get new key which is the part of the key after the first period
			String newKey = key.substring(periodIndex + 1);
			recursiveKeyValueToMap(newParent, newKey, value);

		}else {
			//we have reached the top of the recursion calls since the key no longer (or never had) a period
			if(arrayIndexPosition >= 0) {
				//we have an array
				//first make sure if the parent item exists, is it an array already?
				ArrayList<Object> listItem;
				if(existingRootFromParent != null && existingRootFromParent instanceof ArrayList) {
					@SuppressWarnings("unchecked")
					ArrayList<Object> listItemTemp = (ArrayList<Object>)existingRootFromParent;
					listItem = listItemTemp; //just to avoid compiler warnings
					listItem.ensureCapacity(arrayIndexPosition);
				}else{
					//existing item either doesn't exist, create a new array (possibily overwriting prior value)
					listItem = new ArrayList<>(arrayIndexPosition > 10 ? arrayIndexPosition : 10);
				}
				listItem.add(arrayIndexPosition, value);
				parent.put(rootKey, listItem);
			}else{
				//no array, just put the value attached to the key.
				parent.put(rootKey, value);
			}
		}
	}

	@ExceptionHandler(NoSuchLabelException.class)
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
						LinkedHashMap<String, Object> map = new LinkedHashMap<>();
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
					current.put(name, new ArrayList<>());
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

}
