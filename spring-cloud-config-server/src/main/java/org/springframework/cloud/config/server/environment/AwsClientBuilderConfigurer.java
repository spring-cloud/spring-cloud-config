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

import java.net.URI;

import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;

import org.springframework.util.StringUtils;

abstract class AwsClientBuilderConfigurer {

	private AwsClientBuilderConfigurer() {
	}

	static void configureClientBuilder(AwsClientBuilder<?, ?> clientBuilder, String region, String endpoint) {
		if (StringUtils.hasText(region)) {
			clientBuilder.region(Region.of(region));
			if (StringUtils.hasText(endpoint)) {
				clientBuilder.endpointOverride(URI.create(endpoint));
			}
		}
	}

}
