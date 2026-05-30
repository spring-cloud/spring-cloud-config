/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.config.server.environment.secretmanager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.services.cloudresourcemanager.v3.CloudResourceManager;
import com.google.api.services.cloudresourcemanager.v3.model.TestIamPermissionsRequest;
import com.google.api.services.cloudresourcemanager.v3.model.TestIamPermissionsResponse;
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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

public class GoogleSecretManagerV1AccessStrategy implements GoogleSecretManagerAccessStrategy {

	private final SecretManagerServiceClient client;

	private final RestTemplate rest;

	private final GoogleConfigProvider configProvider;

	private static final String APPLICATION_NAME = "spring-cloud-config-server";

	private static final String ACCESS_SECRET_PERMISSION = "secretmanager.versions.access";

	private static Log logger = LogFactory.getLog(GoogleSecretManagerV1AccessStrategy.class);

	private GcpProjectResolutionSupport projectResolutionSupport;

	/**
	 * Constructs a new instance of {@code GoogleSecretManagerV1AccessStrategy}. This
	 * constructor is deprecated and marked for removal.
	 * @param rest the {@code RestTemplate} instance used for making HTTP requests
	 * @param configProvider the {@code GoogleConfigProvider} instance providing
	 * configuration values
	 * @param serviceAccountFile the path to the Google Cloud service account JSON file
	 * @throws IOException if an I/O error occurs while reading the service account file
	 */
	@Deprecated(forRemoval = true)
	public GoogleSecretManagerV1AccessStrategy(RestTemplate rest, GoogleConfigProvider configProvider,
			String serviceAccountFile) throws IOException {
		if (StringUtils.hasText(serviceAccountFile)) {
			GoogleCredentials creds = GoogleCredentials.fromStream(new FileInputStream(new File(serviceAccountFile)));
			this.client = SecretManagerServiceClient.create(SecretManagerServiceSettings.newBuilder()
				.setCredentialsProvider(FixedCredentialsProvider.create(creds))
				.build());
		}
		else {
			this.client = SecretManagerServiceClient.create();
		}
		this.rest = rest;
		this.configProvider = configProvider;
	}

	/**
	 * Constructs a new instance of {@code GoogleSecretManagerV1AccessStrategy}.
	 * @param rest the RestTemplate instance used for making HTTP requests
	 * @param configProvider the GoogleConfigProvider instance providing configuration
	 * values
	 * @param serviceAccountFile the path to the Google Cloud service account JSON file.
	 * If this parameter is non-empty, credentials will be loaded using the specified
	 * file.
	 * @param gcpProjectResolutionSupport the GcpProjectResolutionSupport instance
	 * providing support for resolving the Google Cloud project to use
	 * @throws IOException if an I/O error occurs while reading the service account file
	 */
	public GoogleSecretManagerV1AccessStrategy(RestTemplate rest, GoogleConfigProvider configProvider,
			String serviceAccountFile, GcpProjectResolutionSupport gcpProjectResolutionSupport) throws IOException {
		if (StringUtils.hasText(serviceAccountFile)) {
			GoogleCredentials creds = GoogleCredentials.fromStream(new FileInputStream(new File(serviceAccountFile)));
			this.client = SecretManagerServiceClient.create(SecretManagerServiceSettings.newBuilder()
				.setCredentialsProvider(FixedCredentialsProvider.create(creds))
				.build());
		}
		else {
			this.client = SecretManagerServiceClient.create();
		}
		this.rest = rest;
		this.configProvider = configProvider;
		this.projectResolutionSupport = gcpProjectResolutionSupport;
	}

	/**
	 * Constructor for the GoogleSecretManagerV1AccessStrategy class. This constructor is
	 * deprecated and will be removed in a future release. Use a constructor that passes
	 * {@code GcpProjectResolutionSupport}.
	 * @param rest the RestTemplate instance used for making HTTP requests
	 * @param configProvider the GoogleConfigProvider instance providing configuration
	 * values
	 * @param client the SecretManagerServiceClient instance used for interacting with
	 * Google Secret Manager
	 */
	@Deprecated(forRemoval = true)
	public GoogleSecretManagerV1AccessStrategy(RestTemplate rest, GoogleConfigProvider configProvider,
			SecretManagerServiceClient client) {
		this.client = client;
		this.rest = rest;
		this.configProvider = configProvider;
	}

