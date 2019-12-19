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
import org.springframework.util.StringUtils;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.GcpComputeAuthentication;
import org.springframework.vault.authentication.GcpComputeAuthenticationOptions;
import org.springframework.web.client.RestOperations;

public class GcpGceClientAuthenticationProvider
		extends SpringVaultClientAuthenticationProvider {

	public GcpGceClientAuthenticationProvider() {
		super(AuthenticationMethod.GCP_GCE);
	}

	@Override
	public ClientAuthentication getClientAuthentication(
			VaultEnvironmentProperties vaultProperties,
			RestOperations vaultRestOperations, RestOperations externalRestOperations) {

		VaultEnvironmentProperties.GcpGceProperties gcp = vaultProperties.getGcpGce();

		Assert.hasText(gcp.getRole(), missingPropertyForAuthMethod("gcp-iam.role",
				AuthenticationMethod.GCP_GCE));

		GcpComputeAuthenticationOptions.GcpComputeAuthenticationOptionsBuilder builder = GcpComputeAuthenticationOptions
				.builder().path(gcp.getGcpPath()).role(gcp.getRole());

		if (StringUtils.hasText(gcp.getServiceAccount())) {
			builder.serviceAccount(gcp.getServiceAccount());
		}

		return new GcpComputeAuthentication(builder.build(), vaultRestOperations,
				externalRestOperations);
	}

}
