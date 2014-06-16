/*
 * Copyright 2013-2014 the original author or authors.
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

package org.springframework.platform.config.client;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.actuate.endpoint.AbstractEndpoint;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.platform.bootstrap.config.ConfigServiceBootstrapConfiguration;
import org.springframework.platform.context.environment.EnvironmentChangeEvent;
import org.springframework.util.ReflectionUtils;

/**
 * @author Dave Syer
 *
 */
@ConfigurationProperties(prefix = "endpoints.refresh", ignoreUnknownFields = false)
public class RefreshEndpoint extends AbstractEndpoint<Collection<String>> {

	private ConfigurableApplicationContext context;

	private ConfigServiceBootstrapConfiguration bootstrap = new ConfigServiceBootstrapConfiguration();

	public RefreshEndpoint(ConfigurableApplicationContext context, ConfigServiceBootstrapConfiguration bootstrap) {
		super("refresh");
		this.context = context;
		this.bootstrap = bootstrap;
	}

	@Override
	public Collection<String> invoke() {
		Map<String, Object> before = extract(context.getEnvironment().getPropertySources());
		bootstrap.initialize(context);
		Set<String> keys = changes(before,
				extract(context.getEnvironment().getPropertySources())).keySet();
		if (keys.isEmpty()) {
			return keys;
		}
		context.publishEvent(new EnvironmentChangeEvent(keys));
		return keys;
	}

	private Map<String, Object> changes(Map<String, Object> before,
			Map<String, Object> after) {
		Map<String, Object> result = new HashMap<String, Object>();
		for (String key : before.keySet()) {
			if (!after.containsKey(key)) {
				result.put(key, null);
			} else if (!equal(before.get(key), after.get(key))) {
				result.put(key, after.get(key));
			}
		}
		for (String key : after.keySet()) {
			if (!before.containsKey(key)) {
				result.put(key, after.get(key));
			}
		}
		return result;
	}

	private boolean equal(Object one, Object two) {
		if (one == null && two == null) {
			return true;
		}
		if (one == null || two == null) {
			return false;
		}
		return one.equals(two);
	}

	private Map<String, Object> extract(MutablePropertySources propertySources) {
		Map<String, Object> result = new HashMap<String, Object>();
		PropertySource<?> parent = propertySources.get("bootstrap");
		extract(parent, result);
		return result;
	}

	private void extract(PropertySource<?> parent, Map<String, Object> result) {
		if (parent instanceof CompositePropertySource) {
			try {
				Field field = ReflectionUtils.findField(CompositePropertySource.class,
						"propertySources");
				field.setAccessible(true);
				@SuppressWarnings("unchecked")
				Set<PropertySource<?>> sources = (Set<PropertySource<?>>) field.get(parent);
				for (PropertySource<?> source : sources) {
					extract(source, result);
				}
			} catch (Exception e) {
				return;
			}
		} else if (parent instanceof EnumerablePropertySource) {
			for (String key : ((EnumerablePropertySource<?>) parent).getPropertyNames()) {
				result.put(key, parent.getProperty(key));
			}
		}
	}

}
