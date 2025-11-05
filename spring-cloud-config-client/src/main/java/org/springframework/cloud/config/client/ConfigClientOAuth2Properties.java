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

package org.springframework.cloud.config.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Bruce Randall
 *
 */
@ConfigurationProperties(ConfigClientOAuth2Properties.PREFIX)
public class ConfigClientOAuth2Properties {

	/**
	 * Prefix for Spring Cloud Config properties.
	 */
	public static final String PREFIX = "spring.cloud.config.oauth2";

	/**
	 * The OAuth2 token URI of the IDP issuing JWT tokens. When present enables OAuth2
	 * client calls.
	 */
	private String tokenUri;

	/**
	 * The OAuth2 grant type (client_credentials, password).
	 */
	private String grantType;

	/**
	 * The OAuth2 client id should it be needed in JWT token request.
	 */
	private String clientId;

	/**
	 * The OAuth2 client secret should it be needed in JWT token request.
	 */
	private String clientSecret;

	/**
	 * The OAuth2 username to use when contacting the IDP.
	 */
	private String oauthUsername;

	/**
	 * The OAuth2 user password to use when contacting the IDP.
	 */
	private String oauthPassword;

	public String getTokenUri() {
		return tokenUri;
	}

	public void setTokenUri(String tokenUri) {
		this.tokenUri = tokenUri;
	}

	public String getGrantType() {
		return grantType;
	}

	public void setGrantType(String grantType) {
		this.grantType = grantType;
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public String getClientSecret() {
		return clientSecret;
	}

	public void setClientSecret(String clientSecret) {
		this.clientSecret = clientSecret;
	}

	public String getOauthUsername() {
		return oauthUsername;
	}

	public void setOauthUsername(String oauthUsername) {
		this.oauthUsername = oauthUsername;
	}

	public String getOauthPassword() {
		return oauthPassword;
	}

	public void setOauthPassword(String oauthPassword) {
		this.oauthPassword = oauthPassword;
	}

	@Override
	public String toString() {
		return "ConfigClientOAuth2Properties{" + "tokenUri='" + tokenUri + '\'' + ", grantType='" + grantType + '\''
				+ ", clientId='" + clientId + '\'' + ", oauthUsername='" + oauthUsername + '\'' + '}';
	}

}
