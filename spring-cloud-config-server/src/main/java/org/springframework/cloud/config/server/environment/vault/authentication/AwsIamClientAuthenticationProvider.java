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

package org.springframework.cloud.config.server.environment.vault.authentication;

import java.util.concurrent.atomic.AtomicReference;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod;
import org.springframework.cloud.config.server.environment.vault.SpringVaultClientAuthenticationProvider;
import org.springframework.util.StringUtils;
import org.springframework.vault.authentication.AwsIamAuthentication;
import org.springframework.vault.authentication.AwsIamAuthenticationOptions;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.web.client.RestOperations;

public class AwsIamClientAuthenticationProvider extends SpringVaultClientAuthenticationProvider {

	public AwsIamClientAuthenticationProvider() {
		super(AuthenticationMethod.AWS_IAM);
	}

	@Override
	public ClientAuthentication getClientAuthentication(VaultEnvironmentProperties vaultProperties,
			RestOperations vaultRestOperations, RestOperations externalRestOperations) {

		assertClassPresent("software.amazon.awssdk.auth.credentials.AwsCredentials",
				missingClassForAuthMethod("AwsCredentials", "aws-core", AuthenticationMethod.AWS_IAM));

		VaultEnvironmentProperties.AwsIamProperties awsIam = vaultProperties.getAwsIam();

		AwsCredentialsProvider credentialsProvider = AwsCredentialProvider.getAwsCredentialsProvider();

		AwsIamAuthenticationOptions.AwsIamAuthenticationOptionsBuilder builder = AwsIamAuthenticationOptions.builder();

		if (StringUtils.hasText(awsIam.getRole())) {
			builder.role(awsIam.getRole());
		}

		if (StringUtils.hasText(awsIam.getServerName())) {
			builder.serverName(awsIam.getServerName());
		}

		if (awsIam.getEndpointUri() != null) {
			builder.endpointUri(awsIam.getEndpointUri());
		}

		builder.path(awsIam.getAwsPath()) //
				.credentialsProvider(credentialsProvider);

		AwsIamAuthenticationOptions options = builder.credentialsProvider(credentialsProvider).build();

		return new AwsIamAuthentication(options, vaultRestOperations);
	}

	private static class AwsCredentialProvider {

		private static AwsCredentialsProvider getAwsCredentialsProvider() {

			DefaultCredentialsProvider backingCredentialsProvider = DefaultCredentialsProvider.create();

			// Eagerly fetch credentials preventing lag during the first, actual login.
			AwsCredentials firstAccess = backingCredentialsProvider.resolveCredentials();

			AtomicReference<AwsCredentials> once = new AtomicReference<>(firstAccess);

			return new AwsCredentialsProvider() {

				@Override
				public AwsCredentials resolveCredentials() {

					if (once.compareAndSet(firstAccess, null)) {
						return firstAccess;
					}

					return backingCredentialsProvider.resolveCredentials();
				}

			};
		}

	}

}
