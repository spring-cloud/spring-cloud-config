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

package org.springframework.cloud.config.client;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.actuate.endpoint.AbstractEndpoint;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.context.scope.refresh.RefreshScope;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.context.support.StandardServletEnvironment;

/**
 * @author Dave Syer
 *
 */
@ConfigurationProperties(prefix = "endpoints.refresh", ignoreUnknownFields = false)
@ManagedResource
public class RefreshEndpoint extends AbstractEndpoint<Collection<String>> {

	private Set<String> standardSources = new HashSet<String>(Arrays.asList(
			StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
			StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
			StandardServletEnvironment.JNDI_PROPERTY_SOURCE_NAME,
			StandardServletEnvironment.SERVLET_CONFIG_PROPERTY_SOURCE_NAME,
			StandardServletEnvironment.SERVLET_CONTEXT_PROPERTY_SOURCE_NAME));

	private ConfigurableApplicationContext context;

	private RefreshScope scope;

	public RefreshEndpoint(ConfigurableApplicationContext context, RefreshScope scope) {
		super("refresh");
		this.context = context;
		this.scope = scope;
	}

	@ManagedOperation
	public synchronized String[] refresh() {
		Map<String, Object> before = extract(context.getEnvironment().getPropertySources());
		addConfigFilesToEnvironment();
		Set<String> keys = changes(before,
				extract(context.getEnvironment().getPropertySources())).keySet();
		scope.refreshAll();
		if (keys.isEmpty()) {
			return new String[0];
		}
		context.publishEvent(new EnvironmentChangeEvent(keys));
		return keys.toArray(new String[keys.size()]);
	}

	private void addConfigFilesToEnvironment() {
		ConfigurableApplicationContext capture = new SpringApplicationBuilder(Empty.class).showBanner(
				false).web(false).environment(context.getEnvironment()).run();
		MutablePropertySources target = context.getEnvironment().getPropertySources();
		for (PropertySource<?> source : capture.getEnvironment().getPropertySources()) {
			String name = source.getName();
			if (!standardSources.contains(name)) {
				if (target.contains(name)) {
					target.replace(name, source);
				} else {
					if (target.contains("defaultProperties")) {
						target.addBefore("defaultProperties", source);
					} else {
						target.addLast(source);
					}
				}
			}
		}
	}

	@Override
	public Collection<String> invoke() {
		return Arrays.asList(refresh());
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
		for (PropertySource<?> parent : propertySources) {
			if (!standardSources.contains(parent.getName())) {
				extract(parent, result);
			}
		}
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

	@Configuration
	protected static class Empty {

	}

}
