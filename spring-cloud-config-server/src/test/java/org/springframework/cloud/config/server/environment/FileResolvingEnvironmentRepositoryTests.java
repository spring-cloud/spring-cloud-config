/*
 * Copyright 2024-2026 the original author or authors.
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link FileResolvingEnvironmentRepository}.
 */
class FileResolvingEnvironmentRepositoryTests {

	@TempDir
	File tempDir;

	@Test
	void findOneShouldResolveFileContentToBase64() throws Exception {
		File secretFile = new File(tempDir, "secret.txt");
		String content = "hello-spring-cloud";
		Files.writeString(secretFile.toPath(), content);

		EnvironmentRepository delegate = mock(EnvironmentRepository.class);
		Environment originalEnv = new Environment("app", "dev");

		Map<String, Object> sourceMap = new HashMap<>();
		sourceMap.put("my.secret", "{file}" + secretFile.getAbsolutePath());
		sourceMap.put("my.normal", "just-string");

		PropertySource propertySource = new PropertySource("test-source", sourceMap);
		originalEnv.add(propertySource);

		given(delegate.findOne(anyString(), anyString(), any())).willReturn(originalEnv);

		FileResolvingEnvironmentRepository repository = new FileResolvingEnvironmentRepository(delegate);

		Environment resultEnv = repository.findOne("app", "dev", null);

		assertThat(resultEnv).isNotNull();
		PropertySource resultSource = resultEnv.getPropertySources().get(0);
		Map<?, ?> resultMap = resultSource.getSource();

		String expectedBase64 = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
		assertThat(String.valueOf(resultMap.get("my.secret"))).isEqualTo(expectedBase64);

		assertThat(String.valueOf(resultMap.get("my.normal"))).isEqualTo("just-string");
	}

	@Test
	void findOneShouldHandleNonExistentFile() {
		EnvironmentRepository delegate = mock(EnvironmentRepository.class);
		Environment originalEnv = new Environment("app", "dev");

		Map<String, Object> sourceMap = new HashMap<>();
		String badPath = "{file}/path/to/non/existent/file.txt";
		sourceMap.put("my.bad.secret", badPath);

		originalEnv.add(new PropertySource("test-source", sourceMap));
		given(delegate.findOne(anyString(), anyString(), any())).willReturn(originalEnv);

		FileResolvingEnvironmentRepository repository = new FileResolvingEnvironmentRepository(delegate);
		Environment resultEnv = repository.findOne("app", "dev", null);

		PropertySource resultSource = resultEnv.getPropertySources().get(0);
		Map<?, ?> resultMap = resultSource.getSource();

		assertThat(String.valueOf(resultMap.get("my.bad.secret"))).isEqualTo(badPath);
	}

	@Test
	void findOneShouldHandleUnmodifiableMapSafely() throws IOException {
		File secretFile = new File(tempDir, "secret.txt");
		Files.write(secretFile.toPath(), "content".getBytes());

		EnvironmentRepository delegate = mock(EnvironmentRepository.class);
		Environment originalEnv = new Environment("app", "dev");

		Map<String, Object> sourceMap = Collections.singletonMap("my.secret", "{file}" + secretFile.getAbsolutePath());

		originalEnv.add(new PropertySource("immutable-source", sourceMap));
		given(delegate.findOne(anyString(), anyString(), any())).willReturn(originalEnv);

		FileResolvingEnvironmentRepository repository = new FileResolvingEnvironmentRepository(delegate);

		Environment resultEnv = repository.findOne("app", "dev", null);

		assertThat(resultEnv).isNotNull();
		Map<?, ?> resultMap = resultEnv.getPropertySources().get(0).getSource();

		assertThat(String.valueOf(resultMap.get("my.secret"))).isNotEqualTo("{file}" + secretFile.getAbsolutePath());
	}
}
