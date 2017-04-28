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
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
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
	public static final String TOKEN_HEADER = "X-Config-Token";
	public static final String STATE_HEADER = "X-Config-State";

	/**
	 * Flag to say that remote configuration is enabled. Default true;
	 */
	private boolean enabled = true;

	/**
	 * The default profile to use when fetching remote configuration (comma-separated).
	 * Default is "default".
	 */
	private String profile = "default";

	/**
	 * Name of application used to fetch remote properties.
	 */
	@Value("${spring.application.name:application}")
	private String name;

	/**
	 * The label name to use to pull remote configuration properties. The default is set
	 * on the server (generally "master" for a git based server).
	 */
	private String label;

	/**
	 * The username to use (HTTP Basic) when contacting the remote server.
	 */
	private String username;

	/**
	 * The password to use (HTTP Basic) when contacting the remote server.
	 */
	private String password;

	/**
	 * The URI of the remote server (default http://localhost:8888).
	 */
	private String uri = "http://localhost:8888";

	/**
	 * Discovery properties.
	 */
	private Discovery discovery = new Discovery();

	/**
	 * Flag to indicate that failure to connect to the server is fatal (default false).
	 */
	private boolean failFast = false;

	/**
	 * Security Token passed thru to underlying environment repository.
	 */
	private String token;

	/**
	 * Authorization token used by the client to connect to the server.
	 */
	private String authorization;

	/**
	 * Additional headers used to create the client request.
	 */
	private Map<String, String> headers = new HashMap<>();

	private ConfigClientProperties() {
	}

	public ConfigClientProperties(Environment environment) {
		String[] profiles = environment.getActiveProfiles();
		if (profiles.length == 0) {
			profiles = environment.getDefaultProfiles();
		}
		this.setProfile(StringUtils.arrayToCommaDelimitedString(profiles));
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getRawUri() {
		return extractCredentials().uri;
	}

	public String getUri() {
		return this.uri;
	}

	public void setUri(String url) {
		this.uri = url;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getProfile() {
		return this.profile;
	}

	public void setProfile(String env) {
		this.profile = env;
	}

	public String getLabel() {
		return this.label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getUsername() {
		return extractCredentials().username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return extractCredentials().password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public Discovery getDiscovery() {
		return this.discovery;
	}

	public void setDiscovery(Discovery discovery) {
		this.discovery = discovery;
	}

	public boolean isFailFast() {
		return this.failFast;
	}

	public void setFailFast(boolean failFast) {
		this.failFast = failFast;
	}

	public String getToken() {
		return this.token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	@DeprecatedConfigurationProperty(reason = "replaced by headers", replacement = "headers")
	@Deprecated
	public String getAuthorization() {
		return this.authorization;
	}

	public void setAuthorization(String authorization) {
		this.authorization = authorization;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public void setHeaders(Map<String, String> headers) {
		this.headers = headers;
	}

	private Credentials extractCredentials() {
		Credentials result = new Credentials();
		String uri = this.uri;
		result.uri = uri;
		Credentials explicitCredentials = getUsernamePassword();
		result.username = explicitCredentials.username;
		result.password = explicitCredentials.password;
		try {
			URL url = new URL(uri);
			String userInfo = url.getUserInfo();
			// no credentials in url, return explicit credentials
			if (StringUtils.isEmpty(userInfo) || ":".equals(userInfo)) {
				return result;
			}
			String bare = UriComponentsBuilder.fromHttpUrl(uri).userInfo(null).build()
					.toUriString();
			result.uri = bare;
			// handle the password only case
			if (!userInfo.contains(":")) {
				userInfo = userInfo + ":";
			}
			String[] split = userInfo.split(":");
			// set username and password from uri
			result.username = split[0];
			result.password = split[1];

			// override password if explicitly set
			if (explicitCredentials.password != null) {
				// Explicit username / password takes precedence
				result.password = explicitCredentials.password;
			}
			// override username if explicitly set
			if (!"user".equals(explicitCredentials.username)) {
				// But the username can be overridden
				result.username = explicitCredentials.username;
			}
			return result;
		}
		catch (MalformedURLException e) {
			throw new IllegalStateException("Invalid URL: " + uri);
		}
	}

	private Credentials getUsernamePassword() {
		Credentials credentials = new Credentials();

		if (StringUtils.hasText(this.password)) {
			credentials.password = this.password.trim();
		}

		if (StringUtils.hasText(this.username)) {
			credentials.username = this.username.trim();
		} else {
			credentials.username = "user";
		}
		return credentials;
	}

	private static class Credentials {
		private String username;
		private String password;
		private String uri;
	}

	public static class Discovery {
		public static final String DEFAULT_CONFIG_SERVER = "configserver";

		/**
		 * Flag to indicate that config server discovery is enabled (config server URL will be
		 * looked up via discovery).
		 */
		private boolean enabled;
		/**
		 * Service id to locate config server.
		 */
		private String serviceId = DEFAULT_CONFIG_SERVER;

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getServiceId() {
			return this.serviceId;
		}

		public void setServiceId(String serviceId) {
			this.serviceId = serviceId;
		}

	}

	public ConfigClientProperties override(
			org.springframework.core.env.Environment environment) {
		ConfigClientProperties override = new ConfigClientProperties();
		BeanUtils.copyProperties(this, override);
		override.setName(
				environment.resolvePlaceholders("${" + ConfigClientProperties.PREFIX
						+ ".name:${spring.application.name:application}}"));
		if (environment.containsProperty(ConfigClientProperties.PREFIX + ".profile")) {
			override.setProfile(
					environment.getProperty(ConfigClientProperties.PREFIX + ".profile"));
		}
		if (environment.containsProperty(ConfigClientProperties.PREFIX + ".label")) {
			override.setLabel(
					environment.getProperty(ConfigClientProperties.PREFIX + ".label"));
		}
		return override;
	}

	@Override
	public String toString() {
		return "ConfigClientProperties [enabled=" + this.enabled + ", profile="
				+ this.profile + ", name=" + this.name + ", label="
				+ (this.label == null ? "" : this.label) + ", username=" + this.username
				+ ", password=" + this.password + ", uri=" + this.uri
				+ ", authorization=" + this.authorization
				+ ", discovery.enabled=" + this.discovery.enabled + ", failFast="
				+ this.failFast + ", token=" + this.token + "]";
	}

}
