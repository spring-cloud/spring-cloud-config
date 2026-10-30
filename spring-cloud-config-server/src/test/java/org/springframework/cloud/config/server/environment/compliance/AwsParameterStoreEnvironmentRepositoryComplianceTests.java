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
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.ParameterMetadata;
import software.amazon.awssdk.services.ssm.model.ParameterType;

import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.environment.AwsParameterStoreEnvironmentProperties;
import org.springframework.cloud.config.server.environment.AwsParameterStoreEnvironmentRepository;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;

/**
 * Compliance tests for {@link AwsParameterStoreEnvironmentRepository}. Each property is a
 * parameter below a path named after the application and profile.
 *
 * @author Ryan Baxter
 */
@Tag("DockerRequired")
class AwsParameterStoreEnvironmentRepositoryComplianceTests extends EnvironmentRepositoryComplianceTests {

	private static SsmClient ssm;

	private final Fixture fixture = new Fixture();

	@BeforeAll
	static void connect() {
		ssm = LocalStack.client(SsmClient::builder, Service.SSM);
	}

	@Override
	protected EnvironmentRepositoryFixture fixture() {
		return this.fixture;
	}

	static class Fixture implements EnvironmentRepositoryFixture {

		private final AwsParameterStoreEnvironmentProperties properties = new AwsParameterStoreEnvironmentProperties();

		@Override
		public void reset() {
			ssm.describeParametersPaginator(request -> {
			})
				.stream()
				.flatMap(response -> response.parameters().stream())
				.map(ParameterMetadata::name)
				.forEach(name -> ssm.deleteParameter(request -> request.name(name)));
		}

		@Override
		public void store(ConfigFile file) {
			String path = this.properties.getPrefix() + "/" + file.baseName() + "/";
			file.properties()
				.forEach((key, value) -> ssm.putParameter(
						request -> request.name(path + key).type(ParameterType.STRING).value(value.toString())));
		}

		@Override
		public EnvironmentRepository repository() {
			return new AwsParameterStoreEnvironmentRepository(ssm, new ConfigServerProperties(), this.properties);
		}

		@Override
		public Set<Capability> capabilities() {
			return EnumSet.of(Capability.DISTINCT_DEFAULT_PROFILE, Capability.DEFAULT_PROFILE_ALWAYS_ACTIVE);
		}

	}

}
