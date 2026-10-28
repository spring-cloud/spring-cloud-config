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

package org.springframework.cloud.config.server.environment.compliance;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.azure.core.http.rest.PagedIterable;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.azure.security.keyvault.secrets.models.SecretProperties;

import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.environment.AzureKeyVaultEnvironmentProperties;
import org.springframework.cloud.config.server.environment.AzureKeyVaultEnvironmentRepository;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Compliance tests for {@link AzureKeyVaultEnvironmentRepository}. Each configuration
 * file is represented by a group of Azure Key Vault secrets.
 *
 * @author Yash Chauhan
 */
class AzureKeyVaultEnvironmentRepositoryComplianceTests extends EnvironmentRepositoryComplianceTests {

	private final Fixture fixture = new Fixture();

	@Override
	protected EnvironmentRepositoryFixture fixture() {
		return this.fixture;
	}

	static class Fixture implements EnvironmentRepositoryFixture {

		private final AzureKeyVaultEnvironmentProperties properties = new AzureKeyVaultEnvironmentProperties();

		private final ConfigServerProperties configServerProperties = new ConfigServerProperties();

		private final Map<String, String> storedSecrets = new HashMap<>();

		private final SecretClient secretClient = mock(SecretClient.class);

		Fixture() {
			configureSecretClient();
		}

		private void configureSecretClient() {
			@SuppressWarnings("unchecked")
			PagedIterable<SecretProperties> secrets = mock(PagedIterable.class);

			when(secrets.iterator())
				.thenAnswer(invocation -> this.storedSecrets.keySet().stream().map(this::secretProperties).iterator());

			when(this.secretClient.listPropertiesOfSecrets()).thenReturn(secrets);

			when(this.secretClient.getSecret(anyString())).thenAnswer(invocation -> {
				String name = invocation.getArgument(0);
				String value = this.storedSecrets.get(name);

				if (value == null) {
					throw new IllegalArgumentException("Secret not found: " + name);
				}

				KeyVaultSecret secret = mock(KeyVaultSecret.class);
				when(secret.getValue()).thenReturn(value);
				return secret;
			});
		}

		private SecretProperties secretProperties(String name) {
			SecretProperties properties = mock(SecretProperties.class);
			when(properties.getName()).thenReturn(name);
			return properties;
		}

		@Override
		public void reset() {
			this.storedSecrets.clear();
		}

		@Override
		public void store(ConfigFile file) {
			String secretPrefix;

			if (file.profile() == null) {
				secretPrefix = this.properties.getPrefix() + "app-" + file.application() + "--";
			}
			else {
				secretPrefix = this.properties.getPrefix() + "profile-" + file.application() + "--" + file.profile()
						+ "--";
			}

			file.properties().forEach((key, value) -> {
				String encodedProperty = key.replace(".", this.properties.getPropertySeparator());

				this.storedSecrets.put(secretPrefix + encodedProperty, String.valueOf(value));
			});
		}

		@Override
		public EnvironmentRepository repository() {
			return new AzureKeyVaultEnvironmentRepository(this.secretClient, this.configServerProperties,
					this.properties);
		}

		@Override
		public Set<Capability> capabilities() {
			return EnumSet.of(Capability.MULTIPLE_APPLICATIONS, Capability.DISTINCT_DEFAULT_PROFILE);
		}

	}

}
