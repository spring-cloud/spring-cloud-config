/*
 * Copyright 2013-2020 the original author or authors.
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

import java.util.Objects;

import org.apache.commons.logging.Log;

import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.Profiles;
import org.springframework.core.style.ToStringCreator;
import org.springframework.web.client.RestTemplate;

public class ConfigServerConfigDataLocation extends ConfigDataLocation {

	private final Log log;

	private final RestTemplate restTemplate;

	private final ConfigClientProperties properties;

	private final Profiles profiles;

	public ConfigServerConfigDataLocation(Log log, RestTemplate restTemplate,
			ConfigClientProperties properties, Profiles profiles) {
		this.log = log;
		this.restTemplate = restTemplate;
		this.properties = properties;
		this.profiles = profiles;
	}

	public Log getLog() {
		return this.log;
	}

	public RestTemplate getRestTemplate() {
		return this.restTemplate;
	}

	public ConfigClientProperties getProperties() {
		return this.properties;
	}

	public Profiles getProfiles() {
		return this.profiles;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ConfigServerConfigDataLocation that = (ConfigServerConfigDataLocation) o;
		return Objects.equals(this.log, that.log)
				&& Objects.equals(this.restTemplate, that.restTemplate)
				&& Objects.equals(this.properties, that.properties)
				&& Objects.equals(this.profiles, that.profiles);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.log, this.restTemplate, this.properties, this.profiles);
	}

	@Override
	public String toString() {
		return new ToStringCreator(this).append("uris", properties.getUri())
				.append("profiles", profiles.getAccepted()).toString();

	}

}
