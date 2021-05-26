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

import java.util.List;
import java.util.Objects;

import org.apache.commons.logging.Log;

import org.springframework.boot.context.config.ConfigDataResource;
import org.springframework.boot.context.config.Profiles;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.StringUtils;

public class ConfigServerConfigDataResource extends ConfigDataResource {

	private final ConfigClientProperties properties;

	private final boolean optional;

	private final Profiles profiles;

	private RetryProperties retryProperties;

	private Log log;

	public ConfigServerConfigDataResource(ConfigClientProperties properties, boolean optional, Profiles profiles) {
		this.properties = properties;
		this.optional = optional;
		this.profiles = profiles;
	}

	public ConfigClientProperties getProperties() {
		return this.properties;
	}

	public boolean isOptional() {
		return this.optional;
	}

	public String getProfiles() {
		if (StringUtils.hasText(properties.getProfile())
				&& !properties.getProfile().equals(ConfigClientProperties.DEFAULT_PROFILE)) {
			return properties.getProfile();
		}
		return StringUtils.collectionToCommaDelimitedString(getAcceptedProfiles());
	}

	List<String> getAcceptedProfiles() {
		return this.profiles.getAccepted();
	}

	public void setLog(Log log) {
		this.log = log;
	}

	public Log getLog() {
		return this.log;
	}

	public RetryProperties getRetryProperties() {
		return this.retryProperties;
	}

	public void setRetryProperties(RetryProperties retryProperties) {
		this.retryProperties = retryProperties;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ConfigServerConfigDataResource that = (ConfigServerConfigDataResource) o;
		return Objects.equals(this.properties, that.properties) && Objects.equals(this.optional, that.optional)
				&& Objects.equals(this.profiles, that.profiles);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.properties, this.optional, this.profiles);
	}

	@Override
	public String toString() {
		return new ToStringCreator(this).append("uris", properties.getUri()).append("optional", optional)
				.append("profiles", profiles.getAccepted()).toString();

	}

}
