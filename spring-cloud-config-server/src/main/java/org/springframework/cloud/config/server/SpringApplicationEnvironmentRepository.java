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

package org.springframework.cloud.config.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.cloud.config.Environment;
import org.springframework.util.StringUtils;

/**
 * Simple implementation of {@link EnvironmentRepository} that uses a SpringApplication
 * and configuration files located through the normal protocols. The resulting Environment
 * is composed of property sources located using the application name as the config file
 * stem (spring.config.name) and the environment name as a Spring profile.
 * 
 * @author Dave Syer
 *
 */
public class SpringApplicationEnvironmentRepository implements EnvironmentRepository {

	private String[] locations;

	@Override
	public Environment findOne(String config, String profile, String label) {
		SpringApplicationBuilder builder = new SpringApplicationBuilder(
				PropertyPlaceholderAutoConfiguration.class);
		ConfigurableEnvironment environment = getEnvironment(profile);
		builder.environment(environment);
		builder.web(false).showBanner(false);
		String[] args = getArgs(config);
		ConfigurableApplicationContext context = builder.run(args);
		environment.getPropertySources().remove("profiles");
		try {
			return new NativeEnvironmentRepository(environment).findOne(
					config, profile, label);
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

	private String[] getArgs(String config) {
		List<String> list = new ArrayList<String>();
		if (!config.startsWith("application")) {
			config = "application," + config;
		}
		list.add("--spring.config.name=" + config);
		list.add("--spring.platform.bootstrap.enabled=false");
		if (locations != null) {
			list.add("--spring.config.location="
					+ StringUtils.arrayToCommaDelimitedString(locations));
		}
		return list.toArray(new String[0]);
	}

	public void setSearchLocations(String... locations) {
		this.locations = locations;
		for (int i = 0; i < locations.length; i++) {
			String location = locations[i];
			if (!location.endsWith(".properties") && !location.endsWith(".yml")
					&& !location.endsWith(".yaml") && !location.endsWith("/")) {
				location = location + "/";
			}
			locations[i] = location;
		}
	}

}
