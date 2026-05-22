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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.PropertyPlaceholderHelper;

/**
 * @author Spencer Gibb
 */
public class EnvironmentPropertySource extends PropertySource<Environment> {

	// "\${" (from text) or "\\${" from JSON to signal escaped placeholder
	private static final Pattern ESCAPED_PLACEHOLDERS = Pattern.compile("[\\\\]{1,2}\\$\\{");

	private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

	public EnvironmentPropertySource(Environment sources) {
		super("cloudEnvironment", sources);
	}

	public static StandardEnvironment prepareEnvironment(Environment environment) {
		StandardEnvironment standardEnvironment = new StandardEnvironment();
		standardEnvironment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
		standardEnvironment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
		standardEnvironment.getPropertySources().addFirst(new EnvironmentPropertySource(environment));
		return standardEnvironment;
	}

	/**
	 * Resolve placeholders in flat text (used for .properties output). Handles escaped
	 * placeholders (\${...}) by masking and restoring them.
	 */
	public static String resolvePlaceholders(StandardEnvironment preparedEnvironment, String text) {
		// Mask out escaped placeholders
		text = ESCAPED_PLACEHOLDERS.matcher(text).replaceAll("\\$_{");
		return preparedEnvironment.resolvePlaceholders(text).replace("$_{", "${");
	}

	/**
	 * Resolve placeholders in a nested Map structure. Walks all values recursively,
	 * resolving any String values that contain ${...} expressions. Returns a new Map with
	 * resolved values — the original is not modified.
	 *
	 * <p>
	 * Use this before serializing to YAML or JSON so that the serializer handles
	 * multiline values and escaping natively.
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> resolveMapPlaceholders(StandardEnvironment env, Map<String, Object> map) {
		Map<String, Object> resolved = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			resolved.put(entry.getKey(), resolveValue(env, entry.getValue()));
		}
		return resolved;
	}

	@SuppressWarnings("unchecked")
	private static Object resolveValue(StandardEnvironment env, Object value) {
		if (value instanceof String s) {
			return resolveStringValue(env, s);
		}
		else if (value instanceof Map) {
			Map<String, Object> mapValue = (Map<String, Object>) value;
			Map<String, Object> resolved = new LinkedHashMap<>();
			for (Map.Entry<String, Object> entry : mapValue.entrySet()) {
				resolved.put(entry.getKey(), resolveValue(env, entry.getValue()));
			}
			return resolved;
		}
		else if (value instanceof List) {
			List<Object> listValue = (List<Object>) value;
			return listValue.stream().map(item -> resolveValue(env, item)).collect(Collectors.toList());
		}
		return value;
	}

	private static String resolveStringValue(StandardEnvironment env, String value) {
		if (!PLACEHOLDER_PATTERN.matcher(value).find()) {
			return value;
		}
		// Mask escaped placeholders
		String masked = ESCAPED_PLACEHOLDERS.matcher(value).replaceAll("\\$_{");
		// Use PropertyPlaceholderHelper directly to trim whitespace from keys
		PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper("${", "}", ":", null, true);
		String resolved = helper.replacePlaceholders(masked, (placeholder) -> env.getProperty(placeholder.strip()));
		return resolved.replace("$_{", "${");
	}

	@Override
	public Object getProperty(String name) {
		for (org.springframework.cloud.config.environment.PropertySource source : getSource().getPropertySources()) {
			Map<?, ?> map = source.getSource();
			if (map.containsKey(name)) {
				return map.get(name);
			}
		}
		return null;
	}

}
