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

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.micrometer.observation.ObservationRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.config.ConfigDataEnvironmentUpdateListener;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataResource;
import org.springframework.boot.context.config.StandardConfigDataResource;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.StringUtils;

/**
 * Simple implementation of {@link EnvironmentRepository} that uses a SpringApplication
 * and configuration files located through the normal protocols. The resulting Environment
 * is composed of property sources located using the application name as the config file
 * stem (spring.config.name) and the environment name as a Spring profile.
 *
 * @author Dave Syer
 * @author Roy Clarkson
 * @author Venil Noronha
 * @author Daniel Lavoie
 */
public class NativeEnvironmentRepository implements EnvironmentRepository, SearchPathLocator, Ordered {

	private static final String[] DEFAULT_LOCATIONS = new String[] { "optional:classpath:/",
			"optional:classpath:/config/", "optional:file:./", "optional:file:./config/" };

	static final Pattern RESOURCE_PATTERN = Pattern.compile("Config resource '(.*?)' via location '(.*)'");

	private static Log logger = LogFactory.getLog(NativeEnvironmentRepository.class);

	private String defaultLabel;

	/**
	 * Locations to search for configuration files. Defaults to the same as a Spring Boot
	 * app so [classpath:/,classpath:/config/,file:./,file:./config/].
	 */
	private String[] searchLocations;

	/**
	 * Flag to determine how to handle exceptions during decryption (default false).
	 */
	private boolean failOnError;

	/**
	 * Flag to determine whether label locations should be added.
	 */
	private boolean addLabelLocations;

	/**
	 * Version string to be reported for native repository.
	 */
	private String version;

	private ConfigurableEnvironment environment;

	private int order;

	private final ObservationRegistry observationRegistry;

	public NativeEnvironmentRepository(ConfigurableEnvironment environment, NativeEnvironmentProperties properties,
			ObservationRegistry observationRegistry) {
		this.environment = environment;
		this.addLabelLocations = properties.getAddLabelLocations();
		this.defaultLabel = properties.getDefaultLabel();
		this.failOnError = properties.getFailOnError();
		this.order = properties.getOrder();
		this.observationRegistry = observationRegistry;
		setSearchLocations(properties.getSearchLocations());
		this.version = properties.getVersion();
	}

	public boolean isFailOnError() {
		return this.failOnError;
	}

	public void setFailOnError(boolean failOnError) {
		this.failOnError = failOnError;
	}

	public boolean isAddLabelLocations() {
		return this.addLabelLocations;
	}

	public void setAddLabelLocations(boolean addLabelLocations) {
		this.addLabelLocations = addLabelLocations;
	}

	public String getDefaultLabel() {
		return this.defaultLabel;
	}

	public void setDefaultLabel(String defaultLabel) {
		this.defaultLabel = defaultLabel;
	}

	@Override
	public Environment findOne(String config, String profile, String label) {
		return findOne(config, profile, label, false);
	}

	@Override
	public Environment findOne(String config, String profile, String label, boolean includeOrigin) {

		try {
			ConfigurableEnvironment environment = getEnvironment(config, profile, label);
			DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
			Map<org.springframework.core.env.PropertySource<?>, PropertySourceConfigData> propertySourceToConfigData = new HashMap<>();
			ConfigDataEnvironmentPostProcessor.applyTo(environment, resourceLoader, null,
					StringUtils.commaDelimitedListToSet(profile), new ConfigDataEnvironmentUpdateListener() {
						@Override
						public void onPropertySourceAdded(org.springframework.core.env.PropertySource<?> propertySource,
								ConfigDataLocation location, ConfigDataResource resource) {
							propertySourceToConfigData.put(propertySource,
									new PropertySourceConfigData(location, resource));
						}
					});

			environment.getPropertySources().remove("config-data-setup");
			return clean(ObservationEnvironmentRepositoryWrapper
					.wrap(this.observationRegistry, new PassthruEnvironmentRepository(environment))
					.findOne(config, profile, label, includeOrigin), propertySourceToConfigData);
		}
		catch (Exception e) {
			String msg = String.format("Could not construct context for config=%s profile=%s label=%s includeOrigin=%b",
					config, profile, label, includeOrigin);
			String completeMessage = NestedExceptionUtils.buildMessage(msg,
					NestedExceptionUtils.getMostSpecificCause(e));
			throw new FailedToConstructEnvironmentException(completeMessage, e);
		}
	}

	@Override
	public Locations getLocations(String application, String profile, String label) {
		String[] locations = this.searchLocations;
		if (this.searchLocations == null || this.searchLocations.length == 0) {
			locations = DEFAULT_LOCATIONS;
		}
		Collection<String> output = new LinkedHashSet<String>();

		if (label == null) {
			label = this.defaultLabel;
		}
		for (String location : locations) {
			String[] profiles = new String[] { profile };
			if (profile != null) {
				profiles = StringUtils.commaDelimitedListToStringArray(profile);
			}
			String[] apps = new String[] { application };
			if (application != null) {
				apps = StringUtils.commaDelimitedListToStringArray(application);
			}
			for (String prof : profiles) {
				for (String app : apps) {
					String value = location;
					if (application != null) {
						value = value.replace("{application}", app);
					}
					if (prof != null) {
						value = value.replace("{profile}", prof);
					}
					if (label != null) {
						value = value.replace("{label}", label);
					}
					if (!value.endsWith("/")) {
						value = value + "/";
					}
					if (isDirectory(value)) {
						output.add(value);
					}
				}
			}
		}
		if (this.addLabelLocations) {
			for (String location : locations) {
				if (StringUtils.hasText(label)) {
					String labelled = location + label.trim() + "/";
					if (isDirectory(labelled)) {
						output.add(labelled);
					}
				}
			}
		}
		return new Locations(application, profile, label, this.version, output.toArray(new String[0]));
	}

