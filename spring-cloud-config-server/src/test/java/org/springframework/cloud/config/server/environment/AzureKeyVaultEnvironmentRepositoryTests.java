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

		String secretName = "profile-foo--default--s3--accessKey";

		SecretProperties secretProperties = mock(SecretProperties.class);
		when(secretProperties.getName()).thenReturn(secretName);

		@SuppressWarnings("unchecked")
		PagedIterable<SecretProperties> secrets = mock(PagedIterable.class);
		when(secrets.iterator()).thenAnswer(invocation -> List.of(secretProperties).iterator());

		KeyVaultSecret secret = mock(KeyVaultSecret.class);
		when(secret.getValue()).thenReturn("test-value");

		when(secretClient.listPropertiesOfSecrets()).thenReturn(secrets);
		when(secretClient.getSecret(secretName)).thenReturn(secret);

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

		String matchingSecretName = "profile-foo--default--s3--accessKey";
		String nonMatchingSecretName = "profile-bar--default--s3--accessKey";

		SecretProperties matchingSecret = mock(SecretProperties.class);
		when(matchingSecret.getName()).thenReturn(matchingSecretName);

		SecretProperties nonMatchingSecret = mock(SecretProperties.class);
		when(nonMatchingSecret.getName()).thenReturn(nonMatchingSecretName);

		@SuppressWarnings("unchecked")
		PagedIterable<SecretProperties> secrets = mock(PagedIterable.class);
		when(secrets.iterator()).thenAnswer(invocation -> List.of(matchingSecret, nonMatchingSecret).iterator());

		KeyVaultSecret secret = mock(KeyVaultSecret.class);
		when(secret.getValue()).thenReturn("test-value");

		when(secretClient.listPropertiesOfSecrets()).thenReturn(secrets);
		when(secretClient.getSecret(matchingSecretName)).thenReturn(secret);

		AzureKeyVaultEnvironmentRepository repository = new AzureKeyVaultEnvironmentRepository(secretClient,
				configServerProperties, properties);

		Environment environment = repository.findOne("foo", "default", null);

		assertThat(environment.getPropertySources()).hasSize(1);

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

	@Test
	void shouldLoadApplicationWideSecretsWithoutLoadingProfileSecrets() {
		SecretClient secretClient = mock(SecretClient.class);
		AzureKeyVaultEnvironmentProperties properties = new AzureKeyVaultEnvironmentProperties();
		ConfigServerProperties configServerProperties = new ConfigServerProperties();

		String applicationSecretName = "app-foo--s3--accessKey";
		String profileSecretName = "profile-foo--default--s3--accessKey";

		SecretProperties applicationSecret = mock(SecretProperties.class);
		when(applicationSecret.getName()).thenReturn(applicationSecretName);

		SecretProperties profileSecret = mock(SecretProperties.class);
		when(profileSecret.getName()).thenReturn(profileSecretName);

		@SuppressWarnings("unchecked")
		PagedIterable<SecretProperties> secrets = mock(PagedIterable.class);
		when(secrets.iterator()).thenAnswer(invocation -> List.of(applicationSecret, profileSecret).iterator());

		KeyVaultSecret applicationValue = mock(KeyVaultSecret.class);
		when(applicationValue.getValue()).thenReturn("application-value");

		KeyVaultSecret profileValue = mock(KeyVaultSecret.class);
		when(profileValue.getValue()).thenReturn("profile-value");

		when(secretClient.listPropertiesOfSecrets()).thenReturn(secrets);
		when(secretClient.getSecret(applicationSecretName)).thenReturn(applicationValue);
		when(secretClient.getSecret(profileSecretName)).thenReturn(profileValue);

		AzureKeyVaultEnvironmentRepository repository = new AzureKeyVaultEnvironmentRepository(secretClient,
				configServerProperties, properties);

		Environment environment = repository.findOne("foo", "dev", null);

		PropertySource applicationSource = environment.getPropertySources()
			.stream()
			.filter(source -> source.getName().equals("azure:keyvault:foo"))
			.findFirst()
			.orElseThrow();

		assertThat((Map<String, Object>) applicationSource.getSource())
			.containsEntry("s3.accessKey", "application-value")
			.doesNotContainKey("default-s3.accessKey");
	}

	@Test
	void shouldPreserveHyphensInPropertyNames() {
		SecretClient secretClient = mock(SecretClient.class);
		AzureKeyVaultEnvironmentProperties properties = new AzureKeyVaultEnvironmentProperties();
		ConfigServerProperties configServerProperties = new ConfigServerProperties();

		String secretName = "profile-foo--dev--my-property";

		SecretProperties secretProperties = mock(SecretProperties.class);
		when(secretProperties.getName()).thenReturn(secretName);

		@SuppressWarnings("unchecked")
		PagedIterable<SecretProperties> secrets = mock(PagedIterable.class);
		when(secrets.iterator()).thenAnswer(invocation -> List.of(secretProperties).iterator());

		KeyVaultSecret secret = mock(KeyVaultSecret.class);
		when(secret.getValue()).thenReturn("test-value");

		when(secretClient.listPropertiesOfSecrets()).thenReturn(secrets);
		when(secretClient.getSecret(secretName)).thenReturn(secret);

		AzureKeyVaultEnvironmentRepository repository = new AzureKeyVaultEnvironmentRepository(secretClient,
				configServerProperties, properties);

		Environment environment = repository.findOne("foo", "dev", null);

		PropertySource profileSource = environment.getPropertySources()
			.stream()
			.filter(source -> source.getName().equals("azure:keyvault:foo-dev"))
			.findFirst()
			.orElseThrow();

		assertThat((Map<String, Object>) profileSource.getSource()).containsEntry("my-property", "test-value");
	}

}
