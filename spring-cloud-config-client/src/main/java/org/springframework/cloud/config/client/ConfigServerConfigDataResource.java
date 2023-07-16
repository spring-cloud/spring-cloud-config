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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.commons.logging.Log;

import org.springframework.boot.context.config.ConfigDataResource;
import org.springframework.boot.context.config.Profiles;
import org.springframework.core.style.ToStringCreator;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.cloud.config.client.ConfigClientProperties.DEFAULT_APPLICATION;
import static org.springframework.cloud.config.client.ConfigClientProperties.DEFAULT_PROFILE;

public class ConfigServerConfigDataResource extends ConfigDataResource {

	private final ConfigClientProperties properties;

	private final boolean optional;

	private final Profiles profiles;

	private RetryProperties retryProperties;

	private Log log;

	private boolean isProfileSpecific = false;

	public ConfigServerConfigDataResource(ConfigClientProperties properties, boolean optional, Profiles profiles) {
		this.properties = properties;
		this.optional = optional;
		this.profiles = profiles;
	}

	public ConfigClientProperties getProperties() {
		return this.properties;
	}

	public boolean isProfileSpecific() {
		return isProfileSpecific;
	}

	public void setProfileSpecific(boolean profileSpecific) {
		isProfileSpecific = profileSpecific;
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
		if (profiles == null) {
			return Collections.singletonList(!properties.getProfile().equals(ConfigClientProperties.DEFAULT_PROFILE)
					? properties.getProfile() : ConfigClientProperties.DEFAULT_PROFILE);
		}
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

	private String getApplicationName() {
		return ObjectUtils.isEmpty(this.properties.getName()) ? DEFAULT_APPLICATION : this.getProperties().getName();
	}

	private String getProfilesForEquals() {
		return ObjectUtils.isEmpty(this.getProfiles()) ? DEFAULT_PROFILE : this.getProfiles();
	}

	private boolean urisEqual(String[] thatUris) {
		if (this.properties.getUri().length != thatUris.length) {
			return false;
		}
		for (String uri : this.properties.getUri()) {
			if (Arrays.stream(thatUris).noneMatch(thatUri -> uriEqual(uri, thatUri))) {
				return false;
			}
		}
		return true;
	}

	private boolean uriEqual(String thisUriString, String thatUriString) {
		try {
			UriComponents thisUri = UriComponentsBuilder.fromHttpUrl(thisUriString).build();
			UriComponents thatUri = UriComponentsBuilder.fromHttpUrl(thatUriString).build();
			return Objects.equals(thisUri.getHost(), thatUri.getHost())
					&& Objects.equals(thisUri.getPort(), thatUri.getPort())
					&& Objects.equals(thisUri.getPath(), thatUri.getPath());
		}
		catch (Exception e) {
			return Objects.equals(thisUriString, thatUriString);
		}
	}

	private int urisHashCode(String[] uris) {
		return Arrays.stream(uris).mapToInt(uriString -> {
			try {
				UriComponents uriComponents = UriComponentsBuilder.fromHttpUrl(uriString).build();
				return Objects.hash(uriComponents.getHost(), uriComponents.getPath(), uriComponents.getPort());
			}
			catch (Exception e) {
				return Arrays.hashCode(uris);
			}
		}).sum();
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
		return urisEqual(that.properties.getUri())
				&& Objects.equals(this.getApplicationName(), that.getApplicationName())
				&& Objects.equals(this.properties.getLabel(), that.properties.getLabel())
				&& Objects.equals(this.getProfilesForEquals(), that.getProfilesForEquals())
				&& Objects.equals(this.optional, that.optional);
	}

	@Override
	public int hashCode() {
		String[] uris = properties.getUri();
		String name = properties.getName();
		String label = properties.getLabel();
		return Objects.hash(urisHashCode(uris), name, label, optional, getProfilesForEquals());
	}

	@Override
	public String toString() {
		return new ToStringCreator(this).append("uris", properties.getUri()).append("optional", optional)
				.append("profiles", getProfiles()).toString();

	}

}
