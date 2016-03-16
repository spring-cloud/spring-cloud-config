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

import static java.util.Objects.requireNonNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Dave Syer
 * @author Felix Kissel
 *
 */
@ConfigurationProperties(ConfigClientProperties.PREFIX)
public class ConfigClientProperties {
	
	static class ConfigSelectionProperties {
		
		private final String name;
		
		private final String label;
		
		private final String profile;

		public ConfigSelectionProperties(String name, String label, String profile) {
			this.name = name;
			this.label = label;
			this.profile = profile;
		}

		public String getName() {
			return name;
		}

		public String getProfile() {
			return profile;
		}
		
		public String[] getLabels() {
			if (StringUtils.hasText(this.label)) {
				return StringUtils.trimArrayElements(
						StringUtils.commaDelimitedListToStringArray(this.label));
			}
			else {
				return new String[] { "" };
			}
		}
		
	}
	
	static class Credentials extends UsernamePasswordPair{

		// Visible for testing
		Credentials(String username, String password) {
			super(requireNonNull(username), requireNonNull(password));
		}

		@Override
		public String toString() {
			return "Credentials [getUsername()=" + getUsername() + ", getPassword()=******]";
		}
		
	}
	static class UsernamePasswordPair {

		private final String username;
		
		private final String password;

		UsernamePasswordPair(String username, String password) {
			this.username = StringUtils.hasText(username) ? username.trim() : null;
			this.password = StringUtils.hasText(password) ? password.trim() : null;
		}

		public String getUsername() {
			return username;
		}
		
		public String getPassword() {
			return password;
		}
		
		@Override
		public String toString() {
			return getClass().getSimpleName() + " [username=" + username + ", password=" + password + "]";
		}

	}
	
	static class ConfigServerEndpoint {

		private final String rawUri;
		
		private final Credentials credentials;

		// Visible for testing
		static final String DEFAULT_USERNAME_user = "user";

		public ConfigServerEndpoint(String rawUri, Credentials credentials) {
			this.rawUri = rawUri;
			this.credentials = credentials;
		}

		public String getRawUri() {
			return rawUri;
		}

		public Credentials getCredentials() {
			return credentials;
		}
		
		@Override
		public String toString() {
			return "ConfigServerEndpoint [rawUri=" + rawUri + ", credentials="
					+ credentials + "]";
		}

		static ConfigServerEndpoint create(String uri,
				UsernamePasswordPair explicitCredentials) {
			try {
				URL url = new URL(uri);
				String userInfo = url.getUserInfo();
				String uriWithoutCredentials = UriComponentsBuilder.fromHttpUrl(uri).userInfo(null)
						.build().toUriString();
				
				UsernamePasswordPair uriCredentials = determineUriCreds(userInfo);
		
				String username = explicitCredentials.getUsername() != null
						? explicitCredentials.getUsername() : uriCredentials.getUsername();
				String password = explicitCredentials.getPassword() != null
						? explicitCredentials.getPassword() : uriCredentials.getPassword();
		
				ConfigClientProperties.Credentials resultCredentials;
				if (password != null) {
					resultCredentials = new ConfigClientProperties.Credentials(
							username == null ? DEFAULT_USERNAME_user : username,
							password);
				}
				else {
					resultCredentials = null;
				}
				return new ConfigClientProperties.ConfigServerEndpoint(uriWithoutCredentials, resultCredentials);
			}
			catch (MalformedURLException e) {
				throw new IllegalStateException("Invalid URL: " + uri);
			}
		}
		
		private static UsernamePasswordPair determineUriCreds(String userInfo) {
			if(userInfo == null || userInfo.trim().equals(":")) {
				return new UsernamePasswordPair(null, null);
			}
			if (userInfo.contains(":")) {
				String[] split = userInfo.split(":", -1);
				return new UsernamePasswordPair(split[0], split[1]);
			}
			else {
				return new UsernamePasswordPair(userInfo, null);
			}
		}

	}

