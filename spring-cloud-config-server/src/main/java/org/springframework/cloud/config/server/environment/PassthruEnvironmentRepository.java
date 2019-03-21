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

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.StringUtils;
import org.springframework.web.context.support.StandardServletEnvironment;

/**
 * Simple implementation of {@link EnvironmentRepository} that just reflects an existing
 * Spring Environment.
 *
 * @author Dave Syer
 * @author Roy Clarkson
 */
public class PassthruEnvironmentRepository implements EnvironmentRepository {

	private static final String DEFAULT_LABEL = "master";

	private Set<String> standardSources = new HashSet<String>(Arrays.asList("vcap",
			StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
			StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
			StandardServletEnvironment.JNDI_PROPERTY_SOURCE_NAME,
			StandardServletEnvironment.SERVLET_CONFIG_PROPERTY_SOURCE_NAME,
			StandardServletEnvironment.SERVLET_CONTEXT_PROPERTY_SOURCE_NAME));

	private ConfigurableEnvironment environment;

	public PassthruEnvironmentRepository(ConfigurableEnvironment environment) {
		this.environment = environment;
	}

	public String getDefaultLabel() {
		return DEFAULT_LABEL;
	}

	@Override
	public Environment findOne(String application, String env, String label) {
		Environment result = new Environment(application,
				StringUtils.commaDelimitedListToStringArray(env), label, null, null);
		for (org.springframework.core.env.PropertySource<?> source : this.environment
				.getPropertySources()) {
			String name = source.getName();
			if (!this.standardSources.contains(name)
					&& source instanceof MapPropertySource) {
				result.add(new PropertySource(name, getMap(source)));
			}
		}
		return result;

	}

	private Map<?, ?> getMap(org.springframework.core.env.PropertySource<?> source) {
		Map<Object, Object> map = new LinkedHashMap<>();
		Map<?, ?> input = (Map<?, ?>) source.getSource();
		for (Object key : input.keySet()) {
			// Spring Boot wraps the property values in an "origin" detector, so we need
			// to extract the string values
			map.put(key, source.getProperty(key.toString()));
		}
		return map;
	}

}