	/**
	 * Constructs a new instance of {@code GoogleSecretManagerV1AccessStrategy}.
	 * @param rest the {@code RestTemplate} instance used for making HTTP requests
	 * @param configProvider the {@code GoogleConfigProvider} instance providing
	 * configuration values
	 * @param client the {@code SecretManagerServiceClient} instance used for interacting
	 * with Google Secret Manager
	 * @param projectResolutionSupport the {@code GcpProjectResolutionSupport} instance
	 * providing support for resolving the Google Cloud project to use
	 */
	public GoogleSecretManagerV1AccessStrategy(RestTemplate rest, GoogleConfigProvider configProvider,
			SecretManagerServiceClient client, GcpProjectResolutionSupport projectResolutionSupport) {
		this.client = client;
		this.rest = rest;
		this.configProvider = configProvider;
		this.projectResolutionSupport = projectResolutionSupport;
	}

	@Override
	public List<Secret> getSecrets() {
		String projectId = getProjectId();
		if (projectId == null) {
			return Collections.emptyList();
		}
		// Build the parent name.
		ProjectName project = ProjectName.of(projectId);

		// Create the request.
		ListSecretsRequest listSecretRequest = ListSecretsRequest.newBuilder().setParent(project.toString()).build();

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
		ListSecretVersionsRequest listVersionRequest = ListSecretVersionsRequest.newBuilder()
			.setParent(parent.toString())
			.build();

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
			if ((secretVersion.getState().getNumber() == SecretVersion.State.ENABLED_VALUE)
					&& comparator.compare(secretVersion, winner) > 0) {
				winner = secretVersion;
			}
		}
		if (winner != null) {
			SecretVersionName name = SecretVersionName.parse(winner.getName());
			// Access the secret version.
			AccessSecretVersionRequest request = AccessSecretVersionRequest.newBuilder()
				.setName(name.toString())
				.build();
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
			HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credential);
			service = new CloudResourceManager.Builder(GoogleNetHttpTransport.newTrustedTransport(),
					GsonFactory.getDefaultInstance(), requestInitializer)
				.setApplicationName(APPLICATION_NAME)
				.build();
			List<String> permissionsList = Arrays.asList(ACCESS_SECRET_PERMISSION);

			TestIamPermissionsRequest requestBody = new TestIamPermissionsRequest().setPermissions(permissionsList);

			String projectId = getProjectId();
			if (projectId == null) {
				return Boolean.FALSE;
			}
			TestIamPermissionsResponse testIamPermissionsResponse = service.projects()
				.testIamPermissions(projectId, requestBody)
				.execute();

			if (testIamPermissionsResponse.getPermissions() != null && testIamPermissionsResponse.size() >= 1) {
				return Boolean.TRUE;
			}
			else {
				logger.warn("Access token has no permissions to access secrets in project");
				return Boolean.FALSE;
			}
		}
		catch (Exception e) {
			logger.info("Unable to check token permissions", e);
			return Boolean.FALSE;
		}
	}

	private String getAccessToken() {
		return configProvider.getValue(HttpHeaderGoogleConfigProvider.ACCESS_TOKEN_HEADER, true);
	}

	/**
	 * @return the project id
	 */
	private String getProjectId() {
		if (projectResolutionSupport != null) {
			String project = projectResolutionSupport.resolve(configProvider, rest);
			if (project != null) {
				return project;
			}
		}
		logger.warn(
				"Unable to resolve project id.  This could be because you are not passing GcpProjectResolutionSupport to GoogleSecretManagerV1AccessStrategy,the X-Project-ID header was not set in the request or the project ID from the header is not configured in the project allow list, the project ID could not be retrieved via http://metadata.google.internal/computeMetadata/v1/project/project-id, or no default project ID was set in the configuration.");
		return null;
	}

	private static HttpHeaders getMetadataHttpHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Metadata-Flavor", "Google");
		return headers;
	}

}
