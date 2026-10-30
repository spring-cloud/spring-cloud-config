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

import java.util.ArrayList;
import java.util.List;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.Json;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.server.environment.GoogleSecretManagerEnvironmentProperties;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GoogleSecretManagerV1AccessStrategyTests {

	private static final String PROJECT_ID = "my-project";

	private final List<String> requestedUrls = new ArrayList<>();

	private GoogleConfigProvider configProvider;

	@BeforeEach
	void setUp() {
		configProvider = mock(GoogleConfigProvider.class);
		when(configProvider.getValue(HttpHeaderGoogleConfigProvider.ACCESS_TOKEN_HEADER, true)).thenReturn("token");
		when(configProvider.getValue(HttpHeaderGoogleConfigProvider.PROJECT_ID_HEADER, false)).thenReturn(PROJECT_ID);
	}

	@Test
	void checkRemotePermissionsUsesProjectsResourceName() {
		GoogleSecretManagerV1AccessStrategy strategy = strategy(
				"{\"permissions\":[\"secretmanager.versions.access\"]}");

		assertThat(strategy.checkRemotePermissions()).isTrue();
		assertThat(requestedUrls).singleElement()
			.asString()
			.endsWith("/v3/projects/" + PROJECT_ID + ":testIamPermissions");
	}

	@Test
	void checkRemotePermissionsReturnsFalseWithoutPermission() {
		GoogleSecretManagerV1AccessStrategy strategy = strategy("{}");

		assertThat(strategy.checkRemotePermissions()).isFalse();
		assertThat(requestedUrls).singleElement()
			.asString()
			.endsWith("/v3/projects/" + PROJECT_ID + ":testIamPermissions");
	}

	private GoogleSecretManagerV1AccessStrategy strategy(String responseBody) {
		HttpTransport transport = new MockHttpTransport() {
			@Override
			public LowLevelHttpRequest buildRequest(String method, String url) {
				requestedUrls.add(url);
				return new MockLowLevelHttpRequest(url) {
					@Override
					public LowLevelHttpResponse execute() {
						return new MockLowLevelHttpResponse().setContentType(Json.MEDIA_TYPE).setContent(responseBody);
					}
				};
			}
		};
		GoogleSecretManagerEnvironmentProperties properties = new GoogleSecretManagerEnvironmentProperties();
		return new GoogleSecretManagerV1AccessStrategy(new RestTemplate(), configProvider,
				mock(SecretManagerServiceClient.class), new GcpProjectResolutionSupport(properties)) {
			@Override
			HttpTransport createHttpTransport() {
				return transport;
			}
		};
	}

}
