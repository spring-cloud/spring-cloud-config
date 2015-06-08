/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.config.server;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.config.ConfigFileApplicationListener;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.context.ConfigurableApplicationContext;
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
 */
@ConfigurationProperties("spring.cloud.config.server.native")
public class NativeEnvironmentRepository implements EnvironmentRepository {

	private static Log logger = LogFactory.getLog(NativeEnvironmentRepository.class);

	private static final String DEFAULT_LABEL = "master";

	/**
	 * Locations to search for configuration files. Defaults to the same as a Spring Boot
	 * app so [classpath:/,classpath:/config/,file:./,file:./config/].
	 */
	private String[] searchLocations;

	/**
	 * Flag to determine how to handle exceptions during decryption (default false).
	 */
	private boolean failOnError = false;

	private static final String[] DEFAULT_LOCATIONS = new String[] { "classpath:/",
			"classpath:/config/", "file:./", "file:./config/" };

	private ConfigurableEnvironment environment;

	public NativeEnvironmentRepository(ConfigurableEnvironment environment) {
		this.environment = environment;
	}

	public void setFailOnError(boolean failOnError) {
		this.failOnError = failOnError;
	}

	public boolean isFailOnError() {
		return failOnError;
	}

	@Override
	public String getDefaultLabel() {
		return DEFAULT_LABEL;
	}

	@Override
	public Environment findOne(String config, String profile, String label) {
		SpringApplicationBuilder builder = new SpringApplicationBuilder(
				PropertyPlaceholderAutoConfiguration.class);
		ConfigurableEnvironment environment = getEnvironment(profile);
		builder.environment(environment);
		builder.web(false).showBanner(false);
		String[] args = getArgs(config, label);
		// Explicitly set the listeners (to exclude logging listener which would change
		// log levels in the caller)
		builder.application().setListeners(
				Collections.singletonList(new ConfigFileApplicationListener()));
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

	private ConfigurableEnvironment getEnvironment(String profile) {
		ConfigurableEnvironment environment = new StandardEnvironment();
		environment.getPropertySources()
				.addFirst(
						new MapPropertySource("profiles", Collections
								.<String, Object> singletonMap("spring.profiles.active",
										profile)));
		return environment;
	}

	protected Environment clean(Environment value) {
		Environment result = new Environment(value.getName(), value.getProfiles(),
				value.getLabel());
		for (PropertySource source : value.getPropertySources()) {
			String name = source.getName();
			if (environment.getPropertySources().contains(name)) {
				continue;
			}
			name = name.replace("applicationConfig: [", "");
			name = name.replace("]", "");
			if (searchLocations != null) {
				boolean matches = false;
				String normal = name;
				if (normal.startsWith("file:")) {
					normal = StringUtils.cleanPath(new File(normal.substring("file:".length()))
							.getAbsolutePath());
				}
				for (String pattern : StringUtils
						.commaDelimitedListToStringArray(getLocations(searchLocations,
								result.getLabel()))) {
					if (!pattern.contains(":")) {
						pattern = "file:" + pattern;
					}
					if (pattern.startsWith("file:")) {
						pattern = StringUtils.cleanPath(new File(pattern
								.substring("file:".length())).getAbsolutePath()) + "/";
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

	private String[] getArgs(String config, String label) {
		List<String> list = new ArrayList<String>();
		if (!config.startsWith("application")) {
			config = "application," + config;
		}
		list.add("--spring.config.name=" + config);
		list.add("--spring.cloud.bootstrap.enabled=false");
		list.add("--encrypt.failOnError=" + failOnError);
		String[] locations = this.searchLocations;
		if (searchLocations == null) {
			locations = DEFAULT_LOCATIONS;
		}
		list.add("--spring.config.location=" + getLocations(locations, label));
		return list.toArray(new String[0]);
	}

	private String getLocations(String[] locations, String label) {
		List<String> output = new ArrayList<String>();
		for (String location : locations) {
			output.add(location);
		}
		for (String location : locations) {
			if (isDirectory(location) && StringUtils.hasText(label)) {
				output.add(location + label.trim() + "/");
			}
		}
		return StringUtils.collectionToCommaDelimitedString(output);
	}

	public String[] getSearchLocations() {
		return searchLocations;
	}

	public void setSearchLocations(String... locations) {
		this.searchLocations = locations;
		for (int i = 0; i < locations.length; i++) {
			String location = locations[i];
			if (isDirectory(location) && !location.endsWith("/")) {
				location = location + "/";
			}
			locations[i] = location;
		}
	}

	private boolean isDirectory(String location) {
		return !location.endsWith(".properties") && !location.endsWith(".yml")
				&& !location.endsWith(".yaml");
	}

}
