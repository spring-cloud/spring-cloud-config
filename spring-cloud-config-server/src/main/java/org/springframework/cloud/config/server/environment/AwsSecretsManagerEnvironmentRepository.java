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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.util.function.Consumer;
import java.util.function.Function;

public interface AwsSecretsManagerEnvironmentRepository extends EnvironmentRepository, Ordered {

	@Override
	default int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	static Builder builder(Function<Builder, AwsSecretsManagerEnvironmentRepository> initializer) {
		return new Builder(initializer);
	}

	class Builder {

		private Function<Builder, AwsSecretsManagerEnvironmentRepository> initializer;

		private ObjectMapper objectMapper = new ObjectMapper();

		private SecretsManagerClient smClient;

		private ConfigServerProperties configServerProperties;

		private AwsSecretsManagerEnvironmentProperties environmentProperties;

		private int order = Ordered.LOWEST_PRECEDENCE;

		private Consumer<GetSecretValueRequest.Builder> secretValueRequestCustomizer = builder -> {};

		public Builder(Function<Builder, AwsSecretsManagerEnvironmentRepository> initializer) {
			this.initializer = initializer;
		}

		public Builder initializer(Function<Builder, AwsSecretsManagerEnvironmentRepository> initializer) {
			Assert.notNull(initializer, "initializer");

			this.initializer = initializer;

			return this;
		}

		public Builder objectMapper(ObjectMapper objectMapper) {
			this.objectMapper = objectMapper;

			return this;
		}

		public ObjectMapper objectMapper() {
			return objectMapper;
		}

		public Builder smClient(SecretsManagerClient smClient) {
			this.smClient = smClient;

			return this;
		}

		public SecretsManagerClient smClient() {
			return smClient;
		}

		public Builder configServerProperties(ConfigServerProperties configServerProperties) {
			this.configServerProperties = configServerProperties;

			return this;
		}

		public ConfigServerProperties configServerProperties() {
			return configServerProperties;
		}

		public Builder environmentProperties(AwsSecretsManagerEnvironmentProperties environmentProperties) {
			this.environmentProperties = environmentProperties;
			this.order = environmentProperties.getOrder();

			return this;
		}

		public AwsSecretsManagerEnvironmentProperties environmentProperties() {
			return environmentProperties;
		}

		public Builder order(int order) {
			this.order = order;

			return this;
		}

		public int order() {
			return order;
		}

		public Builder secretValueRequestCustomizer(Consumer<GetSecretValueRequest.Builder> secretValueRequestCustomizer) {
			this.secretValueRequestCustomizer = secretValueRequestCustomizer;

			return this;
		}

		public Consumer<GetSecretValueRequest.Builder> secretValueRequestCustomizer() {
			return secretValueRequestCustomizer;
		}

		public AwsSecretsManagerEnvironmentRepository build() {
			return initializer.apply(this);
		}

	}

	@FunctionalInterface
	interface Customizer {
		void customize(Builder builder);
	}

}
