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

import java.io.ByteArrayInputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.config.ConfigServerProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Noah Hanka
 */
public class ParallelAwsS3EnvironmentRepositoryTests {

	@Test
	public void testParallelFetching() {
		S3Client s3Client = mock(S3Client.class);
		ConfigServerProperties server = new ConfigServerProperties();
		Executor executor = Executors.newFixedThreadPool(2);

		AwsS3EnvironmentRepository repo = new AwsS3EnvironmentRepository(s3Client, "bucket", false, server, executor);

		String content = "foo: bar";
		GetObjectResponse response = GetObjectResponse.builder().build();

		when(s3Client.getObject(any(GetObjectRequest.class))).thenAnswer(invocation -> {
			return new ResponseInputStream<>(response,
					AbortableInputStream.create(new ByteArrayInputStream(content.getBytes())));
		});

		// Request with 2 profiles
		Environment env = repo.findOne("app", "p1,p2", "label");

		assertThat(env.getPropertySources()).isNotEmpty();
		verify(s3Client, atLeastOnce()).getObject(any(GetObjectRequest.class));
	}

	@Test
	public void testSequentialFetchingByDefault() {
		S3Client s3Client = mock(S3Client.class);
		ConfigServerProperties server = new ConfigServerProperties();

		AwsS3EnvironmentRepository repo = new AwsS3EnvironmentRepository(s3Client, "bucket", server);

		String content = "foo: bar";
		GetObjectResponse response = GetObjectResponse.builder().build();

		when(s3Client.getObject(any(GetObjectRequest.class))).thenAnswer(invocation -> {
			return new ResponseInputStream<>(response,
					AbortableInputStream.create(new ByteArrayInputStream(content.getBytes())));
		});

		Environment env = repo.findOne("app", "p1", "label");

		assertThat(env.getPropertySources()).isNotEmpty();
		verify(s3Client, atLeastOnce()).getObject(any(GetObjectRequest.class));
	}

}
