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

import java.util.Map;

import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

/**
 * A PropertySourceLocator that reads from an EnvironmentRepository.
 * 
 * @author Dave Syer
 *
 */
public class EnvironmentRepositoryPropertySourceLocator implements PropertySourceLocator {

	private EnvironmentRepository repository;
	private String name;
	private String profiles;
	private String label;

	public EnvironmentRepositoryPropertySourceLocator(EnvironmentRepository repository,
			String name, String profiles, String label) {
		this.repository = repository;
		this.name = name;
		this.profiles = profiles;
		this.label = label;
	}

	@Override
	public org.springframework.core.env.PropertySource<?> locate(Environment environment) {
		CompositePropertySource composite = new CompositePropertySource("configService");
		for (PropertySource source : repository.findOne(name, profiles, label)
				.getPropertySources()) {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>) source.getSource();
			composite.addPropertySource(new MapPropertySource(source.getName(), map));
		}
		return composite;
	}

}
