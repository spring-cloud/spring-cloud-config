/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.config.server.environment.secretmanager;

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;

import org.springframework.cloud.config.server.environment.GoogleSecretManagerEnvironmentProperties;
import org.springframework.cloud.config.server.environment.RepositoryException;
import org.springframework.web.client.RestTemplate;

public final class GoogleSecretManagerAccessStrategyFactory {

	private GoogleSecretManagerAccessStrategyFactory() {
		throw new IllegalStateException("Can't instantiate an utility class");
	}

	public static GoogleSecretManagerAccessStrategy forVersion(RestTemplate rest, GoogleConfigProvider configProvider,
			GoogleSecretManagerEnvironmentProperties properties) {

		switch (properties.getVersion()) {
			case 1:
				try {
					return new GoogleSecretManagerV1AccessStrategy(rest, configProvider,
							properties.getServiceAccount());
				}
				catch (Exception e) {
					throw new RepositoryException("Cannot create service client", e);
				}
			default:
				throw new IllegalArgumentException(
						"No support for given Google Secret manager backend version " + properties.getVersion());
		}

	}

	public static GoogleSecretManagerAccessStrategy forVersion(RestTemplate rest, GoogleConfigProvider configProvider,
			GoogleSecretManagerEnvironmentProperties properties, SecretManagerServiceClient client) {

		switch (properties.getVersion()) {
			case 1:
				return new GoogleSecretManagerV1AccessStrategy(rest, configProvider, client);
			default:
				throw new IllegalArgumentException(
						"No support for given Google Secret manager backend version " + properties.getVersion());
		}
	}

}
