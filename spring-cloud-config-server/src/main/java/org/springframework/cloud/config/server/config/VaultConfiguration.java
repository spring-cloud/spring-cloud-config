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

package org.springframework.cloud.config.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.config.server.environment.ConfigTokenProvider;
import org.springframework.cloud.config.server.environment.EnvironmentConfigTokenProvider;
import org.springframework.cloud.config.server.environment.vault.authentication.AppRoleClientAuthenticationProvider;
import org.springframework.cloud.config.server.environment.vault.authentication.AwsEc2ClientAuthenticationProvider;
import org.springframework.cloud.config.server.environment.vault.authentication.AwsIamClientAuthenticationProvider;
import org.springframework.cloud.config.server.environment.vault.authentication.AzureMsiClientAuthenticationProvider;
import org.springframework.cloud.config.server.environment.vault.authentication.CertificateClientAuthenticationProvider;
import org.springframework.cloud.config.server.environment.vault.authentication.CubbyholeClientAuthenticationProvider;
import org.springframework.cloud.config.server.environment.vault.authentication.GcpGceClientAuthenticationProvider;
import org.springframework.cloud.config.server.environment.vault.authentication.GcpIamClientAuthenticationProvider;
import org.springframework.cloud.config.server.environment.vault.authentication.KubernetesClientAuthenticationProvider;
import org.springframework.cloud.config.server.environment.vault.authentication.PcfClientAuthenticationProvider;
import org.springframework.cloud.config.server.environment.vault.authentication.TokenClientAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.vault.core.VaultTemplate;

/**
 * @author Scott Frederick
 */
@Configuration(proxyBeanMethods = false)
public class VaultConfiguration {

	private static final String VAULT_TOKEN_PROPERTY_NAME = "spring.cloud.config.server.vault.token";

	@Bean
	@ConditionalOnProperty(VAULT_TOKEN_PROPERTY_NAME)
	public ConfigTokenProvider configTokenProvider(Environment environment) {
		return new EnvironmentConfigTokenProvider(environment, VAULT_TOKEN_PROPERTY_NAME);
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(VaultTemplate.class)
	public static class VaultClientAuthenticationProviderConfiguration {

		@Bean
		public AppRoleClientAuthenticationProvider appRoleClientAuthenticationProvider() {
			return new AppRoleClientAuthenticationProvider();
		}

		@Bean
		public AwsEc2ClientAuthenticationProvider awsEc2ClientAuthenticationProvider() {
			return new AwsEc2ClientAuthenticationProvider();
		}

		@Bean
		public AwsIamClientAuthenticationProvider awsIamClientAuthenticationProvider() {
			return new AwsIamClientAuthenticationProvider();
		}

		@Bean
		public AzureMsiClientAuthenticationProvider azureMsiClientAuthenticationProvider() {
			return new AzureMsiClientAuthenticationProvider();
		}

		@Bean
		public CertificateClientAuthenticationProvider certificateClientAuthenticationProvider() {
			return new CertificateClientAuthenticationProvider();
		}

		@Bean
		public CubbyholeClientAuthenticationProvider cubbyholeClientAuthenticationProvider() {
			return new CubbyholeClientAuthenticationProvider();
		}

		@Bean
		public GcpGceClientAuthenticationProvider gcpGceClientAuthenticationProvider() {
			return new GcpGceClientAuthenticationProvider();
		}

		@Bean
		public GcpIamClientAuthenticationProvider gcpIamClientAuthenticationProvider() {
			return new GcpIamClientAuthenticationProvider();
		}

		@Bean
		public KubernetesClientAuthenticationProvider kubernetesClientAuthenticationProvider() {
			return new KubernetesClientAuthenticationProvider();
		}

		@Bean
		public PcfClientAuthenticationProvider pcfClientAuthenticationProvider() {
			return new PcfClientAuthenticationProvider();
		}

		@Bean
		public TokenClientAuthenticationProvider tokenClientAuthenticationProvider() {
			return new TokenClientAuthenticationProvider();
		}

	}

}
