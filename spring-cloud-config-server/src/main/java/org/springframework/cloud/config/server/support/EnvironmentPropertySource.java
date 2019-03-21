/*
 * Copyright 2018-2019 the original author or authors.
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

import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * @author Spencer Gibb
 */
public class EnvironmentPropertySource extends PropertySource<Environment> {

	// "\${" (from text) or "\\${" from JSON to signal escaped placeholder
	private static final Pattern ESCAPED_PLACEHOLDERS = Pattern
			.compile("[\\\\]{1,2}\\$\\{");

	public EnvironmentPropertySource(Environment sources) {
		super("cloudEnvironment", sources);
	}

	public static StandardEnvironment prepareEnvironment(Environment environment) {
		StandardEnvironment standardEnvironment = new StandardEnvironment();
		standardEnvironment.getPropertySources()
				.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
		standardEnvironment.getPropertySources()
				.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
		standardEnvironment.getPropertySources()
				.addFirst(new EnvironmentPropertySource(environment));
		return standardEnvironment;
	}

	public static String resolvePlaceholders(StandardEnvironment preparedEnvironment,
			String text) {
		// Mask out escaped placeholders
		text = ESCAPED_PLACEHOLDERS.matcher(text).replaceAll("\\$_{");
		return preparedEnvironment.resolvePlaceholders(text).replace("$_{", "${");
	}

	@Override
	public Object getProperty(String name) {
		for (org.springframework.cloud.config.environment.PropertySource source : getSource()
				.getPropertySources()) {
			Map<?, ?> map = source.getSource();
			if (map.containsKey(name)) {
				return map.get(name);
			}
		}
		return null;
	}

}
