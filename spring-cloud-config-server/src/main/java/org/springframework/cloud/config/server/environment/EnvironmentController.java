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
	 * Converts the environment properties into a Map for use in converting them to json and yaml.
	 * For example if we have the following three properties
	 * foo.bar=hello
	 * foo.array[0]=goodbye
	 * blah.boo=world
	 *
	 * baseMap("foo",
	 * 		map("bar", "hello")
	 * 		map("array", ["goodbye"]))
	 * baseMap("blah",
	 * 		map("boo","world"))
	 *
	 * 	A side effect is that if there are conflicting property values then the last item will win. For example,
	 * 	foo.bar=world
	 * 	foo.bar=winner
	 * 	In this scenario foo.bar=winner will be the result.
	 *
	 * @param input The environment from which the properties will be converted to a map
	 * @return A map of the properties
	 */
	private Map<String, Object> convertToMap(Environment input) {
		Map<String, Object> target = new LinkedHashMap<>();
		Map<String, Object> data = convertToProperties(input);
		for(String key: data.keySet()) {
			Object value = data.get(key);
			recursivePropertyToMap(target, key, value);
		}
		return target;
	}

	/**
	 * This method works by recursively calling itself while it traversing up the property name as separated by zero or more periods.
	 * For example if we have a property prop.foo.bar[1].hello=world the recursion would look like this...
	 * recursivePropertyToMap(rootMapOfAllProperties, "prop.foo.bar[0].hello", "world")
	 * recursivePropertyToMap(mapOfPropProperty, "foo.bar[0].hello", "world")
	 * recursivePropertyToMap(mapOfBarProperty, "bar[0].hello", "world")
	 * recursivePropertyToMap(mapOfHelloProperty, "hello", "world")
	 *
	 * The last call for the key item "hello" will actually set the property value of the property. The result
	 * will be the the currMapNode that is first passed in will be populated by
	 * map("prop",
	 * 		map("foo",
	 * 			map("bar",
	 * 				[map("hello", "world")])))
	 *
	 * 	The expectation is that this will be called from #convertToMap using the same target map object
	 * 	thus resulting in a map containing all the property values.
	 *
	 * @param currLeafMapNode The current "leaf" of the Map for the key
	 * @param currKeyName The key name to be worked on.
	 * @param propValue The value that will be assigned to the property once we finish traversing the property name
	 * @see #convertToMap(Environment)
	 */
	private void recursivePropertyToMap(Map<String, Object> currLeafMapNode, String currKeyName, Object propValue) {
		//will hold the root of the key if nested and/or part of an array
		//for example foo.bar->rootKey=foo  foo[1].bar->rootKey=foo
		String currentLeafNodeKey;

		//determine if we have a nested key and assign rootKey variable, will nested will cause recursion to happen
		int periodIndex = currKeyName.indexOf('.');
		if(periodIndex > 0) {
			currentLeafNodeKey = currKeyName.substring(0, periodIndex);
		}else{
			currentLeafNodeKey = currKeyName;
		}

		//See if we have an array and if yes determine its index position
		//must be in the format of propName[NUMBER] where number is 0 or greater
		int arrayIndexPosition = -1;
		int beginBracketIndex = currentLeafNodeKey.indexOf('[');
		if (beginBracketIndex > 0) {
			int endBracketIndex = currentLeafNodeKey.indexOf(']');
			if (endBracketIndex > 0) {
				//get position
				String positionStr = currentLeafNodeKey.substring(beginBracketIndex + 1, endBracketIndex);
				try {
					int tempPosition = Integer.parseInt(positionStr);
					if (tempPosition >= 0) {
						//we have a live one, treat as an array
						arrayIndexPosition = tempPosition;
						//assign the proper rootKey
						currentLeafNodeKey = currentLeafNodeKey.substring(0,beginBracketIndex);
					}
				} catch (NumberFormatException nfe) {
					//do nothing, don't treat this as an array. Should we error out?
				}
			}
		}

		/**
		 * get the existing value from the parent if it exists. We need to treat this as an object because it could be a
		 * one of two different types
		 * 1) An ArrayList - Example: foo.bar[1].hello.world and we are processing the "hello" portion of the property.
		 * 			Here the parent "bar[1]" is an array.
		 * 2) A TreeMap -  Example: foo.bar.hello.world and we are again process the "hello" portion of the property.
		 * 			Here the parent "bar" is not an array so we treat it as a key value Map
		 **/
		Object existingNodeFromParent = currLeafMapNode.get(currentLeafNodeKey);

		if(periodIndex > 0) {
			//we still have more property name nodes to process so we will get recursive
			//first, get the new node parent which will then hold the map of the current leaf node being processed
			Map<String, Object> newNestedMapNode = getNestedMapNode(currLeafMapNode, currentLeafNodeKey, existingNodeFromParent, arrayIndexPosition);

			//first get remaining part of the key which is the part after the first period
			String remainingKey = currKeyName.substring(periodIndex + 1);

			//now lets get recursive and process the next item in the property name
			recursivePropertyToMap(newNestedMapNode, remainingKey, propValue);

		}else {
			//we are at the last node of the property and thus the end of the recursion so
			//go ahead and actually assign the value
			Object propertyValue = getLeafPropertyValue(existingNodeFromParent, propValue, arrayIndexPosition);
			currLeafMapNode.put(currentLeafNodeKey, propertyValue);
		}
	}

	/**
	 * This method is responsible for mapping a nested property. A nested property is any part of the property
     * that is not the last element.  This is different because it will ultimately have a Map returned from
     * which the next item in the property name will be mapped.
	 * @param currLeafMapNode The current leaf map from which we will work from
	 * @param currentNodeKey The remaining key name that hasn't been processed yet
	 * @param existingNodeFromParent The node that possibly already exists from teh currLeafMapNode.  Null if it doesn't exist
	 * @param arrayIndexPosition Index position if this is an array.  -1 if its not an array.
	 * @return The new leaf map node which can be used in the next key level.
	 */
	private Map<String, Object> getNestedMapNode(Map<String, Object> currLeafMapNode, String currentNodeKey, Object existingNodeFromParent, int arrayIndexPosition) {
		Map<String, Object> newNestedMapNode = null;
		if (arrayIndexPosition > -1) {
            //we have an array item so lets to that logic
            //get the rootKey item, without the array part if it exists.

            if (existingNodeFromParent != null && existingNodeFromParent instanceof ArrayList) {
                //we have an existing new parent and it is an array
                //just add (or replace) the new item in the array
                @SuppressWarnings("unchecked")
                ArrayList<Map<String, Object>> listItem = (ArrayList<Map<String, Object>>) existingNodeFromParent;
                listItem.ensureCapacity(arrayIndexPosition + 1);

				//do we already have an item at this position
                try {
                    newNestedMapNode = listItem.get(arrayIndexPosition);
                }catch(IndexOutOfBoundsException ioobe) {
                    //do nothing, there should be a non exception producing version of get
                }
                if (newNestedMapNode == null) {
                	//item at this position doesn't exist, so create it.
                    newNestedMapNode = new TreeMap<>();
                    listItem.add(arrayIndexPosition, newNestedMapNode);
                }
            }
            if (newNestedMapNode == null) {
                //an existing mapNode for the current nodeKey was not found or it isn't an array, create new or replace
                newNestedMapNode = new TreeMap<>();
                @SuppressWarnings("unchecked")
                ArrayList<Map<String, Object>> listItem = new ArrayList<>(arrayIndexPosition > 10 ? arrayIndexPosition : 10);
                listItem.add(arrayIndexPosition, newNestedMapNode);
				currLeafMapNode.put(currentNodeKey, listItem);
            }


        } else if (existingNodeFromParent != null && existingNodeFromParent instanceof TreeMap) {
            //this is not an array and existing value is a hashmap so just use it.
            @SuppressWarnings("unchecked")
            Map<String, Object> newParentTemp = (Map<String, Object>) existingNodeFromParent;
            newNestedMapNode = newParentTemp; //just to avoid compiler warnings
			currLeafMapNode.put(currentNodeKey, newNestedMapNode);
        } else {
            //no existing value so create a new one
            newNestedMapNode = new TreeMap<>();
			currLeafMapNode.put(currentNodeKey, newNestedMapNode);
        }

		return newNestedMapNode;
	}

	/**
	 * Returns the value object to be assigned to the last node in the map of a property. If the last item
	 * is not an array then it will return the propValue itself otherwise it will handle array logic.
	 * The array logic is as follows
	 * 		- If existing root exists and it is an array, great.  Use the existing array and place our new value
	 * 			at the arrayIndexPosition - WARING: possibly overwriting a previous value.
	 * 		- If existing root doesn't exist or is not an array then create a new array and assign value of
	 * 			arrayIndexPosition.  WARNING: If the existing root does exist it will be overwritten.
	 * @param existingRootFromParent Any existing value item that may already exist, null if it doesn't exist
	 * @param propValue The value of the property
	 * @param arrayIndexPosition If the node property value is an array say like, foo.bar[2] then in this example the
	 *                           arrayIndexPosition would be 2.  If it is not an array then the value must be -1 or less.
	 * @see #recursivePropertyToMap(Map, String, Object)
	 */
	private Object getLeafPropertyValue(Object existingRootFromParent, Object propValue, int arrayIndexPosition) {
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
            listItem.add(arrayIndexPosition, propValue);
			return listItem;
        }else{
            //no array, just put the value attached to the key.
			return propValue;
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
