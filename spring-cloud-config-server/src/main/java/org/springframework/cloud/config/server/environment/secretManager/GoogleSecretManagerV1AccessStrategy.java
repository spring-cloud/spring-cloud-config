/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.config.server.environment.secretManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.services.cloudresourcemanager.CloudResourceManager;
import com.google.api.services.cloudresourcemanager.model.TestIamPermissionsRequest;
import com.google.api.services.cloudresourcemanager.model.TestIamPermissionsResponse;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.secretmanager.v1.AccessSecretVersionRequest;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.ListSecretVersionsRequest;
import com.google.cloud.secretmanager.v1.ListSecretsRequest;
import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import com.google.cloud.secretmanager.v1.SecretName;
import com.google.cloud.secretmanager.v1.SecretVersion;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.config.server.environment.GoogleSecretManagerEnvironmentProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

public class GoogleSecretManagerV1AccessStrategy
		implements GoogleSecretManagerAccessStrategy {

	private final SecretManagerServiceClient client;

	private final RestTemplate rest;

	private final GoogleConfigProvider configProvider;

	private static final String APPLICATION_NAME = "spring-cloud-config-server";

	private static final String ACCESS_SECRET_PERMISSION = "secretmanager.versions.access";

	private static Log logger = LogFactory
			.getLog(GoogleSecretManagerV1AccessStrategy.class);

	public GoogleSecretManagerV1AccessStrategy(RestTemplate rest,
			GoogleConfigProvider configProvider, String serviceAccountFile)
			throws IOException {
		if (StringUtils.isNotEmpty(serviceAccountFile)) {
			GoogleCredentials creds = GoogleCredentials
					.fromStream(new FileInputStream(new File(serviceAccountFile)));
			this.client = SecretManagerServiceClient.create(SecretManagerServiceSettings
					.newBuilder()
					.setCredentialsProvider(FixedCredentialsProvider.create(creds))
					.build());
		}
		else {
			this.client = SecretManagerServiceClient.create();
		}
		this.rest = rest;
		this.configProvider = configProvider;
	}

	public GoogleSecretManagerV1AccessStrategy(RestTemplate rest,
			GoogleConfigProvider configProvider, SecretManagerServiceClient client)
			throws IOException {
		this.client = client;
		this.rest = rest;
		this.configProvider = configProvider;
	}

	@Override
	public List<Secret> getSecrets() {
		// Build the parent name.
		ProjectName project = ProjectName.of(getProjectId());

		// Create the request.
		ListSecretsRequest listSecretRequest = ListSecretsRequest.newBuilder()
				.setParent(project.toString()).build();

		// Get all secrets.
		SecretManagerServiceClient.ListSecretsPagedResponse pagedListSecretResponse = client
				.listSecrets(listSecretRequest);

		List<Secret> result = new ArrayList<Secret>();
		pagedListSecretResponse.iterateAll().forEach(result::add);

		// List all secrets.
		return result;
	}

	private List<SecretVersion> getSecretVersions(Secret secret) {
		SecretName parent = SecretName.parse(secret.getName());

		// Create the request.
		ListSecretVersionsRequest listVersionRequest = ListSecretVersionsRequest
				.newBuilder().setParent(parent.toString()).build();

		// Get all versions.
		SecretManagerServiceClient.ListSecretVersionsPagedResponse pagedListVersionResponse = client
				.listSecretVersions(listVersionRequest);
		List<SecretVersion> result = new ArrayList<SecretVersion>();
		pagedListVersionResponse.iterateAll().forEach(result::add);
		return result;
	}

	@Override
	public String getSecretValue(Secret secret, Comparator<SecretVersion> comparator) {
		String result = null;
		List<SecretVersion> versions = getSecretVersions(secret);
		SecretVersion winner = null;
		for (SecretVersion secretVersion : versions) {
			if ((secretVersion.getState()
					.getNumber() == SecretVersion.State.ENABLED_VALUE)
					&& comparator.compare(secretVersion, winner) > 0) {
				winner = secretVersion;
			}
		}
		if (winner != null) {
			SecretVersionName name = SecretVersionName.parse(winner.getName());
			// Access the secret version.
			AccessSecretVersionRequest request = AccessSecretVersionRequest.newBuilder()
					.setName(name.toString()).build();
			AccessSecretVersionResponse response = client.accessSecretVersion(request);
			result = response.getPayload().getData().toStringUtf8();
		}
		return result;
	}

	@Override
	public String getSecretName(Secret secret) {
		SecretName parent = SecretName.parse(secret.getName());
		return parent.getSecret();
	}

	@Override
	public Boolean checkRemotePermissions() {
		CloudResourceManager service = null;
		try {
			AccessToken accessToken = new AccessToken(getAccessToken(), null);
			GoogleCredentials credential = new GoogleCredentials(accessToken);
			HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(
					credential);
			service = new CloudResourceManager.Builder(
					GoogleNetHttpTransport.newTrustedTransport(),
					JacksonFactory.getDefaultInstance(), requestInitializer)
							.setApplicationName(APPLICATION_NAME).build();
			List<String> permissionsList = Arrays.asList(ACCESS_SECRET_PERMISSION);

			TestIamPermissionsRequest requestBody = new TestIamPermissionsRequest()
					.setPermissions(permissionsList);

			TestIamPermissionsResponse testIamPermissionsResponse = service.projects()
					.testIamPermissions(getProjectId(), requestBody).execute();

			if (testIamPermissionsResponse.getPermissions() != null
					&& testIamPermissionsResponse.size() >= 1) {
				return Boolean.TRUE;
			}
			else {
				logger.warn(
						"Access token has no permissions to access secrets in project");
				return Boolean.FALSE;
			}
		}
		catch (Exception e) {
			logger.info("Unable to check token permissions", e);
			return Boolean.FALSE;
		}
	}

	private String getAccessToken() {
		return configProvider
				.getValue(HttpHeaderGoogleConfigProvider.ACCESS_TOKEN_HEADER);
	}

	/**
	 * @return
	 */
	private String getProjectId() {
		String result = null;
		try {
			result = configProvider
					.getValue(HttpHeaderGoogleConfigProvider.PROJECT_ID_HEADER);
		}
		catch (Exception e) {
			// not in GCP
			HttpEntity<String> entity = new HttpEntity<String>("parameters",
					getMetadataHttpHeaders());
			result = rest.exchange(
					GoogleSecretManagerEnvironmentProperties.GOOGLE_METADATA_PROJECT_URL,
					HttpMethod.GET, entity, String.class).getBody();
		}
		return result;
	}

	private static HttpHeaders getMetadataHttpHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Metadata-Flavor", "Google");
		return headers;
	}

}
