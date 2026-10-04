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

import java.util.List;
import java.util.Map;

import com.azure.core.http.rest.PagedIterable;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.azure.security.keyvault.secrets.models.SecretProperties;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.ConfigServerProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AzureKeyVaultEnvironmentRepositoryTests {

	@Test
	void shouldLoadSecretsForApplicationAndProfile() {
		SecretClient secretClient = mock(SecretClient.class);

		AzureKeyVaultEnvironmentProperties properties = new AzureKeyVaultEnvironmentProperties();

		ConfigServerProperties configServerProperties = new ConfigServerProperties();

		SecretProperties secretProperties = mock(SecretProperties.class);

		when(secretProperties.getName()).thenReturn("foo-default-s3--accessKey");

		@SuppressWarnings("unchecked")
		PagedIterable<SecretProperties> secrets = mock(PagedIterable.class);

		when(secrets.iterator()).thenReturn(List.of(secretProperties).iterator());

		KeyVaultSecret secret = mock(KeyVaultSecret.class);

		when(secret.getValue()).thenReturn("test-value");

		when(secretClient.listPropertiesOfSecrets()).thenReturn(secrets);

		when(secretClient.getSecret("foo-default-s3--accessKey")).thenReturn(secret);

		AzureKeyVaultEnvironmentRepository repository = new AzureKeyVaultEnvironmentRepository(secretClient,
				configServerProperties, properties);

		Environment environment = repository.findOne("foo", "default", null);

		assertThat(environment.getPropertySources()).hasSize(1);

		PropertySource propertySource = environment.getPropertySources().get(0);

		assertThat((Map<String, Object>) propertySource.getSource()).containsEntry("s3.accessKey", "test-value");
	}

	@Test
	void shouldIgnoreSecretsForDifferentApplicationOrProfile() {
		SecretClient secretClient = mock(SecretClient.class);

		AzureKeyVaultEnvironmentProperties properties = new AzureKeyVaultEnvironmentProperties();

		ConfigServerProperties configServerProperties = new ConfigServerProperties();

		SecretProperties matchingSecret = mock(SecretProperties.class);

		SecretProperties nonMatchingSecret = mock(SecretProperties.class);

		when(matchingSecret.getName()).thenReturn("foo-default-s3--accessKey");

		when(nonMatchingSecret.getName()).thenReturn("bar-default-s3--accessKey");

		@SuppressWarnings("unchecked")
		PagedIterable<SecretProperties> secrets = mock(PagedIterable.class);

		when(secrets.iterator()).thenReturn(List.of(matchingSecret, nonMatchingSecret).iterator());

		KeyVaultSecret secret = mock(KeyVaultSecret.class);

		when(secret.getValue()).thenReturn("test-value");

		when(secretClient.listPropertiesOfSecrets()).thenReturn(secrets);

		when(secretClient.getSecret("foo-default-s3--accessKey")).thenReturn(secret);

		AzureKeyVaultEnvironmentRepository repository = new AzureKeyVaultEnvironmentRepository(secretClient,
				configServerProperties, properties);

		Environment environment = repository.findOne("foo", "default", null);

		PropertySource propertySource = environment.getPropertySources().get(0);

		assertThat((Map<String, Object>) propertySource.getSource()).hasSize(1)
			.containsEntry("s3.accessKey", "test-value");
	}

	@Test
	void shouldUseConfiguredOrder() {
		SecretClient secretClient = mock(SecretClient.class);

		AzureKeyVaultEnvironmentProperties properties = new AzureKeyVaultEnvironmentProperties();

		properties.setOrder(42);

		ConfigServerProperties configServerProperties = new ConfigServerProperties();

		AzureKeyVaultEnvironmentRepository repository = new AzureKeyVaultEnvironmentRepository(secretClient,
				configServerProperties, properties);

		assertThat(repository.getOrder()).isEqualTo(42);
	}

}
