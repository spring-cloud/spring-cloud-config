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

package org.springframework.cloud.config.server.environment.compliance;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.environment.AwsS3EnvironmentRepository;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;

/**
 * Compliance tests for {@link AwsS3EnvironmentRepository}. Each file is an object in a
 * bucket, prefixed with its label.
 *
 * @author Ryan Baxter
 */
@Tag("DockerRequired")
class AwsS3EnvironmentRepositoryComplianceTests extends EnvironmentRepositoryComplianceTests {

	private static final String BUCKET = "compliance";

	private static S3Client s3;

	private final Fixture fixture = new Fixture();

	@BeforeAll
	static void createBucket() {
		s3 = LocalStack.client(S3Client::builder, Service.S3);
		s3.createBucket(request -> request.bucket(BUCKET));
	}

	@Override
	protected EnvironmentRepositoryFixture fixture() {
		return this.fixture;
	}

	@Override
	@Test
	@Disabled("TODO-issue: Documents activated by a profile expression get the lowest precedence")
	public void profileExpressionsActivateDocuments() throws Exception {
		super.profileExpressionsActivateDocuments();
	}

	@Override
	@Test
	@Disabled("TODO-issue: Labels are interleaved by profile rather than resolved one after another")
	public void lastLabelTakesPrecedence() throws Exception {
		super.lastLabelTakesPrecedence();
	}

	static class Fixture implements EnvironmentRepositoryFixture {

		@Override
		public void reset() {
			for (S3Object object : s3.listObjectsV2Paginator(request -> request.bucket(BUCKET)).contents()) {
				s3.deleteObject(request -> request.bucket(BUCKET).key(object.key()));
			}
		}

		@Override
		public void store(ConfigFile file) {
			s3.putObject(request -> request.bucket(BUCKET).key(file.path()), RequestBody.fromString(file.content()));
		}

		@Override
		public EnvironmentRepository repository() {
			return new AwsS3EnvironmentRepository(s3, BUCKET, new ConfigServerProperties());
		}

		@Override
		public Set<Capability> capabilities() {
			return EnumSet.allOf(Capability.class);
		}

	}

}
