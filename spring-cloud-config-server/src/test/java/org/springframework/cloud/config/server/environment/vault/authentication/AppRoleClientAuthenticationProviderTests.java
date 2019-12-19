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

import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.RoleId;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.SecretId;
import org.springframework.vault.support.VaultToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppRoleClientAuthenticationProviderTests {

	@Test
	public void appRoleRoleIdProvidedSecretIdProvided() {

		VaultEnvironmentProperties properties = new VaultEnvironmentProperties();
		properties.getAppRole().setRoleId("foo");
		properties.getAppRole().setSecretId("bar");

		AppRoleAuthenticationOptions options = AppRoleClientAuthenticationProvider
				.getAppRoleAuthenticationOptions(properties);

		assertThat(options.getRoleId()).isInstanceOf(RoleId.provided("foo").getClass());
		assertThat(options.getSecretId())
				.isInstanceOf(SecretId.provided("bar").getClass());
	}

	@Test
	public void appRoleRoleIdProvidedSecretIdAbsent() {

		VaultEnvironmentProperties properties = new VaultEnvironmentProperties();
		properties.getAppRole().setRoleId("foo");

		AppRoleAuthenticationOptions options = AppRoleClientAuthenticationProvider
				.getAppRoleAuthenticationOptions(properties);

		assertThat(options.getRoleId()).isInstanceOf(RoleId.provided("foo").getClass());
		assertThat(options.getSecretId()).isInstanceOf(SecretId.absent().getClass());
	}

	@Test
	public void appRoleRoleIdProvidedSecretIdPull() {

		VaultEnvironmentProperties properties = new VaultEnvironmentProperties();
		properties.setToken("token");
		properties.getAppRole().setRoleId("foo");
		properties.getAppRole().setRole("my-role");

		AppRoleAuthenticationOptions options = AppRoleClientAuthenticationProvider
				.getAppRoleAuthenticationOptions(properties);

		assertThat(options.getAppRole()).isEqualTo("my-role");
		assertThat(options.getRoleId()).isInstanceOf(RoleId.provided("foo").getClass());
		assertThat(options.getSecretId())
				.isInstanceOf(SecretId.pull(VaultToken.of("token")).getClass());
	}

	@Test
	public void appRoleWithFullPull() {

		VaultEnvironmentProperties properties = new VaultEnvironmentProperties();
		properties.setToken("token");
		properties.getAppRole().setRole("my-role");

		AppRoleAuthenticationOptions options = AppRoleClientAuthenticationProvider
				.getAppRoleAuthenticationOptions(properties);

		assertThat(options.getAppRole()).isEqualTo("my-role");
		assertThat(options.getRoleId())
				.isInstanceOf(RoleId.pull(VaultToken.of("token")).getClass());
		assertThat(options.getSecretId())
				.isInstanceOf(SecretId.pull(VaultToken.of("token")).getClass());
	}

	@Test
	public void appRoleFullWrapped() {

		VaultEnvironmentProperties properties = new VaultEnvironmentProperties();
		properties.setToken("token");

		AppRoleAuthenticationOptions options = AppRoleClientAuthenticationProvider
				.getAppRoleAuthenticationOptions(properties);

		assertThat(options.getRoleId())
				.isInstanceOf(RoleId.wrapped(VaultToken.of("token")).getClass());
		assertThat(options.getSecretId())
				.isInstanceOf(SecretId.wrapped(VaultToken.of("token")).getClass());
	}

	@Test
	public void appRoleRoleIdWrappedSecretIdProvided() {

		VaultEnvironmentProperties properties = new VaultEnvironmentProperties();
		properties.setToken("token");
		properties.getAppRole().setSecretId("bar");

		AppRoleAuthenticationOptions options = AppRoleClientAuthenticationProvider
				.getAppRoleAuthenticationOptions(properties);

		assertThat(options.getRoleId())
				.isInstanceOf(RoleId.wrapped(VaultToken.of("token")).getClass());
		assertThat(options.getSecretId())
				.isInstanceOf(SecretId.provided("bar").getClass());
	}

	@Test
	public void appRoleRoleIdProvidedSecretIdWrapped() {

		VaultEnvironmentProperties properties = new VaultEnvironmentProperties();
		properties.setToken("token");
		properties.getAppRole().setRoleId("foo");

		AppRoleAuthenticationOptions options = AppRoleClientAuthenticationProvider
				.getAppRoleAuthenticationOptions(properties);

		assertThat(options.getRoleId()).isInstanceOf(RoleId.provided("foo").getClass());
		assertThat(options.getSecretId())
				.isInstanceOf(SecretId.wrapped(VaultToken.of("token")).getClass());
	}

	@Test
	public void appRoleWithUnconfiguredRoleId() {

		VaultEnvironmentProperties properties = new VaultEnvironmentProperties();

		assertThatThrownBy(() -> AppRoleClientAuthenticationProvider
				.getAppRoleAuthenticationOptions(properties))
						.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void appRoleWithUnconfiguredRoleIdIfRoleNameSet() {

		VaultEnvironmentProperties properties = new VaultEnvironmentProperties();
		properties.getAppRole().setRole("my-role");

		assertThatThrownBy(() -> AppRoleClientAuthenticationProvider
				.getAppRoleAuthenticationOptions(properties))
						.isInstanceOf(IllegalArgumentException.class);
	}

}
