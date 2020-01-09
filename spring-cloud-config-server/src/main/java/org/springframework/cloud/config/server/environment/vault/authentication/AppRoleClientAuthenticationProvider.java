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
import org.springframework.util.StringUtils;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestOperations;

public class AppRoleClientAuthenticationProvider
		extends SpringVaultClientAuthenticationProvider {

	public AppRoleClientAuthenticationProvider() {
		super(AuthenticationMethod.APPROLE);
	}

	@Override
	public ClientAuthentication getClientAuthentication(
			VaultEnvironmentProperties vaultProperties,
			RestOperations vaultRestOperations, RestOperations externalRestOperations) {

		AppRoleAuthenticationOptions options = getAppRoleAuthenticationOptions(
				vaultProperties);

		return new AppRoleAuthentication(options, vaultRestOperations);
	}

	static AppRoleAuthenticationOptions getAppRoleAuthenticationOptions(
			VaultEnvironmentProperties vaultProperties) {

		VaultEnvironmentProperties.AppRoleProperties appRole = vaultProperties
				.getAppRole();

		AppRoleAuthenticationOptions.AppRoleAuthenticationOptionsBuilder builder = AppRoleAuthenticationOptions
				.builder().path(appRole.getAppRolePath());

		if (StringUtils.hasText(appRole.getRole())) {
			builder.appRole(appRole.getRole());
		}

		AppRoleAuthenticationOptions.RoleId roleId = getRoleId(vaultProperties, appRole);
		AppRoleAuthenticationOptions.SecretId secretId = getSecretId(vaultProperties,
				appRole);

		builder.roleId(roleId).secretId(secretId);

		return builder.build();
	}

	private static AppRoleAuthenticationOptions.RoleId getRoleId(
			VaultEnvironmentProperties vaultProperties,
			VaultEnvironmentProperties.AppRoleProperties appRole) {

		if (StringUtils.hasText(appRole.getRoleId())) {
			return AppRoleAuthenticationOptions.RoleId.provided(appRole.getRoleId());
		}

		if (StringUtils.hasText(vaultProperties.getToken())
				&& StringUtils.hasText(appRole.getRole())) {
			return AppRoleAuthenticationOptions.RoleId
					.pull(VaultToken.of(vaultProperties.getToken()));
		}

		if (StringUtils.hasText(vaultProperties.getToken())) {
			return AppRoleAuthenticationOptions.RoleId
					.wrapped(VaultToken.of(vaultProperties.getToken()));
		}

		throw new IllegalArgumentException("Any of '" + VAULT_PROPERTIES_PREFIX
				+ "app-role.role-id', '.token', "
				+ "or '.app-role.role' and '.token' must be provided if the "
				+ AuthenticationMethod.APPROLE + " authentication method is specified.");
	}

	private static AppRoleAuthenticationOptions.SecretId getSecretId(
			VaultEnvironmentProperties vaultProperties,
			VaultEnvironmentProperties.AppRoleProperties appRole) {

		if (StringUtils.hasText(appRole.getSecretId())) {
			return AppRoleAuthenticationOptions.SecretId.provided(appRole.getSecretId());
		}

		if (StringUtils.hasText(vaultProperties.getToken())
				&& StringUtils.hasText(appRole.getRole())) {
			return AppRoleAuthenticationOptions.SecretId
					.pull(VaultToken.of(vaultProperties.getToken()));
		}

		if (StringUtils.hasText(vaultProperties.getToken())) {
			return AppRoleAuthenticationOptions.SecretId
					.wrapped(VaultToken.of(vaultProperties.getToken()));
		}

		return AppRoleAuthenticationOptions.SecretId.absent();
	}

}
