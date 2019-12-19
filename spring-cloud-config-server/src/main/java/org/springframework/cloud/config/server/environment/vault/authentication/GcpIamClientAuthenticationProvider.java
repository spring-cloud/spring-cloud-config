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

import java.io.ByteArrayInputStream;
import java.util.Base64;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;

import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod;
import org.springframework.cloud.config.server.environment.vault.SpringVaultClientAuthenticationProvider;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.GcpCredentialSupplier;
import org.springframework.vault.authentication.GcpIamAuthentication;
import org.springframework.vault.authentication.GcpIamAuthenticationOptions;
import org.springframework.web.client.RestOperations;

public class GcpIamClientAuthenticationProvider
		extends SpringVaultClientAuthenticationProvider {

	public GcpIamClientAuthenticationProvider() {
		super(AuthenticationMethod.GCP_IAM);
	}

	@Override
	public ClientAuthentication getClientAuthentication(
			VaultEnvironmentProperties vaultProperties,
			RestOperations vaultRestOperations, RestOperations externalRestOperations) {

		assertClassPresent(
				"com.google.api.client.googleapis.auth.oauth2.GoogleCredential",
				missingClassForAuthMethod("GoogleCredential", "google-api-client",
						AuthenticationMethod.GCP_IAM));

		VaultEnvironmentProperties.GcpIamProperties gcp = vaultProperties.getGcpIam();

		Assert.hasText(gcp.getRole(), missingPropertyForAuthMethod("gcp-iam.role",
				AuthenticationMethod.GCP_IAM));

		GcpIamAuthenticationOptions.GcpIamAuthenticationOptionsBuilder builder = GcpIamAuthenticationOptions
				.builder().path(gcp.getGcpPath()).role(gcp.getRole())
				.jwtValidity(gcp.getJwtValidity());

		if (StringUtils.hasText(gcp.getProjectId())) {
			builder.projectId(gcp.getProjectId());
		}

		if (StringUtils.hasText(gcp.getServiceAccountId())) {
			builder.serviceAccountId(gcp.getServiceAccountId());
		}

		GcpCredentialSupplier supplier = GcpCredentialProvider.getGoogleCredential(gcp);
		builder.credential(supplier.get());

		GcpIamAuthenticationOptions options = builder.build();

		return new GcpIamAuthentication(options, vaultRestOperations);
	}

	@SuppressWarnings("deprecation")
	private static class GcpCredentialProvider {

		public static GcpCredentialSupplier getGoogleCredential(
				VaultEnvironmentProperties.GcpIamProperties gcp) {
			return () -> {

				VaultEnvironmentProperties.GcpCredentials credentialProperties = gcp
						.getCredentials();
				if (credentialProperties.getLocation() != null) {
					return GoogleCredential.fromStream(
							credentialProperties.getLocation().getInputStream());
				}

				if (StringUtils.hasText(credentialProperties.getEncodedKey())) {
					return GoogleCredential.fromStream(new ByteArrayInputStream(Base64
							.getDecoder().decode(credentialProperties.getEncodedKey())));
				}

				return GoogleCredential.getApplicationDefault();
			};
		}

	}

}
