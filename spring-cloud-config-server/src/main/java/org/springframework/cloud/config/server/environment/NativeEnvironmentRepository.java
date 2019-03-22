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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.Banner.Mode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.config.ConfigFileApplicationListener;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
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
public class NativeEnvironmentRepository
		implements EnvironmentRepository, SearchPathLocator, Ordered {

	private static final String[] DEFAULT_LOCATIONS = new String[] { "classpath:/",
			"classpath:/config/", "file:./", "file:./config/" };

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

	public NativeEnvironmentRepository(ConfigurableEnvironment environment,
			NativeEnvironmentProperties properties) {
		this.environment = environment;
		this.addLabelLocations = properties.getAddLabelLocations();
		this.defaultLabel = properties.getDefaultLabel();
		this.failOnError = properties.getFailOnError();
		this.order = properties.getOrder();
		this.searchLocations = properties.getSearchLocations();
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
		SpringApplicationBuilder builder = new SpringApplicationBuilder(
				PropertyPlaceholderAutoConfiguration.class);
		ConfigurableEnvironment environment = getEnvironment(profile);
		builder.environment(environment);
		builder.web(WebApplicationType.NONE).bannerMode(Mode.OFF);
		if (!logger.isDebugEnabled()) {
			// Make the mini-application startup less verbose
			builder.logStartupInfo(false);
		}
		String[] args = getArgs(config, profile, label);
		// Explicitly set the listeners (to exclude logging listener which would change
		// log levels in the caller)
		builder.application()
				.setListeners(Arrays.asList(new ConfigFileApplicationListener()));
		ConfigurableApplicationContext context = builder.run(args);
		environment.getPropertySources().remove("profiles");
		try {
			return clean(new PassthruEnvironmentRepository(environment).findOne(config,
					profile, label));
		}
		finally {
			context.close();
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
		return new Locations(application, profile, label, this.version,
				output.toArray(new String[0]));
	}

	private ConfigurableEnvironment getEnvironment(String profile) {
		ConfigurableEnvironment environment = new StandardEnvironment();
		Map<String, Object> map = new HashMap<>();
		map.put("spring.profiles.active", profile);
		map.put("spring.main.web-application-type", "none");
		environment.getPropertySources().addFirst(new MapPropertySource("profiles", map));
		return environment;
	}

	protected Environment clean(Environment value) {
		Environment result = new Environment(value.getName(), value.getProfiles(),
				value.getLabel(), this.version, value.getState());
		for (PropertySource source : value.getPropertySources()) {
			String name = source.getName();
			if (this.environment.getPropertySources().contains(name)) {
				continue;
			}
			name = name.replace("applicationConfig: [", "");
			name = name.replace("]", "");
			if (this.searchLocations != null) {
				boolean matches = false;
				String normal = name;
				if (normal.startsWith("file:")) {
					normal = StringUtils
							.cleanPath(new File(normal.substring("file:".length()))
									.getAbsolutePath());
				}
				String profile = result.getProfiles() == null ? null
						: StringUtils.arrayToCommaDelimitedString(result.getProfiles());
				for (String pattern : getLocations(result.getName(), profile,
						result.getLabel()).getLocations()) {
					if (!pattern.contains(":")) {
						pattern = "file:" + pattern;
					}
					if (pattern.startsWith("file:")) {
						pattern = StringUtils
								.cleanPath(new File(pattern.substring("file:".length()))
										.getAbsolutePath())
								+ "/";
					}
					if (logger.isTraceEnabled()) {
						logger.trace("Testing pattern: " + pattern
								+ " with property source: " + name);
					}
					if (normal.startsWith(pattern)
							&& !normal.substring(pattern.length()).contains("/")) {
						matches = true;
						break;
					}
				}
				if (!matches) {
					// Don't include this one: it wasn't matched by our search locations
					if (logger.isDebugEnabled()) {
						logger.debug("Not adding property source: " + name);
					}
					continue;
				}
			}
			logger.info("Adding property source: " + name);
			result.add(new PropertySource(name, source.getSource()));
		}
		return result;
	}

	private String[] getArgs(String application, String profile, String label) {
		List<String> list = new ArrayList<String>();
		String config = application;
		if (!config.startsWith("application")) {
			config = "application," + config;
		}
		list.add("--spring.config.name=" + config);
		list.add("--spring.cloud.bootstrap.enabled=false");
		list.add("--encrypt.failOnError=" + this.failOnError);
		list.add("--spring.config.location=" + StringUtils.arrayToCommaDelimitedString(
				getLocations(application, profile, label).getLocations()));
		return list.toArray(new String[0]);
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
		return !location.contains("{") && !location.endsWith(".properties")
				&& !location.endsWith(".yml") && !location.endsWith(".yaml");
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

}