	public static final String PREFIX = "spring.cloud.config";

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

	@SuppressWarnings("unused")
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

	/**
	 * @deprecated use {@link #getConfigServerEndpoints()} instead
	 * @return the without uri-credentials for the first config server address
	 */
	@Deprecated
	public String getRawUri() {
		return getSingleConfigServerEndpoint().getRawUri();
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

	/**
	 * @deprecated use {@link #getConfigServerEndpoints()} instead
	 * @return the username for the first config server address; might be null
	 */
	@Deprecated
	public String getUsername() {
		Credentials credentials = getSingleConfigServerEndpoint().getCredentials();
		return credentials == null ? null : credentials.getUsername();
	}

	public void setUsername(String username) {
		this.username = username;
	}
	
	/**
	 * @deprecated use {@link #getConfigServerEndpoints()} instead
	 * @return the password for the first config server address; might be null
	 */
	@Deprecated
	public String getPassword() {
		Credentials credentials = getSingleConfigServerEndpoint().getCredentials();
		return credentials == null ? null : credentials.getPassword();
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

	private ConfigServerEndpoint determineConfigServerEndpoint(String uri) {
		return ConfigServerEndpoint.create(uri, getExplicitCredentials());
	}

	
	/**
	 * @return a list of ConfigServerEndpoint, might be empty
	 */
	public List<ConfigServerEndpoint> getConfigServerEndpoints() {
		if(StringUtils.isEmpty(this.uri)) {
			return Collections.emptyList();
		}
		String[] uris = StringUtils.commaDelimitedListToStringArray(this.uri);
		List<ConfigServerEndpoint> result = new ArrayList<>();
		for (String oneUri : uris) {
			if(StringUtils.hasText(oneUri)) {
				result.add(determineConfigServerEndpoint(oneUri.trim()));
			}
		}
		return result;
	}
	
	private ConfigServerEndpoint getSingleConfigServerEndpoint() {
		List<ConfigServerEndpoint> configServerEndpoints = getConfigServerEndpoints();
		if(configServerEndpoints.size() != 1) {
			throw new IllegalStateException(
					"not a configuration with exactly one uri: " + this.uri);
		}
		return configServerEndpoints.iterator().next();
	}

	private UsernamePasswordPair getExplicitCredentials() {
		if (StringUtils.hasText(this.password)) {
			return new UsernamePasswordPair(this.username, this.password);
		}
		else {
			return new UsernamePasswordPair(null, null);
		}
	}

	public static class Discovery {
		public static final String DEFAULT_CONFIG_SERVER = "CONFIGSERVER";

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

	public ConfigSelectionProperties getConfigSelectionProperties(
			org.springframework.core.env.Environment environment) {
		String name = environment.resolvePlaceholders("${" + ConfigClientProperties.PREFIX
				+ ".name:${spring.application.name:application}}");
		String profile;
		if (environment.containsProperty(ConfigClientProperties.PREFIX + ".profile")) {
			profile = environment.getProperty(ConfigClientProperties.PREFIX + ".profile");
		} else {
			profile = this.getProfile();
		}
		String label;
		if (environment.containsProperty(ConfigClientProperties.PREFIX + ".label")) {
			label = environment.getProperty(ConfigClientProperties.PREFIX + ".label");
		} else {
			label = this.getLabel();
		}
		return new ConfigSelectionProperties(name, label, profile);
	}

	@Override
	public String toString() {
		return "ConfigClientProperties [enabled=" + this.enabled + ", profile="
				+ this.profile + ", name=" + this.name + ", label="
				+ (this.label == null ? "" : this.label) + ", username=" + this.username
				+ ", password=" + this.password + ", uri=" + this.uri
				+ ", discovery.enabled=" + this.discovery.enabled + ", failFast="
				+ this.failFast + "]";
	}

}
