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
import java.util.stream.Collectors;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health.Builder;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

import static org.springframework.cloud.bootstrap.config.PropertySourceBootstrapConfiguration.BOOTSTRAP_PROPERTY_SOURCE_NAME;
import static org.springframework.cloud.config.client.ConfigServerConfigDataLoader.CONFIG_CLIENT_PROPERTYSOURCE_NAME;
import static org.springframework.cloud.config.client.ConfigServerConfigDataLocationResolver.PREFIX;

/**
 * @author Spencer Gibb
 * @author Marcos Barbero
 */
public class ConfigServerHealthIndicator extends AbstractHealthIndicator {

	private ConfigClientHealthProperties properties;

	private ConfigurableEnvironment environment;

	private long lastAccess = 0;

	private List<PropertySource<?>> cached = new ArrayList<>();

	public ConfigServerHealthIndicator(ConfigurableEnvironment environment, ConfigClientHealthProperties properties) {
		this.environment = environment;
		this.properties = properties;
	}

	@Override
	protected void doHealthCheck(Builder builder) {
		List<PropertySource<?>> propertySources = getPropertySource();
		if (propertySources.isEmpty()) {
			builder.unknown();
			builder.unknown().withDetail("error", "no property sources located");
		}
		else {
			builder.up();
			List<String> sources = new ArrayList<>();
			for (PropertySource<?> propertySource : propertySources) {

				if (propertySource instanceof CompositePropertySource) {
					for (PropertySource<?> ps : ((CompositePropertySource) propertySource).getPropertySources()) {
						sources.add(ps.getName());
					}
				}
				else if (propertySource != null) {
					sources.add(propertySource.getName());
				}
			}
			builder.withDetail("propertySources", sources);
		}
	}

	private List<PropertySource<?>> getPropertySource() {
		long accessTime = System.currentTimeMillis();
		if (isCacheStale(accessTime)) {
			this.lastAccess = accessTime;
			this.cached = this.environment.getPropertySources().stream()
					.filter(p -> p.getName().startsWith(CONFIG_CLIENT_PROPERTYSOURCE_NAME)
							|| p.getName().startsWith(BOOTSTRAP_PROPERTY_SOURCE_NAME + "-")
							|| p.getName().startsWith(PREFIX))
					.collect(Collectors.toList());
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
