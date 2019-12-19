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

import java.net.URI;

import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod;
import org.springframework.cloud.config.server.environment.vault.SpringVaultClientAuthenticationProvider;
import org.springframework.util.StringUtils;
import org.springframework.vault.authentication.AwsEc2Authentication;
import org.springframework.vault.authentication.AwsEc2AuthenticationOptions;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.web.client.RestOperations;

public class AwsEc2ClientAuthenticationProvider
		extends SpringVaultClientAuthenticationProvider {

	public AwsEc2ClientAuthenticationProvider() {
		super(AuthenticationMethod.AWS_EC2);
	}

	@Override
	public ClientAuthentication getClientAuthentication(
			VaultEnvironmentProperties vaultProperties,
			RestOperations vaultRestOperations, RestOperations externalRestOperations) {

		VaultEnvironmentProperties.AwsEc2Properties awsEc2 = vaultProperties.getAwsEc2();

		AwsEc2AuthenticationOptions.Nonce nonce = StringUtils.hasText(awsEc2.getNonce())
				? AwsEc2AuthenticationOptions.Nonce
						.provided(awsEc2.getNonce().toCharArray())
				: AwsEc2AuthenticationOptions.Nonce.generated();

		AwsEc2AuthenticationOptions authenticationOptions = AwsEc2AuthenticationOptions
				.builder().role(awsEc2.getRole()) //
				.path(awsEc2.getAwsEc2Path()) //
				.nonce(nonce) //
				.identityDocumentUri(URI.create(awsEc2.getIdentityDocument())) //
				.build();

		return new AwsEc2Authentication(authenticationOptions, vaultRestOperations,
				externalRestOperations);
	}

}