	private ConfigurableEnvironment getEnvironment(String application, String profile, String label) {
		ConfigurableEnvironment environment = new StandardEnvironment();
		Map<String, Object> map = new HashMap<>();
		map.put("spring.profiles.active", profile);
		String config = application;
		if (!config.startsWith("application")) {
			config = "application," + config;
		}
		map.put("spring.config.name", config);
		// map.put("encrypt.failOnError=" + this.failOnError);
		map.put("spring.config.location",
				StringUtils.arrayToDelimitedString(getLocations(application, profile, label).getLocations(), ";"));
		// globally ignore config files that are not found
		map.put("spring.config.on-not-found", "IGNORE");
		environment.getPropertySources().addFirst(new MapPropertySource("config-data-setup", map));
		return environment;
	}

	protected Environment clean(Environment env) {
		return clean(env, Collections.emptyMap());
	}

	protected Environment clean(Environment env,
			Map<org.springframework.core.env.PropertySource<?>, PropertySourceConfigData> propertySourceToConfigData) {
		Environment result = new Environment(env.getName(), env.getProfiles(), env.getLabel(), this.version,
				env.getState());
		for (PropertySource source : env.getPropertySources()) {
			String originalName = source.getName();
			String name = originalName;
			if (this.environment.getPropertySources().contains(name)) {
				continue;
			}
			String[] locations = null;

			PropertySourceConfigData configData = propertySourceToConfigData.get(source.getOriginalPropertySource());
			// try and get information directly from ConfigData
			if (configData != null && configData.resource instanceof StandardConfigDataResource) {
				StandardConfigDataResource configDataResource = (StandardConfigDataResource) configData.resource;
				// use StandardConfigDataResource as that format is expected still
				name = configDataResource.toString();
				locations = configDataLocations(configData.location.split());
			}
			else {
				// if not, try and parse
				Matcher matcher = RESOURCE_PATTERN.matcher(name);
				if (matcher.find()) {
					name = matcher.group(1);
					locations = new String[] { matcher.group(2) };
				}
			}
			name = name.replace("applicationConfig: [", "");
			name = name.replace("file [", "file:");
			name = name.replace("class path resource [", "classpath:/");
			if (name.indexOf('[') < 0) {
				// only remove if there isn't a matching left bracket
				name = name.replace("]", "");
			}
			if (this.searchLocations != null) {
				boolean matches = matchesLocation(locations, name, result);
				if (!matches) {
					// Don't include this one: it wasn't matched by our search locations
					if (logger.isDebugEnabled()) {
						logger.debug("Not adding property source: " + originalName);
					}
					continue;
				}
			}
			logger.info("Adding property source: " + originalName);
			if (originalName.contains("document #")) {
				// this is a multi-document file, use originalName for uniqueness.
				result.add(new PropertySource(originalName, source.getSource()));
			}
			else {
				// many other file tests rely on the mangled name
				result.add(new PropertySource(name, source.getSource()));
			}
		}
		return result;
	}

	private String[] configDataLocations(ConfigDataLocation[] locations) {
		String[] stringLocations = new String[locations.length];
		for (int i = 0; i < locations.length; i++) {
			stringLocations[i] = locations[i].toString();
		}
		return stringLocations;
	}

	private boolean matchesLocation(String[] locations, String name, Environment result) {
		boolean matches = false;
		String normal = name;
		if (normal.startsWith("file:")) {
			normal = StringUtils.cleanPath(new File(normal.substring("file:".length())).getAbsolutePath());
		}
		String profile = result.getProfiles() == null ? null
				: StringUtils.arrayToCommaDelimitedString(result.getProfiles());
		for (String pattern : getLocations(result.getName(), profile, result.getLabel()).getLocations()) {
			if (!pattern.contains(":")) {
				pattern = "file:" + pattern;
			}
			if (pattern.startsWith("optional:")) {
				pattern = pattern.substring("optional:".length());
			}
			if (pattern.startsWith("file:")) {
				pattern = StringUtils.cleanPath(new File(pattern.substring("file:".length())).getAbsolutePath()) + "/";
			}
			final String finalPattern = pattern;
			if (logger.isTraceEnabled()) {
				logger.trace("Testing pattern: " + finalPattern + " with property source: " + name);
			}
			if (normal.startsWith(finalPattern)) {
				matches = true;
				break;
			}
			if (locations != null) {
				matches = Arrays.stream(locations).map(this::cleanFileLocation)
						.anyMatch(location -> location.startsWith(finalPattern));
				if (matches) {
					break;
				}
			}
		}
		return matches;
	}

	private String cleanFileLocation(String location) {
		if (location.startsWith("file:")) {
			return StringUtils.cleanPath(new File(location.substring("file:".length())).getAbsolutePath()) + "/";
		}
		return location;
	}

	public String[] getSearchLocations() {
		return this.searchLocations;
	}

	public void setSearchLocations(String... locations) {
		this.searchLocations = locations;
		if (locations != null) {
			for (int i = 0; i < locations.length; i++) {
				String location = locations[i];
				if (isDirectory(location) && !location.endsWith("/")) {
					location = location + "/";
				}
				locations[i] = location;
			}
		}
	}

	public String getVersion() {
		return this.version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	private boolean isDirectory(String location) {
		return !location.contains("{") && !location.endsWith(".properties") && !location.endsWith(".yml")
				&& !location.endsWith(".yaml");
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	private final class PropertySourceConfigData {

		private final ConfigDataLocation location;

		private final ConfigDataResource resource;

		private PropertySourceConfigData(ConfigDataLocation location, ConfigDataResource resource) {
			this.location = location;
			this.resource = resource;
		}

	}

}
