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

import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod;
import org.springframework.cloud.config.server.environment.vault.SpringVaultClientAuthenticationProvider;
import org.springframework.util.Assert;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.CubbyholeAuthentication;
import org.springframework.vault.authentication.CubbyholeAuthenticationOptions;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestOperations;

public class CubbyholeClientAuthenticationProvider
		extends SpringVaultClientAuthenticationProvider {

	public CubbyholeClientAuthenticationProvider() {
		super(AuthenticationMethod.CUBBYHOLE);
	}

	@Override
	public ClientAuthentication getClientAuthentication(
			VaultEnvironmentProperties vaultProperties,
			RestOperations vaultRestOperations, RestOperations externalRestOperations) {

		String token = vaultProperties.getToken();

		Assert.hasText(token,
				missingPropertyForAuthMethod("token", AuthenticationMethod.CUBBYHOLE));

		CubbyholeAuthenticationOptions options = CubbyholeAuthenticationOptions.builder() //
				.wrapped() //
				.initialToken(VaultToken.of(token)) //
				.build();

		return new CubbyholeAuthentication(options, vaultRestOperations);
	}

}
