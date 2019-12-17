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

import java.net.URI;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.config.server.environment.ConfigTokenProvider;
import org.springframework.cloud.config.server.environment.EnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.EnvironmentWatch;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties;
import org.springframework.util.StringUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.SimpleSessionManager;
import org.springframework.vault.client.RestTemplateBuilder;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author Dylan Roberts
 * @author Scott Frederick
 */
public class SpringVaultEnvironmentRepositoryFactory implements
		EnvironmentRepositoryFactory<SpringVaultEnvironmentRepository, VaultEnvironmentProperties> {

	private final ObjectProvider<HttpServletRequest> request;

	private final EnvironmentWatch watch;

	private final ConfigTokenProvider tokenProvider;

	public SpringVaultEnvironmentRepositoryFactory(
			ObjectProvider<HttpServletRequest> request, EnvironmentWatch watch,
			ConfigTokenProvider tokenProvider) {
		this.request = request;
		this.watch = watch;
		this.tokenProvider = tokenProvider;
	}

	@Override
	public SpringVaultEnvironmentRepository build(
			VaultEnvironmentProperties vaultProperties) {
		RestTemplateBuilder restTemplateBuilder = buildRestTemplateBuilder(
				vaultProperties);

		VaultTemplate vaultTemplate = buildVaultTemplate(restTemplateBuilder);

		VaultKeyValueOperations accessStrategy = buildVaultAccessStrategy(vaultProperties,
				vaultTemplate);

		return new SpringVaultEnvironmentRepository(this.request, this.watch,
				vaultProperties, accessStrategy);
	}

	private RestTemplateBuilder buildRestTemplateBuilder(
			VaultEnvironmentProperties vaultProperties) {
		URI baseUrl = UriComponentsBuilder.newInstance()
				.scheme(vaultProperties.getScheme()).host(vaultProperties.getHost())
				.port(vaultProperties.getPort()).build().toUri();

		RestTemplateBuilder restTemplateBuilder = RestTemplateBuilder.builder()
				.endpoint(VaultEndpoint.from(baseUrl));

		if (vaultProperties.getNamespace() != null) {
			restTemplateBuilder.customizers(
					restTemplate -> restTemplate.getInterceptors().add(VaultClients
							.createNamespaceInterceptor(vaultProperties.getNamespace())));
		}

		return restTemplateBuilder;
	}

	private VaultTemplate buildVaultTemplate(RestTemplateBuilder restTemplateBuilder) {
		return new VaultTemplate(restTemplateBuilder, new SimpleSessionManager(
				new ConfigTokenProviderAuthentication(tokenProvider)));
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

	public static class ConfigTokenProviderAuthentication
			implements ClientAuthentication {

		private final ConfigTokenProvider tokenProvider;

		public ConfigTokenProviderAuthentication(ConfigTokenProvider tokenProvider) {
			this.tokenProvider = tokenProvider;
		}

		@Override
		public VaultToken login() throws VaultException {
			String token = tokenProvider.getToken();
			if (!StringUtils.hasLength(token)) {
				throw new IllegalArgumentException(
						"A Vault token must be supplied by a token provider");
			}
			return VaultToken.of(token);
		}

	}

}
