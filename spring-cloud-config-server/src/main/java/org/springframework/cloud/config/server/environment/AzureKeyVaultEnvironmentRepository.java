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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.azure.core.http.rest.PagedIterable;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.azure.security.keyvault.secrets.models.SecretProperties;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;

/**
 * Environment repository backed by Azure Key Vault.
 *
 * @author Yash Chauhan
 */
public class AzureKeyVaultEnvironmentRepository implements EnvironmentRepository, Ordered {

	private final SecretClient secretClient;

	private final ConfigServerProperties configServerProperties;

	private final AzureKeyVaultEnvironmentProperties environmentProperties;

	private final int order;

	public AzureKeyVaultEnvironmentRepository(SecretClient secretClient, ConfigServerProperties configServerProperties,
			AzureKeyVaultEnvironmentProperties environmentProperties) {

		this.secretClient = secretClient;
		this.configServerProperties = configServerProperties;
		this.environmentProperties = environmentProperties;
		this.order = environmentProperties.getOrder();
	}

	@Override
	public Environment findOne(String application, String profile, String label) {

		String defaultApplication = this.configServerProperties.getDefaultApplicationName();
		String defaultProfile = this.configServerProperties.getDefaultProfile();

		if (!StringUtils.hasLength(application)) {
			application = defaultApplication;
		}

		if (!StringUtils.hasLength(profile)) {
			profile = defaultProfile;
		}

		String[] profiles = StringUtils.trimArrayElements(StringUtils.commaDelimitedListToStringArray(profile));

		Environment result = new Environment(application, profiles, label, null, null);

		List<String> reversedProfiles = new ArrayList<>(Arrays.asList(profiles));
		Collections.reverse(reversedProfiles);

		if (!reversedProfiles.contains(defaultProfile)) {
			reversedProfiles.add(defaultProfile);
		}

		List<String> applications = new ArrayList<>(
				Arrays.asList(StringUtils.commaDelimitedListToStringArray(application)));

		Collections.reverse(applications);

		if (!applications.contains(defaultApplication)) {
			applications.add(defaultApplication);
		}

		for (String profileName : reversedProfiles) {
			for (String applicationName : applications) {
				addPropertySource(result, applicationName, profileName);
			}
		}

		for (String applicationName : applications) {
			addPropertySource(result, applicationName, null);
		}

		Map<String, String> overrides = this.configServerProperties.getOverrides();

		if (!overrides.isEmpty()) {
			result.add(new PropertySource("overrides", overrides));
		}

		return result;
	}

	private void addPropertySource(Environment environment, String application, String profile) {

		Map<String, Object> properties = getSecrets(application, profile);

		if (!properties.isEmpty()) {

			String name = this.environmentProperties.getOrigin() + application;

			if (profile != null) {
				name += this.environmentProperties.getProfileSeparator() + profile;
			}

			environment.add(new PropertySource(name, properties));
		}
	}

	private Map<String, Object> getSecrets(String application, String profile) {

		Map<String, Object> result = new HashMap<>();

		String prefix = this.environmentProperties.getPrefix();
		String profileSeparator = this.environmentProperties.getProfileSeparator();

		String secretPrefix;

		if (profile == null) {
			secretPrefix = prefix + application + profileSeparator;
		}
		else {
			secretPrefix = prefix + application + profileSeparator + profile + profileSeparator;
		}

		PagedIterable<SecretProperties> secrets = this.secretClient.listPropertiesOfSecrets();

		for (SecretProperties secretProperties : secrets) {

			String secretName = secretProperties.getName();

			if (!secretName.startsWith(secretPrefix)) {
				continue;
			}

			KeyVaultSecret secret = this.secretClient.getSecret(secretName);

			String propertyName = secretName.substring(secretPrefix.length())
				.replace(this.environmentProperties.getPropertySeparator(), ".");

			result.put(propertyName, secret.getValue());
		}

		return result;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

}
