/*
 * Copyright 2016-present the original author or authors.
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

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.server.environment.GoogleSecretManagerEnvironmentProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GcpProjectResolutionSupportTests {

	@Test
	void clientHeaderDeniedWhenAllowListEmpty() {
		GoogleConfigProvider provider = mock(GoogleConfigProvider.class);
		when(provider.getValue(HttpHeaderGoogleConfigProvider.PROJECT_ID_HEADER, false)).thenReturn("forbidden");
		GoogleSecretManagerEnvironmentProperties props = new GoogleSecretManagerEnvironmentProperties();
		props.setTokenMandatory(false);
		assertThat(new GcpProjectResolutionSupport(props).resolve(provider, new RestTemplate())).isNull();
	}

	@Test
	void clientHeaderAllowedWhenInAllowList() {
		GoogleConfigProvider provider = mock(GoogleConfigProvider.class);
		when(provider.getValue(HttpHeaderGoogleConfigProvider.PROJECT_ID_HEADER, false)).thenReturn("allowed-proj");
		GoogleSecretManagerEnvironmentProperties props = new GoogleSecretManagerEnvironmentProperties();
		props.setTokenMandatory(false);
		props.setAllowedProjectIds(List.of("allowed-proj"));
		String project = new GcpProjectResolutionSupport(props).resolve(provider, new RestTemplate());
		assertThat(project).isNotNull();
		assertThat(project).isEqualTo("allowed-proj");
	}

	@Test
	void clientHeaderAllowedWhenTokenMandatoryTrueAndAllowListEmpty() {
		GoogleConfigProvider provider = mock(GoogleConfigProvider.class);
		when(provider.getValue(HttpHeaderGoogleConfigProvider.PROJECT_ID_HEADER, false)).thenReturn("any-project");
		GoogleSecretManagerEnvironmentProperties props = new GoogleSecretManagerEnvironmentProperties();
		String project = new GcpProjectResolutionSupport(props).resolve(provider, new RestTemplate());
		assertThat(project).isNotNull();
		assertThat(project).isEqualTo("any-project");
	}

	@Test
	void metadataUsedWhenNoHeader() {
		GoogleConfigProvider provider = mock(GoogleConfigProvider.class);
		when(provider.getValue(HttpHeaderGoogleConfigProvider.PROJECT_ID_HEADER, false)).thenReturn("");
		RestTemplate rest = mock(RestTemplate.class);
		when(rest.exchange(eq(GoogleSecretManagerEnvironmentProperties.GOOGLE_METADATA_PROJECT_URL), eq(HttpMethod.GET),
				any(), eq(String.class)))
			.thenReturn(ResponseEntity.ok("meta-proj"));
		GoogleSecretManagerEnvironmentProperties props = new GoogleSecretManagerEnvironmentProperties();
		props.setTokenMandatory(false);
		String project = new GcpProjectResolutionSupport(props).resolve(provider, rest);
		assertThat(project).isNotNull();
		assertThat(project).isEqualTo("meta-proj");
	}

	@Test
	void configuredProjectWhenMetadataUnavailable() {
		GoogleConfigProvider provider = mock(GoogleConfigProvider.class);
		when(provider.getValue(HttpHeaderGoogleConfigProvider.PROJECT_ID_HEADER, false)).thenReturn(null);
		RestTemplate rest = mock(RestTemplate.class);
		when(rest.exchange(eq(GoogleSecretManagerEnvironmentProperties.GOOGLE_METADATA_PROJECT_URL), eq(HttpMethod.GET),
				any(), eq(String.class)))
			.thenThrow(new RuntimeException("no metadata"));
		GoogleSecretManagerEnvironmentProperties props = new GoogleSecretManagerEnvironmentProperties();
		props.setProjectId("local-dev-project");
		props.setTokenMandatory(false);
		String project = new GcpProjectResolutionSupport(props).resolve(provider, rest);
		assertThat(project).isNotNull();
		assertThat(project).isEqualTo("local-dev-project");
	}

	@Test
	void nullWhenNoResolutionPossible() {
		GoogleConfigProvider provider = mock(GoogleConfigProvider.class);
		when(provider.getValue(HttpHeaderGoogleConfigProvider.PROJECT_ID_HEADER, false)).thenReturn(null);
		RestTemplate rest = mock(RestTemplate.class);
		when(rest.exchange(eq(GoogleSecretManagerEnvironmentProperties.GOOGLE_METADATA_PROJECT_URL), eq(HttpMethod.GET),
				any(), eq(String.class)))
			.thenThrow(new RuntimeException("no metadata"));
		GoogleSecretManagerEnvironmentProperties props = new GoogleSecretManagerEnvironmentProperties();
		props.setTokenMandatory(false);
		assertThat(new GcpProjectResolutionSupport(props).resolve(provider, rest)).isNull();
	}

}
