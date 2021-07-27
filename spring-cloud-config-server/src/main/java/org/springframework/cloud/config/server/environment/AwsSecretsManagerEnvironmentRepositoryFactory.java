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

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;

import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.util.StringUtils;

/**
 * @author Tejas Pandilwar
 */
public class AwsSecretsManagerEnvironmentRepositoryFactory implements
		EnvironmentRepositoryFactory<AwsSecretsManagerEnvironmentRepository, AwsSecretsManagerEnvironmentProperties> {

	private final ConfigServerProperties configServerProperties;

	public AwsSecretsManagerEnvironmentRepositoryFactory(ConfigServerProperties configServerProperties) {
		this.configServerProperties = configServerProperties;
	}

	@Override
	public AwsSecretsManagerEnvironmentRepository build(AwsSecretsManagerEnvironmentProperties environmentProperties) {
		AWSSecretsManagerClientBuilder clientBuilder = AWSSecretsManagerClientBuilder.standard();
		String region = environmentProperties.getRegion();

		if (!StringUtils.isEmpty(region)) {
			Regions awsRegion = Regions.fromName(region);
			clientBuilder.withRegion(awsRegion);

			String endpoint = environmentProperties.getEndpoint();
			if (!StringUtils.isEmpty(endpoint)) {
				AwsClientBuilder.EndpointConfiguration endpointConfiguration = new AwsClientBuilder.EndpointConfiguration(
						endpoint, awsRegion.getName());
				clientBuilder.withEndpointConfiguration(endpointConfiguration);
			}
		}

		AWSSecretsManager client = clientBuilder.build();
		return new AwsSecretsManagerEnvironmentRepository(client, configServerProperties, environmentProperties);
	}

}
