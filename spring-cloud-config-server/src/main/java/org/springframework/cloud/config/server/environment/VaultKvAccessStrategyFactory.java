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

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.web.client.RestOperations;

/**
 * Factory for {@link VaultKvAccessStrategy}.
 *
 * @author Haroun Pacquee
 * @author Mark Paluch
 * @since 2.0
 */
public final class VaultKvAccessStrategyFactory {

	private VaultKvAccessStrategyFactory() {
		throw new IllegalStateException("Can't instantiate an utility class");
	}

	/**
	 * Create a new {@link VaultKvAccessStrategy} given {@link RestOperations},
	 * {@code baseUrl}, and {@code version}.
	 * @param rest must not be {@literal null}.
	 * @param baseUrl the Vault base URL.
	 * @param version version of the Vault key-value backend.
	 * @return the access strategy.
	 */
	public static VaultKvAccessStrategy forVersion(RestOperations rest, String baseUrl,
			int version) {

		switch (version) {
		case 1:
			return new V1VaultKvAccessStrategy(baseUrl, rest);
		case 2:
			return new V2VaultKvAccessStrategy(baseUrl, rest);
		default:
			throw new IllegalArgumentException(
					"No support for given Vault k/v backend version " + version);
		}
	}

	/**
	 * Strategy for the key-value backend API version 1.
	 */
	static class V1VaultKvAccessStrategy extends VaultKvAccessStrategySupport {

		V1VaultKvAccessStrategy(String baseUrl, RestOperations rest) {
			super(baseUrl, rest);
		}

		@Override
		public String getPath() {
			return "{key}";
		}

		@Override
		public String extractDataFromBody(VaultResponse body) {
			return body.getData() == null ? null : body.getData().toString();
		}

	}

	/**
	 * Strategy for the key-value backend API version 2.
	 */
	static class V2VaultKvAccessStrategy extends VaultKvAccessStrategySupport {

		V2VaultKvAccessStrategy(String baseUrl, RestOperations rest) {
			super(baseUrl, rest);
		}

		@Override
		public String getPath() {
			return "data/{key}";
		}

		@Override
		public String extractDataFromBody(VaultResponse body) {
			JsonNode nestedDataNode = body.getData() == null ? null
					: ((JsonNode) body.getData()).get("data");
			return nestedDataNode == null ? null : nestedDataNode.toString();
		}

	}

}
