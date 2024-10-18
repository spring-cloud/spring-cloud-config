/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.config.server.environment.vault;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.config.server.environment.EnvironmentWatch;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport;
import org.springframework.vault.core.VaultTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Scott Frederick
 */
public class SpringVaultEnvironmentRepositoryFactoryTests {

	private final SpringVaultClientConfiguration clientConfiguration = mock(SpringVaultClientConfiguration.class);

	private final SpringVaultTemplateBuilder vaultTemplateBuilder = mock(SpringVaultTemplateBuilder.class);

	private final VaultTemplate vaultTemplate = new VaultTemplate(VaultEndpoint.create("localhost", 8200),
			new TokenAuthentication("token"));

	@Test
	public void buildForVersion1() {
		VaultEnvironmentProperties properties = new VaultEnvironmentProperties();
		when(clientConfiguration.vaultTemplate()).thenReturn(vaultTemplate);

		SpringVaultEnvironmentRepository environmentRepository = new SpringVaultEnvironmentRepositoryFactory(
				mockHttpRequest(), new EnvironmentWatch.Default(), clientConfiguration)
			.build(properties);

		VaultKeyValueOperations keyValueTemplate = environmentRepository.getKeyValueTemplate();
		assertThat(keyValueTemplate.getApiVersion()).isEqualTo(VaultKeyValueOperationsSupport.KeyValueBackend.KV_1);
		verify(clientConfiguration).vaultTemplate();
		verifyNoMoreInteractions(clientConfiguration, vaultTemplateBuilder);
	}

	@Test
	public void buildForVersion1WithVaultTemplateBuilder() {
		VaultEnvironmentProperties properties = new VaultEnvironmentProperties();
		when(vaultTemplateBuilder.build(properties)).thenReturn(vaultTemplate);

		SpringVaultEnvironmentRepository environmentRepository = new SpringVaultEnvironmentRepositoryFactory(
				mockHttpRequest(), new EnvironmentWatch.Default(), vaultTemplateBuilder)
			.build(properties);

		VaultKeyValueOperations keyValueTemplate = environmentRepository.getKeyValueTemplate();
		assertThat(keyValueTemplate.getApiVersion()).isEqualTo(VaultKeyValueOperationsSupport.KeyValueBackend.KV_1);
		verify(vaultTemplateBuilder).build(properties);
		verifyNoMoreInteractions(clientConfiguration, vaultTemplateBuilder);
	}

	@Test
	public void buildForVersion2() {
		VaultEnvironmentProperties properties = new VaultEnvironmentProperties();
		properties.setKvVersion(2);
		when(clientConfiguration.vaultTemplate()).thenReturn(vaultTemplate);

		SpringVaultEnvironmentRepository environmentRepository = new SpringVaultEnvironmentRepositoryFactory(
				mockHttpRequest(), new EnvironmentWatch.Default(), clientConfiguration)
			.build(properties);

		VaultKeyValueOperations keyValueTemplate = environmentRepository.getKeyValueTemplate();
		assertThat(keyValueTemplate.getApiVersion()).isEqualTo(VaultKeyValueOperationsSupport.KeyValueBackend.KV_2);
		verify(clientConfiguration).vaultTemplate();
		verifyNoMoreInteractions(clientConfiguration, vaultTemplateBuilder);
	}

	@Test
	public void buildForVersion2WithVaultTemplateBuilder() {
		VaultEnvironmentProperties properties = new VaultEnvironmentProperties();
		properties.setKvVersion(2);
		when(vaultTemplateBuilder.build(properties)).thenReturn(vaultTemplate);

		SpringVaultEnvironmentRepository environmentRepository = new SpringVaultEnvironmentRepositoryFactory(
				mockHttpRequest(), new EnvironmentWatch.Default(), vaultTemplateBuilder)
			.build(properties);

		VaultKeyValueOperations keyValueTemplate = environmentRepository.getKeyValueTemplate();
		assertThat(keyValueTemplate.getApiVersion()).isEqualTo(VaultKeyValueOperationsSupport.KeyValueBackend.KV_2);
		verify(vaultTemplateBuilder).build(properties);
		verifyNoMoreInteractions(clientConfiguration, vaultTemplateBuilder);
	}

	@SuppressWarnings("unchecked")
	private ObjectProvider<HttpServletRequest> mockHttpRequest() {
		ObjectProvider<HttpServletRequest> objectProvider = mock(ObjectProvider.class);
		when(objectProvider.getIfAvailable()).thenReturn(null);
		return objectProvider;
	}

}
