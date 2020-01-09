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
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.config.server.environment.ConfigTokenProvider;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod;
import org.springframework.core.io.Resource;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.client.RestTemplateBuilder;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.client.VaultEndpointProvider;
import org.springframework.vault.config.AbstractVaultConfiguration;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * This class is adapted from
 * {@link org.springframework.vault.config.EnvironmentVaultConfiguration} and <a href=
 * https://github.com/spring-cloud/spring-cloud-vault/blob/master/spring-cloud-vault-config/src/main/java/org/springframework/cloud/vault/config/ClientAuthenticationFactory.java>
 * org.springframework.cloud.vault.config.ClientAuthenticationFactory</a> in order to
 * provide configuration consistent with Spring Cloud Vault's property-based
 * configuration.
 *
 * @author Scott Frederick
 */
public class SpringVaultClientConfiguration extends AbstractVaultConfiguration {

	private static final String VAULT_PROPERTIES_PREFIX = "spring.cloud.config.server.vault.";

	private final VaultEnvironmentProperties vaultProperties;

	private final ConfigTokenProvider configTokenProvider;

	private final RestOperations externalRestOperations;

	private final Log log = LogFactory.getLog(getClass());

	private final List<SpringVaultClientAuthenticationProvider> authProviders;

	public SpringVaultClientConfiguration(VaultEnvironmentProperties vaultProperties,
			ConfigTokenProvider configTokenProvider,
			List<SpringVaultClientAuthenticationProvider> authProviders) {

		this.vaultProperties = vaultProperties;
		this.configTokenProvider = configTokenProvider;
		this.authProviders = authProviders;

		this.externalRestOperations = new RestTemplate(
				clientHttpRequestFactoryWrapper().getClientHttpRequestFactory());
	}

	@Override
	public VaultEndpoint vaultEndpoint() {

		URI baseUrl = UriComponentsBuilder.newInstance()
				.scheme(vaultProperties.getScheme()).host(vaultProperties.getHost())
				.port(vaultProperties.getPort()).build().toUri();

		return VaultEndpoint.from(baseUrl);
	}

	@Override
	protected RestTemplateBuilder restTemplateBuilder(
			VaultEndpointProvider endpointProvider,
			ClientHttpRequestFactory requestFactory) {

		RestTemplateBuilder restTemplateBuilder = super.restTemplateBuilder(
				endpointProvider, requestFactory);

		if (vaultProperties.getNamespace() != null) {
			restTemplateBuilder.customizers(
					restTemplate -> restTemplate.getInterceptors().add(VaultClients
							.createNamespaceInterceptor(vaultProperties.getNamespace())));
		}

		return restTemplateBuilder;
	}

	@Override
	public SslConfiguration sslConfiguration() {
		if (vaultProperties.isSkipSslValidation()) {
			log.warn("The '" + VAULT_PROPERTIES_PREFIX + "skipSslValidation' property "
					+ "is not supported by this Vault environment repository implementation. "
					+ "Use the '" + VAULT_PROPERTIES_PREFIX
					+ "ssl` properties to provide "
					+ "custom keyStore and trustStore material instead.");
		}

		VaultEnvironmentProperties.Ssl ssl = vaultProperties.getSsl();

		SslConfiguration.KeyStoreConfiguration keyStoreConfiguration = getKeyStoreConfiguration(
				ssl.getKeyStore(), ssl.getKeyStorePassword());

		SslConfiguration.KeyStoreConfiguration trustStoreConfiguration = getKeyStoreConfiguration(
				ssl.getTrustStore(), ssl.getTrustStorePassword());

		return new SslConfiguration(keyStoreConfiguration, trustStoreConfiguration);
	}

	private SslConfiguration.KeyStoreConfiguration getKeyStoreConfiguration(
			Resource resourceProperty, String passwordProperty) {

		if (resourceProperty == null) {
			return SslConfiguration.KeyStoreConfiguration.unconfigured();
		}

		if (StringUtils.hasText(passwordProperty)) {
			return SslConfiguration.KeyStoreConfiguration.of(resourceProperty,
					passwordProperty.toCharArray());
		}

		return SslConfiguration.KeyStoreConfiguration.of(resourceProperty);
	}

	/**
	 * @return a new {@link ClientAuthentication}.
	 */
	public ClientAuthentication clientAuthentication() {

		AuthenticationMethod authentication = this.vaultProperties.getAuthentication();

		if (authentication == null) {
			return new ConfigTokenProviderAuthentication(this.configTokenProvider);
		}

		if (this.authProviders == null || this.authProviders.isEmpty()) {
			throw new UnsupportedOperationException(
					"No Vault client authentication providers are configured");
		}

		for (SpringVaultClientAuthenticationProvider authProvider : this.authProviders) {
			if (authProvider.supports(this.vaultProperties)) {
				return authProvider.getClientAuthentication(this.vaultProperties,
						restOperations(), this.externalRestOperations);
			}
		}

		throw new UnsupportedOperationException(
				String.format("Client authentication %s not supported", authentication));
	}

	static class ConfigTokenProviderAuthentication implements ClientAuthentication {

		private final ConfigTokenProvider tokenProvider;

		ConfigTokenProviderAuthentication(ConfigTokenProvider tokenProvider) {
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
