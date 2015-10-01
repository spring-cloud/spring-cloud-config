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

package org.springframework.cloud.config.environment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Simple plain text serializable encapsulation of a list of property sources. Basically a
 * DTO for {@link org.springframework.core.env.Environment}, but also applicable outside
 * the domain of a Spring application.
 *
 * @author Dave Syer
 * @author Spencer Gibb
 *
 */
public class Environment {

	private String name;

	private String[] profiles = new String[0];

	private String label;

	private List<PropertySource> propertySources = new ArrayList<PropertySource>();

	private String version;

	public Environment(String name, String... profiles) {
		this(name, profiles, "master", null);
	}

	@JsonCreator
	public Environment(@JsonProperty("name") String name,
			@JsonProperty("profiles") String[] profiles,
			@JsonProperty("label") String label,
			@JsonProperty("version") String version) {
		super();
		this.name = name;
		this.profiles = profiles;
		this.label = label;
		this.version = version;
	}

	public void add(PropertySource propertySource) {
		this.propertySources.add(propertySource);
	}

	public void addFirst(PropertySource propertySource) {
		this.propertySources.add(0, propertySource);
	}

	public List<PropertySource> getPropertySources() {
		return propertySources;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String[] getProfiles() {
		return profiles;
	}

	public void setProfiles(String[] profiles) {
		this.profiles = profiles;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	@Override
	public String toString() {
		return "Environment [name=" + name + ", profiles=" + Arrays.asList(profiles)
				+ ", label=" + label + ", propertySources=" + propertySources
				+ ", version=" + version+ "]";
	}

}
