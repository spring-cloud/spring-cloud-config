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

package org.springframework.cloud.config.server.environment;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.google.cloud.secretmanager.v1.AccessSecretVersionRequest;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.ListSecretVersionsRequest;
import com.google.cloud.secretmanager.v1.ListSecretsRequest;
import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretName;
import com.google.cloud.secretmanager.v1.SecretVersion;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.environment.secretManager.GoogleConfigProvider;
import org.springframework.cloud.config.server.environment.secretManager.HttpHeaderGoogleConfigProvider;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * @author Jose Maria Alvarez
 */
public class GoogleSecretManagerEnvironmentRepository implements EnvironmentRepository {

	private RestTemplate rest;

	private final GoogleConfigProvider configProvider;

	private String applicationLabel;

	private String profileLabel;

	private static Log logger = LogFactory
			.getLog(GoogleSecretManagerEnvironmentRepository.class);

	public GoogleSecretManagerEnvironmentRepository(
			ObjectProvider<HttpServletRequest> request, RestTemplate rest,
			GoogleSecretManagerEnvironmentProperties properties) {
		this.rest = rest;
		this.configProvider = new HttpHeaderGoogleConfigProvider(request);
		this.applicationLabel = properties.getApplicationLabel();
		this.profileLabel = properties.getProfileLabel();
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		if (StringUtils.isEmpty(label)) {
			label = "master";
		}
		if (StringUtils.isEmpty(profile)) {
			profile = "default";
		}
		if (!profile.startsWith("default")) {
			profile = "default," + profile;
		}
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		Environment result = new Environment(application, profile, label, null, null);
		for (String profileUnit : profiles) {
			Map<?, ?> secrets = getSecrets(application, profileUnit);
			if (!secrets.isEmpty()) {
				result.add(new PropertySource("gsm:" + application + "-" + profileUnit,
						secrets));
			}
		}
		return result;
	}

	/**
	 * @return
	 */
	private String getProjectId() {
		String result = null;
		try {
			HttpEntity<String> entity = new HttpEntity<String>("parameters",
					getMetadataHttpHeaders());
			result = rest.exchange(
					GoogleSecretManagerEnvironmentProperties.GOOGLE_METADATA_PROJECT_URL,
					HttpMethod.GET, entity, String.class).getBody();
		}
		catch (Exception e) {
			// not in GCP
			result = configProvider
					.getValue(HttpHeaderGoogleConfigProvider.PROJECT_ID_HEADER);
		}
		return result;
	}

	private static HttpHeaders getMetadataHttpHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Metadata-Flavor", "Google");
		return headers;
	}

	/**
	 * @param application the application name
	 * @param profile the profile name
	 * @return the properties to add into the environment
	 */
	private Map<?, ?> getSecrets(String application, String profile) {
		String projectId = getProjectId();
		logger.info("Adding secrets for project " + projectId);
		Map<String, String> result = new HashMap<>();
		// Initialize client that will be used to send requests. This client only needs to
		// be created
		// once, and can be reused for multiple requests. After completing all of your
		// requests, call
		// the "close" method on the client to safely clean up any remaining background
		// resources.
		try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
			// Build the parent name.
			ProjectName project = ProjectName.of(projectId);

			// Create the request.
			ListSecretsRequest listSecretRequest = ListSecretsRequest.newBuilder()
					.setParent(project.toString()).build();

			// Get all secrets.
			SecretManagerServiceClient.ListSecretsPagedResponse pagedListSecretResponse = client
					.listSecrets(listSecretRequest);

			// List all secrets.
			for (Secret secret : pagedListSecretResponse.iterateAll()) {
				if (secret.getLabelsOrDefault(applicationLabel, "application")
						.equalsIgnoreCase(application)
						&& secret.getLabelsOrDefault(profileLabel, "default")
								.equalsIgnoreCase(profile)) {
					// Build the parent name.
					SecretName parent = SecretName.parse(secret.getName());

					// Create the request.
					ListSecretVersionsRequest listVersionRequest = ListSecretVersionsRequest
							.newBuilder().setParent(parent.toString()).build();

					// Get all versions.
					SecretManagerServiceClient.ListSecretVersionsPagedResponse pagedListVersionResponse = client
							.listSecretVersions(listVersionRequest);
					String secretVersionId = null;
					String secretValue = null;
					for (SecretVersion secretVersion : pagedListVersionResponse
							.iterateAll()) {
						if ((secretVersion.getState()
								.getNumber() == SecretVersion.State.ENABLED_VALUE)
								&& (secretVersionId == null || secretVersionId
										.compareTo(secretVersion.getName()) < 1)) {
							secretValue = getSecretVersion(secretVersion.getName());
							secretVersionId = secretVersion.getName();
						}
					}
					result.put(parent.getSecret(), secretValue);
				}
			}
		}
		catch (IOException e) {
			logger.error("Error getting Google secrets", e);
		}
		return result;
	}

	/**
	 * @param versionId the google secret manager version id
	 * @return the value of the secret
	 * @throws IOException in case there is a communication error
	 */
	private String getSecretVersion(String versionId) throws IOException {
		// Initialize client that will be used to send requests. This client only needs to
		// be created
		// once, and can be reused for multiple requests. After completing all of your
		// requests, call
		// the "close" method on the client to safely clean up any remaining background
		// resources.
		try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
			// Build the name.
			SecretVersionName name = SecretVersionName.parse(versionId);

			// Access the secret version.
			AccessSecretVersionRequest request = AccessSecretVersionRequest.newBuilder()
					.setName(name.toString()).build();
			AccessSecretVersionResponse response = client.accessSecretVersion(request);

			// Print the secret payload.
			//
			// WARNING: Do not print the secret in a production environment - this
			// snippet is showing how to access the secret material.
			return response.getPayload().getData().toStringUtf8();
		}
	}

}
