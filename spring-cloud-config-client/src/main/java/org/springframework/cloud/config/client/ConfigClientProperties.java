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

package org.springframework.cloud.config.client;

import java.net.MalformedURLException;
import java.net.URL;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Dave Syer
 *
 */
@ConfigurationProperties(ConfigClientProperties.PREFIX)
public class ConfigClientProperties {

	public static final String PREFIX = "spring.cloud.config";
	
	private boolean enabled = true;

	private String env = "default";

	@Value("${spring.application.name:'application'}")
	private String name;

	private String label = "master";

	private String username;

	private String password;

	private String uri = "http://localhost:8888";

	private Discovery discovery = new Discovery();

	private ConfigClientProperties() {
	}

	public ConfigClientProperties(Environment environment) {
		String[] profiles = environment.getActiveProfiles();
		if (profiles.length == 0) {
			profiles = environment.getDefaultProfiles();
		}
		this.setEnv(StringUtils.arrayToCommaDelimitedString(profiles));
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getUri() {
		return extractCredentials()[2];
	}

	public void setUri(String url) {
		this.uri = url;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEnv() {
		return env;
	}

	public void setEnv(String env) {
		this.env = env;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getUsername() {
		return extractCredentials()[0];
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return extractCredentials()[1];
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public Discovery getDiscovery() {
		return discovery;
	}

	public void setDiscovery(Discovery discovery) {
		this.discovery = discovery;
	}

	private String[] extractCredentials() {
		String[] result = new String[3];
		String uri = this.uri;
		result[2] = uri;
		String[] creds = getUsernamePassword();
		result[0] = creds[0];
		result[1] = creds[1];
		try {
			URL url = new URL(uri);
			String userInfo = url.getUserInfo();
			if (StringUtils.isEmpty(userInfo) || ":".equals(userInfo)) {
				return result;
			}
			String bare = UriComponentsBuilder.fromHttpUrl(uri).userInfo(null).build()
					.toUriString();
			result[2] = bare;
			if (!userInfo.contains(":")) {
				userInfo = userInfo + ":";
			}
			String[] split = userInfo.split(":");
			result[0] = split[0];
			result[1] = split[1];
			if (creds[1] != null) {
				// Explicit username / password takes precedence
				result[1] = creds[1];
				if ("user".equals(creds[0])) {
					// But the username can be overridden
					result[0] = split[0];
				}
			}
			return result;
		}
		catch (MalformedURLException e) {
			throw new IllegalStateException("Invalid URL: " + uri);
		}
	}

	private String[] getUsernamePassword() {
		if (StringUtils.hasText(password)) {
			return new String[] {
					StringUtils.hasText(username) ? username.trim() : "user",
					password.trim() };
		}
		return new String[2];
	}

	public static class Discovery {
		public static final String DEFAULT_CONFIG_SERVER = "CONFIGSERVER";

		private boolean enabled;
		private String prefix;
		private String serviceId = DEFAULT_CONFIG_SERVER;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getServiceId() {
			return serviceId;
		}

		public void setServiceId(String serviceId) {
			this.serviceId = serviceId;
		}

		public String getPrefix() {
			return prefix;
		}

		public void setPrefix(String prefix) {
			this.prefix = prefix;
		}
	}

	public ConfigClientProperties override(
			org.springframework.core.env.Environment environment) {
		ConfigClientProperties override = new ConfigClientProperties();
		BeanUtils.copyProperties(this, override);
		override.setName(environment.resolvePlaceholders("${"
				+ ConfigClientProperties.PREFIX
				+ ".name:${spring.application.name:'application'}}"));
		if (environment.containsProperty(ConfigClientProperties.PREFIX + ".env")) {
			override.setEnv(environment.getProperty(ConfigClientProperties.PREFIX + ".env"));
		}
		if (environment.containsProperty(ConfigClientProperties.PREFIX + ".label")) {
			override.setLabel(environment.getProperty(ConfigClientProperties.PREFIX + ".label"));
		}
		return override;
	}

	@Override
	public String toString() {
		return "ConfigClientProperties [name=" + name + ", env=" + env + ", label="
				+ label + ", uri=" + uri + ", discovery.enabled=" + discovery.enabled + "]";
	}

}
