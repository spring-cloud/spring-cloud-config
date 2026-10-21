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
import java.util.Map;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.config.ConfigServerProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
			return blobId.getName().equals("my-app/config.properties") ? blob : null;
		});

		when(blob.getContent()).thenReturn("message=custom-path".getBytes());

		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		Environment environment = repository.findOne("my-app", "default", null);

		assertThat(environment.getPropertySources()).isNotEmpty();
		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("gcs:my-app/config.properties");
		assertThat(environment.getPropertySources().get(0).getSource().get("message")).isEqualTo("custom-path");

		verify(storage).get(BlobId.of("test-bucket", "my-app/config.properties"));
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

	@Test
	void loadsOnlyYamlDocumentMatchingActiveProfile() {
		Blob blob = mock(Blob.class);
		when(storage.get(any(BlobId.class))).thenAnswer(invocation -> {
			BlobId blobId = invocation.getArgument(0);
			return blobId.getName().equals("my-app-dev.yml") ? blob : null;
		});

		String yamlContent = "common: shared\n" + "---\n" + "spring:\n" + "  config:\n" + "    activate:\n"
				+ "      on-profile: dev\n" + "feature: development\n" + "---\n" + "spring:\n" + "  config:\n"
				+ "    activate:\n" + "      on-profile: production\n" + "feature: production\n";

		when(blob.getContent()).thenReturn(yamlContent.getBytes());

		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		Environment environment = repository.findOne("my-app", "dev", null);

		assertThat(environment.getPropertySources()).isNotEmpty();

		Map<?, ?> source = (Map<?, ?>) environment.getPropertySources().get(0).getSource();

		assertThat("shared".equals(source.get("common"))).isTrue();
		assertThat("development".equals(source.get("feature"))).isTrue();
		assertThat(source.containsKey("feature.production")).isFalse();
	}

	@Test
	void excludesProfileSpecificYamlDocumentsWhenNoProfileIsActive() {
		Blob blob = mock(Blob.class);

		when(storage.get(any(BlobId.class))).thenAnswer(invocation -> {
			BlobId blobId = invocation.getArgument(0);
			return blobId.getName().equals("my-app.yml") ? blob : null;
		});

		String yamlContent = String.join("\n", "common: shared", "---", "spring:", "  config:", "    activate:",
				"      on-profile: dev", "feature: development", "---", "spring:", "  config:", "    activate:",
				"      on-profile: production", "feature: production");

		when(blob.getContent()).thenReturn(yamlContent.getBytes());

		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		Environment environment = repository.findOne("my-app", null, null);

		assertThat(environment.getPropertySources().get(0).getSource().toString()).contains("common=shared")
			.doesNotContain("feature=");
	}

	@Test
	void propagatesStorageListingFailures() {
		properties.setSearchPaths(List.of("config/*"));

		when(storage.list(anyString(), any(Storage.BlobListOption.class)))
			.thenThrow(new IllegalStateException("GCS listing failed"));

		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		assertThatThrownBy(() -> repository.findOne("my-app", "default", null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("GCS listing failed");
	}

	@Test
	void propagatesStorageReadFailures() {
		when(storage.get(any(BlobId.class))).thenThrow(new IllegalStateException("GCS read failed"));

		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		assertThatThrownBy(() -> repository.findOne("my-app", "default", null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Cannot load environment")
			.hasCauseInstanceOf(IllegalStateException.class);
	}

	@Test
	void resolvesUnprofiledSearchPathWithoutTrailingHyphen() {
		serverProperties.setDefaultProfile(null);
		properties.setSearchPaths(List.of("{application}-{profile}.yml"));

		Blob blob = mock(Blob.class);
		when(storage.get(any(BlobId.class))).thenAnswer(invocation -> {
			BlobId blobId = invocation.getArgument(0);
			return blobId.getName().equals("my-app.yml") ? blob : null;
		});
		when(blob.getContent()).thenReturn("message=unprofiled".getBytes());

		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		Environment environment = repository.findOne("my-app", null, null);

		assertThat(environment.getPropertySources()).isNotEmpty();
		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("gcs:my-app.yml");

		verify(storage).get(BlobId.of("test-bucket", "my-app.yml"));
		verify(storage, org.mockito.Mockito.never()).get(BlobId.of("test-bucket", "my-app-.yml"));
	}

	@Test
	void fallsBackToUnlabelledKeyWhenLabelledObjectDoesNotExist() {
		Blob blob = mock(Blob.class);
		when(storage.get(any(BlobId.class))).thenAnswer(invocation -> {
			BlobId blobId = invocation.getArgument(0);
			return blobId.getName().equals("my-app-default.properties") ? blob : null;
		});
		when(blob.getContent()).thenReturn("message=unlabelled".getBytes());

		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		Environment environment = repository.findOne("my-app", "default", "release-1");

		assertThat(environment.getPropertySources()).isNotEmpty();
		assertThat(environment.getPropertySources().get(0).getSource().get("message")).isEqualTo("unlabelled");

		verify(storage).get(BlobId.of("test-bucket", "release-1/my-app-default.properties"));
		verify(storage).get(BlobId.of("test-bucket", "my-app-default.properties"));
	}

	@Test
	void supportsNegatedProfilesInYamlDocuments() {
		Blob blob = mock(Blob.class);
		when(storage.get(any(BlobId.class))).thenAnswer(invocation -> {
			BlobId blobId = invocation.getArgument(0);
			return blobId.getName().equals("my-app-dev.yml") ? blob : null;
		});

		String yamlContent = String.join("\n", "common: shared", "---", "spring:", "  config:", "    activate:",
				"      on-profile: \"!foo\"", "feature: enabled", "---", "spring:", "  config:", "    activate:",
				"      on-profile: foo", "feature: disabled");

		when(blob.getContent()).thenReturn(yamlContent.getBytes());

		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		Environment environment = repository.findOne("my-app", "dev", null);

		Map<?, ?> source = (Map<?, ?>) environment.getPropertySources().get(0).getSource();

		assertThat(source.get("common")).isEqualTo("shared");
		assertThat(source.get("feature")).isEqualTo("enabled");
	}

	@Test
	void loadsYamlDocumentsForEachActiveProfile() {
		Blob blob = mock(Blob.class);
		when(storage.get(any(BlobId.class))).thenAnswer(invocation -> {
			BlobId blobId = invocation.getArgument(0);
			return blobId.getName().equals("my-app.yml") ? blob : null;
		});

		String yamlContent = String.join("\n", "common: shared", "---", "spring:", "  config:", "    activate:",
				"      on-profile: dev", "feature.dev: enabled", "---", "spring:", "  config:", "    activate:",
				"      on-profile: production", "feature.production: enabled");

		when(blob.getContent()).thenReturn(yamlContent.getBytes());

		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		Environment environment = repository.findOne("my-app", "dev,production", null);

		assertThat(environment.getProfiles()).containsExactly("dev", "production");
		assertThat(environment.getPropertySources()).isNotEmpty();

		Map<?, ?> source = (Map<?, ?>) environment.getPropertySources().get(0).getSource();
		assertThat(source.get("common")).isEqualTo("shared");
		assertThat(source.get("feature.dev")).isEqualTo("enabled");
		assertThat(source.get("feature.production")).isEqualTo("enabled");
	}

	@Test
	void resolvesMultipleLabelsInReverseOrder() {
		Blob mainBlob = mock(Blob.class);
		Blob developBlob = mock(Blob.class);

		when(storage.get(any(BlobId.class))).thenAnswer(invocation -> {
			BlobId blobId = invocation.getArgument(0);
			return switch (blobId.getName()) {
				case "main/my-app-default.properties" -> mainBlob;
				case "develop/my-app-default.properties" -> developBlob;
				default -> null;
			};
		});

		when(mainBlob.getContent()).thenReturn("message=main".getBytes());
		when(developBlob.getContent()).thenReturn("message=develop".getBytes());

		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		Environment environment = repository.findOne("my-app", "default", "main,develop");

		assertThat(environment.getPropertySources()).hasSize(2);
		assertThat(environment.getPropertySources().get(0).getSource().get("message")).isEqualTo("develop");
		assertThat(environment.getPropertySources().get(1).getSource().get("message")).isEqualTo("main");
	}

	@Test
	void resolvesLiteralSearchPathWithoutAddingProfileSuffix() {
		properties.setSearchPaths(List.of("config/application"));

		Blob baseBlob = mock(Blob.class);

		when(storage.get(any(BlobId.class))).thenAnswer(invocation -> {
			BlobId blobId = invocation.getArgument(0);
			return blobId.getName().equals("config/application.properties") ? baseBlob : null;
		});

		when(baseBlob.getContent()).thenReturn("message=base".getBytes());

		GcsEnvironmentRepository repository = new GcsEnvironmentRepository(storage, properties, serverProperties);

		Environment environment = repository.findOne("my-app", "dev", null);

		assertThat(environment.getPropertySources()).isNotEmpty();
		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("gcs:config/application.properties");
		assertThat(environment.getPropertySources().get(0).getSource().get("message")).isEqualTo("base");

		verify(storage).get(BlobId.of("test-bucket", "config/application.properties"));
		verify(storage, org.mockito.Mockito.never()).get(BlobId.of("test-bucket", "config/application-dev.properties"));
	}

}
