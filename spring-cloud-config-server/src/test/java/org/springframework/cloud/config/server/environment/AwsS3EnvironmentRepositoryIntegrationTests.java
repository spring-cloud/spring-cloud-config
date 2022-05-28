/*
 * Copyright 2022-2022 the original author or authors.
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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.config.ConfigServerProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

/**
 * @author Henning Pöttker
 */
@Testcontainers
@Tag("DockerRequired")
class AwsS3EnvironmentRepositoryIntegrationTests {

	private static final String BUCKET = "bucket1";

	@Container
	static LocalStackContainer localStack = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:0.14.3.1")).withServices(S3);

	@Test
	void test() {
		S3Client s3Client = S3Client.builder().region(Region.of(localStack.getRegion()))
				.endpointOverride(localStack.getEndpointOverride(S3))
				.credentialsProvider(StaticCredentialsProvider
						.create(AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
				.build();
		s3Client.createBucket((builder) -> builder.bucket(BUCKET));
		s3Client.putObject((builder) -> builder.bucket(BUCKET).key("foo-bar.json"),
				RequestBody.fromString("{\"tag\": \"myapp\"}"));

		Environment env = new AwsS3EnvironmentRepository(s3Client, BUCKET, new ConfigServerProperties()).findOne("foo",
				"bar", "");
		assertThat(env.getName()).isEqualTo("foo");
		assertThat(env.getPropertySources()).hasSize(1);
		assertThat(env.getPropertySources().get(0).getSource().get("tag")).isEqualTo("myapp");
	}

}
