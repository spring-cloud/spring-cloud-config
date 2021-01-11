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

package org.springframework.cloud.config.client;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health.Builder;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

/**
 * @author Spencer Gibb
 * @author Marcos Barbero
 */
public class ConfigServerHealthIndicator extends AbstractHealthIndicator {

	private ConfigClientHealthProperties properties;

	private ConfigurableEnvironment environment;

	private long lastAccess = 0;

	private PropertySource<?> cached;

	public ConfigServerHealthIndicator(ConfigurableEnvironment environment, ConfigClientHealthProperties properties) {
		this.environment = environment;
		this.properties = properties;
	}

	@Override
	protected void doHealthCheck(Builder builder) {
		PropertySource<?> propertySource = getPropertySource();
		builder.up();
		if (propertySource instanceof CompositePropertySource) {
			List<String> sources = new ArrayList<>();
			for (PropertySource<?> ps : ((CompositePropertySource) propertySource).getPropertySources()) {
				sources.add(ps.getName());
			}
			builder.withDetail("propertySources", sources);
		}
		else if (propertySource != null) {
			builder.withDetail("propertySources", propertySource.toString());
		}
		else {
			builder.unknown().withDetail("error", "no property sources located");
		}
	}

	private PropertySource<?> getPropertySource() {
		long accessTime = System.currentTimeMillis();
		if (isCacheStale(accessTime)) {
			this.lastAccess = accessTime;
			MutablePropertySources propertySources = this.environment.getPropertySources();
			this.cached = propertySources.get("configClient");
		}
		return this.cached;
	}

	private boolean isCacheStale(long accessTime) {
		if (this.cached == null) {
			return true;
		}
		return (accessTime - this.lastAccess) >= this.properties.getTimeToLive().toMillis();
	}

}
