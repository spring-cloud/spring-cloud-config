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

package org.springframework.cloud.config.server.environment;

import java.util.List;

import com.google.cloud.parametermanager.v1.LocationName;
import com.google.cloud.parametermanager.v1.Parameter;
import com.google.cloud.parametermanager.v1.ParameterManagerClient;
import com.google.cloud.parametermanager.v1.RenderParameterVersionResponse;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.config.ConfigServerProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GoogleParameterManagerEnvironmentRepositoryTests {

	@Test
	@SuppressWarnings("unchecked")
	public void testFindOne() {
		ParameterManagerClient client = mock(ParameterManagerClient.class);
		Parameter parameter = Parameter.newBuilder()
			.setName("projects/test-project/locations/global/parameters/test-property")
			.putLabels("application", "application")
			.putLabels("profile", "default")
			.build();
		ParameterManagerClient.ListParametersPagedResponse parametersResponse = mock(
				ParameterManagerClient.ListParametersPagedResponse.class);
		when(parametersResponse.iterateAll()).thenReturn(List.of(parameter));
		when(client.listParameters(any(LocationName.class))).thenReturn(parametersResponse);
		RenderParameterVersionResponse renderResponse = RenderParameterVersionResponse.newBuilder()
			.setRenderedPayload(ByteString.copyFromUtf8("test-value"))
			.build();
		when(client.renderParameterVersion(any(String.class))).thenReturn(renderResponse);
		GoogleParameterManagerEnvironmentProperties properties = new GoogleParameterManagerEnvironmentProperties();
		properties.setProjectId("test-project");
		ConfigServerProperties configServerProperties = new ConfigServerProperties();
		GoogleParameterManagerEnvironmentRepository repository = new GoogleParameterManagerEnvironmentRepository(client,
				properties, configServerProperties);
		Environment environment = repository.findOne("application", "default", null);
		assertThat(environment.getLabel()).isEqualTo("main");
		assertThat(environment.getPropertySources()).hasSize(1);
		assertThat(environment.getPropertySources().get(0).getSource().get("test-property")).isEqualTo("test-value");
		Mockito.verify(client)
			.renderParameterVersion("projects/test-project/locations/global/parameters/test-property/versions/latest");
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFindOneWithCustomDefaultLabel() {
		ParameterManagerClient client = mock(ParameterManagerClient.class);
		ParameterManagerClient.ListParametersPagedResponse parametersResponse = mock(
				ParameterManagerClient.ListParametersPagedResponse.class);
		when(parametersResponse.iterateAll()).thenReturn(List.of());
		when(client.listParameters(any(LocationName.class))).thenReturn(parametersResponse);
		GoogleParameterManagerEnvironmentProperties properties = new GoogleParameterManagerEnvironmentProperties();
		properties.setProjectId("test-project");
		properties.setDefaultLabel("custom-default");
		ConfigServerProperties configServerProperties = new ConfigServerProperties();
		GoogleParameterManagerEnvironmentRepository repository = new GoogleParameterManagerEnvironmentRepository(client,
				properties, configServerProperties);
		Environment environment = repository.findOne("application", "default", null);
		assertThat(environment.getLabel()).isEqualTo("custom-default");
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFindOneIncludesDefaultApplication() {
		ParameterManagerClient client = mock(ParameterManagerClient.class);
		Parameter defaultApplicationParameter = Parameter.newBuilder()
			.setName("projects/test-project/locations/global/parameters/default-property")
			.putLabels("application", "application")
			.putLabels("profile", "default")
			.build();
		Parameter applicationParameter = Parameter.newBuilder()
			.setName("projects/test-project/locations/global/parameters/application-property")
			.putLabels("application", "my-application")
			.putLabels("profile", "default")
			.build();
		ParameterManagerClient.ListParametersPagedResponse parametersResponse = mock(
				ParameterManagerClient.ListParametersPagedResponse.class);
		when(parametersResponse.iterateAll()).thenReturn(List.of(defaultApplicationParameter, applicationParameter));
		when(client.listParameters(any(LocationName.class))).thenReturn(parametersResponse);
		when(client.renderParameterVersion(
				"projects/test-project/locations/global/parameters/default-property/versions/latest"))
			.thenReturn(RenderParameterVersionResponse.newBuilder()
				.setRenderedPayload(ByteString.copyFromUtf8("default-value"))
				.build());
		when(client.renderParameterVersion(
				"projects/test-project/locations/global/parameters/application-property/versions/latest"))
			.thenReturn(RenderParameterVersionResponse.newBuilder()
				.setRenderedPayload(ByteString.copyFromUtf8("application-value"))
				.build());
		GoogleParameterManagerEnvironmentProperties properties = new GoogleParameterManagerEnvironmentProperties();
		properties.setProjectId("test-project");
		ConfigServerProperties configServerProperties = new ConfigServerProperties();
		GoogleParameterManagerEnvironmentRepository repository = new GoogleParameterManagerEnvironmentRepository(client,
				properties, configServerProperties);
		Environment environment = repository.findOne("my-application", "default", null);
		assertThat(environment.getName()).isEqualTo("my-application");
		assertThat(environment.getPropertySources()).hasSize(2);
		assertThat(environment.getPropertySources().get(0).getSource().get("default-property"))
			.isEqualTo("default-value");
		assertThat(environment.getPropertySources().get(1).getSource().get("application-property"))
			.isEqualTo("application-value");
		Mockito.verify(client).listParameters(any(LocationName.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFindOneUsesConfiguredDefaultProfile() {
		ParameterManagerClient client = mock(ParameterManagerClient.class);
		Parameter parameter = Parameter.newBuilder()
			.setName("projects/test-project/locations/global/parameters/test-property")
			.putLabels("application", "application")
			.putLabels("profile", "custom-default")
			.build();
		ParameterManagerClient.ListParametersPagedResponse parametersResponse = mock(
				ParameterManagerClient.ListParametersPagedResponse.class);
		when(parametersResponse.iterateAll()).thenReturn(List.of(parameter));
		when(client.listParameters(any(LocationName.class))).thenReturn(parametersResponse);
		when(client.renderParameterVersion(any(String.class))).thenReturn(RenderParameterVersionResponse.newBuilder()
			.setRenderedPayload(ByteString.copyFromUtf8("test-value"))
			.build());
		GoogleParameterManagerEnvironmentProperties properties = new GoogleParameterManagerEnvironmentProperties();
		properties.setProjectId("test-project");
		ConfigServerProperties configServerProperties = new ConfigServerProperties();
		configServerProperties.setDefaultProfile("custom-default");
		GoogleParameterManagerEnvironmentRepository repository = new GoogleParameterManagerEnvironmentRepository(client,
				properties, configServerProperties);
		Environment environment = repository.findOne("application", null, null);
		assertThat(environment.getProfiles()).containsExactly("custom-default");
		assertThat(environment.getPropertySources()).hasSize(1);
		assertThat(environment.getPropertySources().get(0).getSource().get("test-property")).isEqualTo("test-value");
	}

	@Test
	public void shouldCloseParameterManagerClient() {
		ParameterManagerClient client = mock(ParameterManagerClient.class);
		GoogleParameterManagerEnvironmentProperties properties = new GoogleParameterManagerEnvironmentProperties();
		ConfigServerProperties configServerProperties = new ConfigServerProperties();
		GoogleParameterManagerEnvironmentRepository repository = new GoogleParameterManagerEnvironmentRepository(client,
				properties, configServerProperties);
		repository.close();
		Mockito.verify(client).close();
	}

}
