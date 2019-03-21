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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;

/**
 * Base class for {@link VaultKvAccessStrategy} implementors.
 *
 * @author Mark Paluch
 * @since 2.0
 */
abstract class VaultKvAccessStrategySupport implements VaultKvAccessStrategy {

	private final String baseUrl;

	private final RestOperations rest;

	VaultKvAccessStrategySupport(String baseUrl, RestOperations rest) {
		this.baseUrl = baseUrl;
		this.rest = rest;
	}

	/**
	 * @return the relative context path template within a secret backend to append to a
	 * URL containing the {@code baseURL}, API version segment and secret backend. May be
	 * templated with {@literal {key}} to as template variable for the secret key.
	 */
	abstract String getPath();

	/**
	 * Extract the raw JSON from the
	 * {@link org.springframework.cloud.config.server.environment.VaultKvAccessStrategy.VaultResponse}.
	 * @param body vault response
	 * @return raw JSON
	 */
	abstract String extractDataFromBody(VaultResponse body);

	/**
	 * @param headers must not be {@literal null}.
	 * @param backend secret backend mount path, must not be {@literal null}.
	 * @param key key within the key-value secret backend, must not be {@literal null}.
	 * @return
	 */
	@Override
	public String getData(HttpHeaders headers, String backend, String key) {
		try {

			String urlTemplate = String.format("%s/v1/%s/%s", this.baseUrl, backend,
					getPath());

			ResponseEntity<VaultResponse> response = this.rest.exchange(urlTemplate,
					HttpMethod.GET, new HttpEntity<>(headers), VaultResponse.class, key);
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
