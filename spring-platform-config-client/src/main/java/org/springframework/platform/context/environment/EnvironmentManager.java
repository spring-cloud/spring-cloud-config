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
package org.springframework.platform.context.environment;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

/**
 * @author Dave Syer
 *
 */
@Component
@ManagedResource
public class EnvironmentManager implements ApplicationEventPublisherAware {

	private static final String MANAGER_PROPERTY_SOURCE = "manager";
	private Map<String, Object> map = new LinkedHashMap<String, Object>();

	private ConfigurableEnvironment environment;
	private ApplicationEventPublisher publisher;

	public EnvironmentManager(ConfigurableEnvironment environment) {
		this.environment = environment;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@ManagedOperation
	public void reset() {
		if (!map.isEmpty()) {
			Set<String> keys = map.keySet();
			map.clear();
			publish(new EnvironmentChangeEvent(keys));
		}
	}

	@ManagedOperation
	public void setProperty(String name, String value) {

		if (!environment.getPropertySources().contains(MANAGER_PROPERTY_SOURCE)) {
			synchronized (map) {
				if (!environment.getPropertySources().contains(MANAGER_PROPERTY_SOURCE)) {
					MapPropertySource source = new MapPropertySource(
							MANAGER_PROPERTY_SOURCE, map);
					environment.getPropertySources().addFirst(source);
				}
			}
		}

		if (!value.equals(environment.getProperty(name))) {
			map.put(name, value);
			publish(new EnvironmentChangeEvent(Collections.singleton(name)));
		}

	}

	@ManagedOperation
	public Object getProperty(String name) {
		return environment.getProperty(name);
	}

	private void publish(EnvironmentChangeEvent environmentChangeEvent) {
		if (publisher !=null ) {
			publisher.publishEvent(environmentChangeEvent);
		}
	}

}
