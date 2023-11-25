/*
 * Copyright 2018-2020 the original author or authors.
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

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;

import org.springframework.cloud.config.server.config.ConfigServerProperties;

import java.util.Collection;

import static org.springframework.cloud.config.server.environment.AwsClientBuilderConfigurer.configureClientBuilder;

/**
 * @author Tejas Pandilwar
 */
public class AwsSecretsManagerEnvironmentRepositoryFactory implements
		EnvironmentRepositoryFactory<AwsSecretsManagerEnvironmentRepository, AwsSecretsManagerEnvironmentProperties> {

	private final ConfigServerProperties configServerProperties;
	private final Collection<AwsSecretsManagerEnvironmentRepository.Customizer> customizers;

	public AwsSecretsManagerEnvironmentRepositoryFactory(
			ConfigServerProperties configServerProperties,
			Collection<AwsSecretsManagerEnvironmentRepository.Customizer> customizers) {

		this.configServerProperties = configServerProperties;
		this.customizers = customizers;
	}

	@Override
	public AwsSecretsManagerEnvironmentRepository build(AwsSecretsManagerEnvironmentProperties environmentProperties) {
		SecretsManagerClientBuilder clientBuilder = SecretsManagerClient.builder();

		configureClientBuilder(clientBuilder, environmentProperties.getRegion(), environmentProperties.getEndpoint());

		SecretsManagerClient client = clientBuilder.build();

		return awsSecretsManagerEnvironmentRepository(client, environmentProperties);
	}

	private AwsSecretsManagerEnvironmentRepository awsSecretsManagerEnvironmentRepository(
			SecretsManagerClient client,
			AwsSecretsManagerEnvironmentProperties environmentProperties) {

		AwsSecretsManagerEnvironmentRepository.Builder builder = AwsSecretsManagerEnvironmentRepository
				.builder(DefaultAwsSecretsManagerEnvironmentRepository::new)
				.smClient(client)
				.configServerProperties(configServerProperties)
				.environmentProperties(environmentProperties);

		customizers.forEach(customizer -> customizer.customize(builder));

		return builder.build();
	}

}
