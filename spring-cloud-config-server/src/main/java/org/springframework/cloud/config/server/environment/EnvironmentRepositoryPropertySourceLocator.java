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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.resource.ResourceRepository;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.Resource;

/**
 * A PropertySourceLocator that reads from an EnvironmentRepository.
 *
 * @author Dave Syer
 */
public class EnvironmentRepositoryPropertySourceLocator implements PropertySourceLocator {

	private EnvironmentRepository repository;

	private ResourceRepository resourceRepository;

	private String name;

	private String profiles;

	private String label;

	public EnvironmentRepositoryPropertySourceLocator(EnvironmentRepository repository,
			ResourceRepository resourceRepository, String name, String profiles, String label) {
		this.repository = repository;
		this.resourceRepository = resourceRepository;
		this.name = name;
		this.profiles = profiles;
		this.label = label;
	}

	@Override
	public org.springframework.core.env.PropertySource<?> locate(Environment environment) {
		CompositePropertySource composite = new CompositePropertySource("configService");
		for (PropertySource source : this.repository.findOne(this.name, this.profiles, this.label, false)
				.getPropertySources()) {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>) source.getSource();
			composite.addPropertySource(new MapPropertySource(source.getName(), map));
		}

		String logFile = (String) composite.getProperty("spring.cloud.config.server.bootstrap-log-file-path");
		if (logFile != null) {
			Resource resource = resourceRepository.findOne(this.name, this.profiles, this.label, logFile);
			try {
				HashMap<String, Object> source = new HashMap<>();
				source.put("logging.config", resource.getURL());

				composite.addFirstPropertySource(new MapPropertySource("loggingConfig", source));
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		return composite;
	}

}
