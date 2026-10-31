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

import java.util.function.Supplier;

import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;

/**
 * A LocalStack container shared by the AWS compliance tests, started on first use.
 *
 * @author Ryan Baxter
 */
final class LocalStack {

	private static final LocalStackContainer CONTAINER = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:4.0.3"))
		.withServices(Service.S3, Service.SECRETSMANAGER, Service.SSM);

	private LocalStack() {
	}

	/**
	 * Build a client for a LocalStack service.
	 * @param builder the client builder
	 * @param service the service
	 * @param <B> the type of the builder
	 * @param <C> the type of the client
	 * @return the client
	 */
	static <B extends AwsClientBuilder<B, C>, C> C client(Supplier<B> builder, Service service) {
		synchronized (CONTAINER) {
			if (!CONTAINER.isRunning()) {
				CONTAINER.start();
			}
		}
		return builder.get()
			.region(Region.of(CONTAINER.getRegion()))
			.credentialsProvider(StaticCredentialsProvider
				.create(AwsBasicCredentials.create(CONTAINER.getAccessKey(), CONTAINER.getSecretKey())))
			.endpointOverride(CONTAINER.getEndpointOverride(service))
			.build();
	}

}
