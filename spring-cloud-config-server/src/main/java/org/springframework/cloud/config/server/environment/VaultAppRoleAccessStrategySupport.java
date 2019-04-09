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

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;

/**
 * Base class for {@link VaultAppRoleAccessStrategy} implementors.
 *
 * @author Kamalakar Ponaka
 * @since 2.0
 */
abstract class VaultAppRoleAccessStrategySupport implements VaultAppRoleAccessStrategy {

	private final String baseUrl;

	private final RestOperations rest;

	VaultAppRoleAccessStrategySupport(String baseUrl, RestOperations rest) {
		this.baseUrl = baseUrl;
		this.rest = rest;
	}

	/**
	 * Extract the raw JSON from the
	 * {@link org.springframework.cloud.config.server.environment.VaultKvAccessStrategy.VaultResponse}.
	 * @param body vault response
	 * @return raw JSON
	 */
	abstract String extractDataFromBody(VaultAppRoleResponse body);

	/**
	 * @param headers must not be {@literal null}.
	 * @param backend secret backend mount path, must not be {@literal null}.
	 * @param key key within the key-value secret backend, must not be {@literal null}.
	 * @return
	 */
	@Override
	public String getAuth(HttpEntity<?> requestEntity) {
		try {

			String urlTemplate = String.format("%s/v1/%s/%s/%s", baseUrl, "auth","approle","login");

			ResponseEntity<VaultAppRoleResponse> response = this.rest.exchange(urlTemplate,
					HttpMethod.POST, requestEntity, VaultAppRoleResponse.class);
			HttpStatus status = response.getStatusCode();
			if (status == HttpStatus.OK) {
				return extractDataFromBody(response.getBody());
			}
		}
		catch (HttpStatusCodeException e) {
			if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
				return null;
			}
			throw e;
		}
		return null;
	}

}
