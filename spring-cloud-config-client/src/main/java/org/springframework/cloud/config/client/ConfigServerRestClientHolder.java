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

package org.springframework.cloud.config.client;

import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

/**
 * Holds the {@link RestClient} built from the {@code RestTemplate} that loaded the
 * configuration from the config server, which carries the client's headers, credentials,
 * TLS settings, timeouts and interceptors. It is registered as a bean of its own type,
 * not as a {@link RestClient}, so that it is neither ambiguous with the application's own
 * {@link RestClient} nor injected into it by accident.
 *
 * @author Sharang Gupta
 * @since 5.1.0
 */
final class ConfigServerRestClientHolder {

	/**
	 * Name of the bean registered when the configuration is imported from the config
	 * server.
	 */
	static final String BEAN_NAME = "configServerRestClientHolder";

	private final RestClient restClient;

	ConfigServerRestClientHolder(RestClient restClient) {
		Assert.notNull(restClient, "RestClient must not be null");
		this.restClient = restClient;
	}

	RestClient getRestClient() {
		return this.restClient;
	}

}
