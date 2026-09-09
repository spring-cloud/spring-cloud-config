/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.config.client.oauth2;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configures OAuth2 token acquisition for outbound calls from the config client to the
 * config server. Token acquisition itself is configured under the standard
 * {@code spring.security.oauth2.client.*} properties; this class only opts the config
 * client in and selects which registration to use.
 */
@ConfigurationProperties(ConfigClientOAuth2Properties.PREFIX)
public class ConfigClientOAuth2Properties {

	/**
	 * Configuration prefix for config-client OAuth2 properties.
	 */
	public static final String PREFIX = "spring.cloud.config.oauth2";

	/**
	 * Whether to attach an OAuth2 bearer token to outbound config-client requests.
	 */
	private boolean enabled = false;

	/**
	 * Spring Security client registration id (as configured under
	 * {@code spring.security.oauth2.client.registration.<id>}) to use when fetching the
	 * access token.
	 */
	private String clientRegistrationId;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getClientRegistrationId() {
		return clientRegistrationId;
	}

	public void setClientRegistrationId(String clientRegistrationId) {
		this.clientRegistrationId = clientRegistrationId;
	}

}
