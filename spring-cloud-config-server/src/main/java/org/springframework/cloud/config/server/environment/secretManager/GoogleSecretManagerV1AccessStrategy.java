package org.springframework.cloud.config.server.environment.secretManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

import org.springframework.cloud.config.server.environment.GoogleSecretManagerEnvironmentProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

public class GoogleSecretManagerV1AccessStrategy implements GoogleSecretManagerAccessStrategy {

	private final SecretManagerServiceClient client;

	private final RestTemplate rest;

	private final GoogleConfigProvider configProvider;

	public GoogleSecretManagerV1AccessStrategy(RestTemplate rest, GoogleConfigProvider configProvider) throws IOException {
		this.client = SecretManagerServiceClient.create();
		this.rest = rest;
		this.configProvider = configProvider;
	}

	public GoogleSecretManagerV1AccessStrategy(RestTemplate rest, GoogleConfigProvider configProvider, SecretManagerServiceClient client) throws IOException {
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
			if ((secretVersion.getState().getNumber() == SecretVersion.State.ENABLED_VALUE) && comparator
				.compare(secretVersion, winner) > 0) {
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

}
