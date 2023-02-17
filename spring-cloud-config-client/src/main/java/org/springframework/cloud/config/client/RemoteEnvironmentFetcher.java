/*
 * Copyright 2013-2023 the original author or authors.
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.config.client.ConfigServerConfigDataLoader.CONFIG_CLIENT_PROPERTYSOURCE_NAME;
import static org.springframework.core.env.AbstractEnvironment.ACTIVE_PROFILES_PROPERTY_NAME;

/**
 * @author Ryan Baxter
 */
class RemoteEnvironmentFetcher {

	private Log logger = LogFactory.getLog(RemoteEnvironmentFetcher.class);

	RemoteEnvironmentFetcher(Log logger) {
		this.logger = logger;
	}

	RemoteEnvironmentFetcher() {
	}

	public void fetch(Supplier<Environment> remoteEnvironmentSupplier, ConfigClientProperties properties,
			Consumer<org.springframework.core.env.PropertySource> propertySourceAdder) {
		Environment result = remoteEnvironmentSupplier.get();
		HashMap<String, Object> configClientMap = new HashMap<>();
		if (result != null) {
			log(result);

			// result.getPropertySources() can be null if using xml
			if (result.getPropertySources() != null) {
				for (org.springframework.cloud.config.environment.PropertySource source : result.getPropertySources()) {
					@SuppressWarnings("unchecked")
					Map<String, Object> map = translateOrigins(source.getName(),
							(Map<String, Object>) source.getSource());
					// if different profile is activated within the default
					// profile
					boolean relocate = checkIfProfileIsActivatedInDefault(result.getProfiles(), map);
					if (relocate) {
						OriginTrackedValue newProfiles = (OriginTrackedValue) map.get(ACTIVE_PROFILES_PROPERTY_NAME);
						properties.setProfile(newProfiles.getValue().toString());
						propertySourceAdder
								.accept(new OriginTrackedMapPropertySource("configserver:" + source.getName(), map));
						fetch(remoteEnvironmentSupplier, properties, propertySourceAdder); // relocate
																							// again

					}
					else {
						propertySourceAdder
								.accept(new OriginTrackedMapPropertySource("configserver:" + source.getName(), map));

						if (StringUtils.hasText(result.getState())) {
							putValue(configClientMap, "config.client.state", result.getState());
						}
						if (StringUtils.hasText(result.getVersion())) {
							putValue(configClientMap, "config.client.version", result.getVersion());
						}
						propertySourceAdder
								.accept(new MapPropertySource(CONFIG_CLIENT_PROPERTYSOURCE_NAME, configClientMap));

					}

				}
				if (result.getPropertySources().isEmpty()) {
					propertySourceAdder
							.accept(new MapPropertySource(CONFIG_CLIENT_PROPERTYSOURCE_NAME, configClientMap));
				}
			}

		}
	}

	private void putValue(HashMap<String, Object> map, String key, String value) {
		if (StringUtils.hasText(value)) {
			map.put(key, value);
		}
	}

	private void log(Environment result) {
		if (logger.isInfoEnabled()) {
			logger.info(String.format("Located environment: name=%s, profiles=%s, label=%s, version=%s, state=%s",
					result.getName(), result.getProfiles() == null ? "" : Arrays.asList(result.getProfiles()),
					result.getLabel(), result.getVersion(), result.getState()));
		}
		if (logger.isDebugEnabled()) {
			List<PropertySource> propertySourceList = result.getPropertySources();
			if (propertySourceList != null) {
				int propertyCount = 0;
				for (PropertySource propertySource : propertySourceList) {
					propertyCount += propertySource.getSource().size();
				}
				logger.debug(String.format("Environment %s has %d property sources with %d properties.",
						result.getName(), result.getPropertySources().size(), propertyCount));
			}

		}
	}

	private Map<String, Object> translateOrigins(String name, Map<String, Object> source) {
		Map<String, Object> withOrigins = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : source.entrySet()) {
			boolean hasOrigin = false;

			if (entry.getValue() instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> value = (Map<String, Object>) entry.getValue();
				if (value.size() == 2 && value.containsKey("origin") && value.containsKey("value")) {
					Origin origin = new ConfigServicePropertySourceLocator.ConfigServiceOrigin(name,
							value.get("origin"));
					OriginTrackedValue trackedValue = OriginTrackedValue.of(value.get("value"), origin);
					withOrigins.put(entry.getKey(), trackedValue);
					hasOrigin = true;
				}
			}

			if (!hasOrigin) {
				withOrigins.put(entry.getKey(), entry.getValue());
			}
		}
		return withOrigins;
	}

	private boolean checkIfProfileIsActivatedInDefault(String[] profiles, Map<String, Object> map) {
		List<String> profilesList = Arrays.asList(profiles);
		return (profilesList.size() == 1 && profilesList.get(0).equalsIgnoreCase("default")
				&& map.containsKey(ACTIVE_PROFILES_PROPERTY_NAME)
				&& !(((OriginTrackedValue) map.get(ACTIVE_PROFILES_PROPERTY_NAME))).getValue().toString()
						.equalsIgnoreCase("default"));
	}

}
