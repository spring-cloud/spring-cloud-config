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

package org.springframework.platform.bootstrap.config;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Dave Syer
 *
 */
public class Environment {

	private String name;

	private String label;

	private List<PropertySource> propertySources = new ArrayList<PropertySource>();

	@JsonCreator
	public Environment(@JsonProperty("name") String name,
			@JsonProperty("label") String label) {
		super();
		this.name = name;
		this.label = label;
	}

	public void add(PropertySource propertySource) {
		this.propertySources.add(propertySource);
	}

	public List<PropertySource> getPropertySources() {
		return propertySources;
	}

	public String getName() {
		return name;
	}

	public String getLabel() {
		return label;
	}

}
