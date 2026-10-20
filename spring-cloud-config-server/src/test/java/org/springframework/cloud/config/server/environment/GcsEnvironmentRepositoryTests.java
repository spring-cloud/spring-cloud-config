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

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.config.ConfigServerProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GcsEnvironmentRepositoryTests {

	private final Storage storage = mock(Storage.class);

	private final GcsEnvironmentProperties properties = createProperties();

	private final ConfigServerProperties serverProperties = new ConfigServerProperties();

	private GcsEnvironmentProperties createProperties() {
		GcsEnvironmentProperties properties = new GcsEnvironmentProperties();
		properties.setBucket("test-bucket");
		return properties;
	}

	@Test
	void rejectsMissingBucket() {
		GcsEnvironmentProperties properties = new GcsEnvironmentProperties();

		assertThatThrownBy(() -> new GcsEnvironmentRepository(storage, properties, serverProperties))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("bucket");
	}

	@Test
	void loadsPropertiesFromStorage() {
		Blob blob = mock(Blob.class);
		when(storage.get(any(BlobId.class))).thenReturn(blob);
		when(blob.getContent()).thenReturn("message=hello\nfeature.enabled=true".getBytes());

		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		Environment environment = repository.findOne("my-app", "default", null);

		assertThat(environment.getName()).isEqualTo("my-app");
		assertThat(environment.getPropertySources()).isNotEmpty();
		assertThat(environment.getPropertySources().get(0).getSource().get("message")).isEqualTo("hello");
		assertThat(environment.getPropertySources().get(0).getSource().get("feature.enabled")).isEqualTo("true");

		verify(storage).get(BlobId.of("test-bucket", "my-app-default.properties"));
	}

	@Test
	void returnsEnvironmentWhenObjectDoesNotExist() {
		when(storage.get(any(BlobId.class))).thenReturn(null);

		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		Environment environment = repository.findOne("my-app", "default", null);

		assertThat(environment.getName()).isEqualTo("my-app");
		assertThat(environment.getPropertySources()).isEmpty();
	}

	@Test
	void exposesConfiguredOrder() {
		properties.setOrder(42);

		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		assertThat(repository.getOrder()).isEqualTo(42);
	}

	@Test
	void loadsYamlFromStorage() {
		Blob blob = mock(Blob.class);
		when(storage.get(any(BlobId.class))).thenAnswer(invocation -> {
			BlobId blobId = invocation.getArgument(0);
			return blobId.getName().equals("my-app-default.yml") ? blob : null;
		});
		when(blob.getContent()).thenReturn("feature:\n  enabled: true\n".getBytes());

		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		Environment environment = repository.findOne("my-app", "default", null);

		assertThat(environment.getPropertySources()).isNotEmpty();
		assertThat(environment.getPropertySources().get(0).getSource().get("feature.enabled")).isEqualTo(true);

		verify(storage).get(BlobId.of("test-bucket", "my-app-default.yml"));
	}

	@Test
	void loadsJsonFromStorage() {
		Blob blob = mock(Blob.class);
		when(storage.get(any(BlobId.class))).thenAnswer(invocation -> {
			BlobId blobId = invocation.getArgument(0);
			return blobId.getName().equals("my-app-default.json") ? blob : null;
		});
		when(blob.getContent()).thenReturn("{\"feature\":{\"enabled\":true}}".getBytes());

		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		Environment environment = repository.findOne("my-app", "default", null);

		assertThat(environment.getPropertySources()).isNotEmpty();
		assertThat(environment.getPropertySources().get(0).getSource().get("feature.enabled")).isEqualTo(true);

		verify(storage).get(BlobId.of("test-bucket", "my-app-default.json"));
	}

	@Test
	void loadsProfileSpecificPropertiesFromStorage() {
		Blob blob = mock(Blob.class);
		when(storage.get(any(BlobId.class))).thenAnswer(invocation -> {
			BlobId blobId = invocation.getArgument(0);
			return blobId.getName().equals("my-app-dev.properties") ? blob : null;
		});
		when(blob.getContent()).thenReturn("feature.enabled=true\napp.profile=dev".getBytes());

		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		Environment environment = repository.findOne("my-app", "dev", null);

		assertThat(environment.getName()).isEqualTo("my-app");
		assertThat(environment.getProfiles()).contains("dev");
		assertThat(environment.getPropertySources()).isNotEmpty();
		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("gcs:my-app-dev.properties");
		assertThat(environment.getPropertySources().get(0).getSource().get("feature.enabled")).isEqualTo("true");
		assertThat(environment.getPropertySources().get(0).getSource().get("app.profile")).isEqualTo("dev");

		verify(storage).get(BlobId.of("test-bucket", "my-app-dev.properties"));
	}

	@Test
	void serverOverridesTakePrecedenceOverGcsProperties() {
		Blob blob = mock(Blob.class);
		when(storage.get(any(BlobId.class))).thenAnswer(invocation -> {
			BlobId blobId = invocation.getArgument(0);
			return blobId.getName().equals("my-app-default.properties") ? blob : null;
		});
		when(blob.getContent()).thenReturn("feature.enabled=false\napp.name=gcs".getBytes());

		serverProperties.getOverrides().put("feature.enabled", "true");
		serverProperties.getOverrides().put("app.name", "server");

		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		Environment environment = repository.findOne("my-app", "default", null);

		assertThat(environment.getPropertySources()).isNotEmpty();
		assertThat(environment.getPropertySources().get(0).getSource().get("feature.enabled")).isEqualTo("true");
		assertThat(environment.getPropertySources().get(0).getSource().get("app.name")).isEqualTo("server");
	}

	@Test
	void loadsPropertiesFromDirectoryLayout() {
		properties.setUseDirectoryLayout(true);

		Blob blob = mock(Blob.class);
		when(storage.get(any(BlobId.class))).thenAnswer(invocation -> {
			BlobId blobId = invocation.getArgument(0);
			return blobId.getName().equals("my-app/application-default.properties") ? blob : null;
		});
		when(blob.getContent()).thenReturn("message=directory-layout".getBytes());

		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		Environment environment = repository.findOne("my-app", "default", null);

		assertThat(environment.getPropertySources()).isNotEmpty();
		assertThat(environment.getPropertySources().get(0).getSource().get("message")).isEqualTo("directory-layout");

		verify(storage).get(BlobId.of("test-bucket", "my-app/application-default.properties"));
	}

	@Test
	void resolvesCustomSearchPathContainingApplicationPlaceholder() {
		properties.setSearchPaths(List.of("{application}/config"));

		Blob blob = mock(Blob.class);
		when(storage.get(any(BlobId.class))).thenAnswer(invocation -> {
			BlobId blobId = invocation.getArgument(0);
			return blobId.getName().equals("my-app/config-default.properties") ? blob : null;
		});
		when(blob.getContent()).thenReturn("message=custom-path".getBytes());

		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		Environment environment = repository.findOne("my-app", "default", null);

		assertThat(environment.getPropertySources()).isNotEmpty();
		assertThat(environment.getPropertySources().get(0).getSource().get("message")).isEqualTo("custom-path");

		verify(storage).get(BlobId.of("test-bucket", "my-app/config-default.properties"));
	}

	@Test
	void rejectsApplicationContainingWildcard() {
		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		assertThatThrownBy(() -> repository.findOne("*", "default", null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("application");

		verifyNoInteractions(storage);
	}

	@Test
	void rejectsProfileContainingPathTraversal() {
		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		assertThatThrownBy(() -> repository.findOne("my-app", "../secret", null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("profile");

		verifyNoInteractions(storage);
	}

	@Test
	void rejectsLabelContainingPathSeparator() {
		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		assertThatThrownBy(() -> repository.findOne("my-app", "default", "../secret"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("label");

		verifyNoInteractions(storage);
	}

	@Test
	void rejectsUnsafeDefaultApplicationName() {
		serverProperties.setDefaultApplicationName("*");

		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		assertThatThrownBy(() -> repository.findOne(null, "default", null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("application");

		verifyNoInteractions(storage);
	}

	@Test
	void rejectsUnsafeDefaultProfile() {
		serverProperties.setDefaultProfile("../secret");

		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		assertThatThrownBy(() -> repository.findOne("my-app", null, null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("profile");

		verifyNoInteractions(storage);
	}

	@Test
	void rejectsUnsafeDefaultLabel() {
		serverProperties.setDefaultLabel("../secret");

		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		assertThatThrownBy(() -> repository.findOne("my-app", "default", null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("label");

		verifyNoInteractions(storage);
	}

}
