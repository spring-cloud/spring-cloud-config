/*
 * Copyright 2026-present the original author or authors.
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

package org.springframework.cloud.config.server.environment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.config.server.support.EnvironmentRepositoryProperties;

/**
 * Configuration properties for the Azure Key Vault environment repository.
 *
 * @author Yash Chauhan
 */
@ConfigurationProperties("spring.cloud.config.server.azure-keyvault")
public class AzureKeyVaultEnvironmentProperties implements EnvironmentRepositoryProperties {

	private static final String DEFAULT_PROFILE_SEPARATOR = "-";

	private static final String DEFAULT_PROPERTY_SEPARATOR = "--";

	private static final String DEFAULT_ORIGIN = "azure:keyvault:";

	/**
	 * The URI of the Azure Key Vault.
	 */
	@NotBlank
	private String vaultUri;

	/**
	 * The prefix used for Azure Key Vault secret names.
	 */
	private String prefix = "";

	/**
	 * String that separates the application, profile, and property name.
	 */
	@NotBlank
	@Pattern(regexp = "[a-zA-Z0-9._-]+")
	private String profileSeparator = DEFAULT_PROFILE_SEPARATOR;

	/**
	 * String used to represent dots in property names stored in Azure Key Vault.
	 */
	@NotBlank
	private String propertySeparator = DEFAULT_PROPERTY_SEPARATOR;

	/**
	 * Prefix indicating the origin of the property.
	 */
	@NotNull
	private String origin = DEFAULT_ORIGIN;

	/**
	 * The order of the environment repository.
	 */
	private int order = DEFAULT_ORDER;

	public String getVaultUri() {
		return this.vaultUri;
	}

	public void setVaultUri(String vaultUri) {
		this.vaultUri = vaultUri;
	}

	public String getPrefix() {
		return this.prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	public String getProfileSeparator() {
		return this.profileSeparator;
	}

	public void setProfileSeparator(String profileSeparator) {
		this.profileSeparator = profileSeparator;
	}

	public String getPropertySeparator() {
		return this.propertySeparator;
	}

	public void setPropertySeparator(String propertySeparator) {
		this.propertySeparator = propertySeparator;
	}

	public String getOrigin() {
		return this.origin;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}

	public int getOrder() {
		return this.order;
	}

	@Override
	public void setOrder(int order) {
		this.order = order;
	}

}
