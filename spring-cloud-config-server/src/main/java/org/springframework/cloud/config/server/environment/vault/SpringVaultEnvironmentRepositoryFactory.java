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

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.config.server.environment.EnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.EnvironmentWatch;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport;
import org.springframework.vault.core.VaultTemplate;

/**
 * @author Dylan Roberts
 * @author Scott Frederick
 */
public class SpringVaultEnvironmentRepositoryFactory implements
		EnvironmentRepositoryFactory<SpringVaultEnvironmentRepository, VaultEnvironmentProperties> {

	private final ObjectProvider<HttpServletRequest> request;

	private final EnvironmentWatch watch;

	private final SpringVaultClientConfiguration clientConfiguration;

	public SpringVaultEnvironmentRepositoryFactory(
			ObjectProvider<HttpServletRequest> request, EnvironmentWatch watch,
			SpringVaultClientConfiguration clientConfiguration) {
		this.request = request;
		this.watch = watch;
		this.clientConfiguration = clientConfiguration;
	}

	@Override
	public SpringVaultEnvironmentRepository build(
			VaultEnvironmentProperties vaultProperties) {
		VaultTemplate vaultTemplate = clientConfiguration.vaultTemplate();

		VaultKeyValueOperations accessStrategy = buildVaultAccessStrategy(vaultProperties,
				vaultTemplate);

		return new SpringVaultEnvironmentRepository(this.request, this.watch,
				vaultProperties, accessStrategy);
	}

	private VaultKeyValueOperations buildVaultAccessStrategy(
			VaultEnvironmentProperties vaultProperties, VaultTemplate vaultTemplate) {
		String backend = vaultProperties.getBackend();
		int version = vaultProperties.getKvVersion();

		switch (version) {
		case 1:
			return vaultTemplate.opsForKeyValue(backend,
					VaultKeyValueOperationsSupport.KeyValueBackend.KV_1);
		case 2:
			return vaultTemplate.opsForKeyValue(backend,
					VaultKeyValueOperationsSupport.KeyValueBackend.KV_2);
		default:
			throw new IllegalArgumentException(
					"No support for given Vault k/v backend version " + version);
		}
	}

}
