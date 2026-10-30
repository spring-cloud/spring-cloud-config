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
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.SecretListEntry;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.environment.AwsSecretsManagerEnvironmentProperties;
import org.springframework.cloud.config.server.environment.AwsSecretsManagerEnvironmentRepository;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;

/**
 * Compliance tests for {@link AwsSecretsManagerEnvironmentRepository}. Each file is a
 * JSON secret named after the application and profile, and each label is a version stage.
 *
 * @author Ryan Baxter
 */
@Tag("DockerRequired")
class AwsSecretsManagerEnvironmentRepositoryComplianceTests extends EnvironmentRepositoryComplianceTests {

	private static SecretsManagerClient secretsManager;

	private final Fixture fixture = new Fixture();

	@BeforeAll
	static void connect() {
		secretsManager = LocalStack.client(SecretsManagerClient::builder, Service.SECRETSMANAGER);
	}

	@Override
	protected EnvironmentRepositoryFixture fixture() {
		return this.fixture;
	}

	static class Fixture implements EnvironmentRepositoryFixture {

		private final AwsSecretsManagerEnvironmentProperties properties = new AwsSecretsManagerEnvironmentProperties();

		private final JsonMapper json = new JsonMapper();

		@Override
		public void reset() {
			secretsManager.listSecretsPaginator(request -> {
			})
				.stream()
				.flatMap(response -> response.secretList().stream())
				.map(SecretListEntry::name)
				.forEach(name -> secretsManager
					.deleteSecret(request -> request.secretId(name).forceDeleteWithoutRecovery(true)));
		}

		@Override
		public void store(ConfigFile file) {
			String name = this.properties.getPrefix() + "/" + file.baseName() + "/";
			String value = this.json.writeValueAsString(file.properties());
			if (file.label() == null) {
				secretsManager.createSecret(request -> request.name(name).secretString(value));
				return;
			}
			try {
				secretsManager.describeSecret(request -> request.secretId(name));
				secretsManager
					.putSecretValue(request -> request.secretId(name).secretString(value).versionStages(file.label()));
			}
			catch (ResourceNotFoundException ex) {
				CreateSecretResponse created = secretsManager
					.createSecret(request -> request.name(name).secretString(value));
				secretsManager.updateSecretVersionStage(request -> request.secretId(name)
					.moveToVersionId(created.versionId())
					.versionStage(file.label()));
			}
		}

		@Override
		public EnvironmentRepository repository() {
			return new AwsSecretsManagerEnvironmentRepository(secretsManager, new ConfigServerProperties(),
					this.properties);
		}

		@Override
		public Set<Capability> capabilities() {
			return EnumSet.of(Capability.LABELS, Capability.MULTIPLE_LABELS, Capability.MULTIPLE_APPLICATIONS,
					Capability.DISTINCT_DEFAULT_PROFILE, Capability.DEFAULT_PROFILE_ALWAYS_ACTIVE);
		}

	}

}
