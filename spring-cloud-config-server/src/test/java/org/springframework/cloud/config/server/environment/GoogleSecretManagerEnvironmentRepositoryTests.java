/*
 * Copyright 2016-2020 the original author or authors.
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
import java.util.ArrayList;
import java.util.List;

import com.google.cloud.secretmanager.v1.AccessSecretVersionRequest;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.ListSecretVersionsRequest;
import com.google.cloud.secretmanager.v1.ListSecretsRequest;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersion;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import org.springframework.cloud.config.server.environment.secretmanager.GoogleConfigProvider;
import org.springframework.cloud.config.server.environment.secretmanager.GoogleSecretComparatorByVersion;
import org.springframework.cloud.config.server.environment.secretmanager.GoogleSecretManagerAccessStrategyFactory;
import org.springframework.cloud.config.server.environment.secretmanager.GoogleSecretManagerV1AccessStrategy;
import org.springframework.cloud.config.server.environment.secretmanager.HttpHeaderGoogleConfigProvider;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GoogleSecretManagerEnvironmentRepositoryTests {

	@Test
	public void testSupportedStrategy() {
		GoogleSecretManagerEnvironmentProperties properties = new GoogleSecretManagerEnvironmentProperties();
		SecretManagerServiceClient mock = mock(SecretManagerServiceClient.class);
		properties.setVersion(1);
		assertThat(GoogleSecretManagerAccessStrategyFactory.forVersion(null, null, properties,
				mock) instanceof GoogleSecretManagerV1AccessStrategy).isTrue();
	}

	@Test
	public void testGetUnsupportedStrategy() {
		assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
			GoogleSecretManagerEnvironmentProperties properties = new GoogleSecretManagerEnvironmentProperties();
			SecretManagerServiceClient mock = mock(SecretManagerServiceClient.class);
			properties.setVersion(2);
			GoogleSecretManagerAccessStrategyFactory.forVersion(null, null, properties, mock);
		});
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testGetSecrets() throws IOException {
		RestTemplate rest = mock(RestTemplate.class);
		GoogleConfigProvider provider = mock(HttpHeaderGoogleConfigProvider.class);
		when(provider.getValue(HttpHeaderGoogleConfigProvider.PROJECT_ID_HEADER, true)).thenReturn("test-project");
		SecretManagerServiceClient mock = mock(SecretManagerServiceClient.class);
		SecretManagerServiceClient.ListSecretsPagedResponse response = mock(
				SecretManagerServiceClient.ListSecretsPagedResponse.class);
		Secret secret = Secret.newBuilder().setName("projects/test-project/secrets/test").build();
		List<Secret> secrets = new ArrayList<Secret>();
		secrets.add(secret);
		when(response.iterateAll()).thenReturn(secrets);
		Mockito.doReturn(response).when(mock).listSecrets(any(ListSecretsRequest.class));
		GoogleSecretManagerV1AccessStrategy strategy = new GoogleSecretManagerV1AccessStrategy(rest, provider, mock);
		assertThat(strategy.getSecrets()).hasSize(1);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testGetSecretValues() {
		RestTemplate rest = mock(RestTemplate.class);
		GoogleConfigProvider provider = mock(HttpHeaderGoogleConfigProvider.class);
		when(provider.getValue(HttpHeaderGoogleConfigProvider.PROJECT_ID_HEADER, true)).thenReturn("test-project");
		SecretManagerServiceClient mock = mock(SecretManagerServiceClient.class);
		SecretManagerServiceClient.ListSecretVersionsPagedResponse response = mock(
				SecretManagerServiceClient.ListSecretVersionsPagedResponse.class);
		SecretVersion secret1 = SecretVersion.newBuilder().setName("projects/test-project/secrets/test/versions/1")
				.setState(SecretVersion.State.ENABLED).build();
		SecretVersion secret2 = SecretVersion.newBuilder().setName("projects/test-project/secrets/test/versions/4")
				.setState(SecretVersion.State.ENABLED).build();
		SecretVersion secret3 = SecretVersion.newBuilder().setName("projects/test-project/secrets/test/versions/9")
				.setState(SecretVersion.State.ENABLED).build();
		SecretVersion secret4 = SecretVersion.newBuilder().setName("projects/test-project/secrets/test/versions/12")
				.setState(SecretVersion.State.ENABLED).build();
		List<SecretVersion> secrets = new ArrayList<SecretVersion>();
		secrets.add(secret1);
		secrets.add(secret2);
		secrets.add(secret3);
		secrets.add(secret4);
		when(response.iterateAll()).thenReturn(secrets);
		Mockito.doReturn(response).when(mock).listSecretVersions(any(ListSecretVersionsRequest.class));
		GoogleSecretManagerV1AccessStrategy strategy = new GoogleSecretManagerV1AccessStrategy(rest, provider, mock);
		AccessSecretVersionResponse accessSecretVersionResponse = mock(AccessSecretVersionResponse.class);
		SecretPayload payload = mock(SecretPayload.class);
		ByteString data = mock(ByteString.class);
		when(accessSecretVersionResponse.getPayload()).thenReturn(payload);
		when(payload.getData()).thenReturn(data);
		when(data.toStringUtf8()).thenReturn("test-value");
		ArgumentMatcher<AccessSecretVersionRequest> matcher = new ArgumentMatcher<AccessSecretVersionRequest>() {
			@Override
			public boolean matches(AccessSecretVersionRequest accessSecretVersionRequest) {
				if (accessSecretVersionRequest.getName().equals("projects/test-project/secrets/test/versions/12")) {
					return true;
				}
				return false;
			}
		};
		Mockito.doReturn(accessSecretVersionResponse).when(mock).accessSecretVersion(ArgumentMatchers.argThat(matcher));
		assertThat(strategy.getSecretValue(Secret.newBuilder().setName("projects/test-project/secrets/test").build(),
				new GoogleSecretComparatorByVersion())).isEqualTo("test-value");
	}

}
