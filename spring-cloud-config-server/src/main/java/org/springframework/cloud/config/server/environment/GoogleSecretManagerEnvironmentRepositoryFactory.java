/*
 * Copyright 2013-2019 the original author or authors.
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

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestTemplate;

/**
 * @author Jose Maria Alvarez
 */
public class GoogleSecretManagerEnvironmentRepositoryFactory implements
		EnvironmentRepositoryFactory<GoogleSecretManagerEnvironmentRepository, GoogleSecretManagerEnvironmentProperties> {

	private final ObjectProvider<HttpServletRequest> request;

	public GoogleSecretManagerEnvironmentRepositoryFactory(
			ObjectProvider<HttpServletRequest> request) {
		this.request = request;
	}

	@Override
	public GoogleSecretManagerEnvironmentRepository build(
			GoogleSecretManagerEnvironmentProperties environmentProperties)
			throws Exception {
		return new GoogleSecretManagerEnvironmentRepository(request, new RestTemplate(),
				environmentProperties);
	}

}
