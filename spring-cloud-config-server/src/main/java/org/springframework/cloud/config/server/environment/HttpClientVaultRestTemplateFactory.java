/*
 * Copyright 2018-2019 the original author or authors.
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

import java.security.GeneralSecurityException;

import org.apache.http.client.HttpClient;

import org.springframework.cloud.config.server.support.HttpClientSupport;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * @author Dylan Roberts
 */
public class HttpClientVaultRestTemplateFactory
		implements VaultEnvironmentRepositoryFactory.VaultRestTemplateFactory {

	@Override
	public RestTemplate build(VaultEnvironmentProperties environmentProperties)
			throws GeneralSecurityException {
		HttpClient httpClient = HttpClientSupport.builder(environmentProperties).build();
		return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
	}

}
