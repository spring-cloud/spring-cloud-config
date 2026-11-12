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

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AwsS3EnvironmentRepositoryFactoryTests {

	@Test
	void usesSuppliedS3Client() {
		S3Client s3Client = mock(S3Client.class);
		AwsS3EnvironmentRepositoryFactory factory = new AwsS3EnvironmentRepositoryFactory(new ConfigServerProperties(),
				s3Client);

		AwsS3EnvironmentProperties properties = new AwsS3EnvironmentProperties();
		properties.setBucket("bucket1");
		AwsS3EnvironmentRepository repository = factory.build(properties);

		assertThat(ReflectionTestUtils.getField(repository, "s3Client")).isSameAs(s3Client);
	}

	@Test
	void buildsOwnS3ClientWhenNoneSupplied() {
		AwsS3EnvironmentRepositoryFactory factory = new AwsS3EnvironmentRepositoryFactory(new ConfigServerProperties());

		AwsS3EnvironmentProperties properties = new AwsS3EnvironmentProperties();
		properties.setBucket("bucket1");
		properties.setRegion("us-east-1");
		properties.setEndpoint("http://localhost:4566");
		AwsS3EnvironmentRepository repository = factory.build(properties);

		assertThat(ReflectionTestUtils.getField(repository, "s3Client")).isNotNull();
	}

}
