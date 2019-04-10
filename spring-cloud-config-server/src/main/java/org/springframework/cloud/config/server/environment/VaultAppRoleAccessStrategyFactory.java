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
 * Factory for {@link VaultAppRoleAccessStrategy}.
 *
 * @author Kamalakar Ponaka
 * @since 2.0
 */
public final class VaultAppRoleAccessStrategyFactory {

	private VaultAppRoleAccessStrategyFactory() {
		throw new IllegalStateException("Can't instantiate an utility class");
	}

	/**
	 * Create a new {@link VaultKvAccessStrategy} given {@link RestOperations},
	 * {@code baseUrl}, and {@code version}.
	 * @param rest must not be {@literal null}.
	 * @param baseUrl the Vault base URL.
	 * @return the access strategy.
	 */
	public static VaultAppRoleAccessStrategy getToken(RestOperations rest,
			String baseUrl) {
		return new V1VaultAppRoleAccessStrategy(baseUrl, rest);
	}

	/**
	 * Strategy for the key-value backend API version 1.
	 */
	static class V1VaultAppRoleAccessStrategy extends VaultAppRoleAccessStrategySupport {

		V1VaultAppRoleAccessStrategy(String baseUrl, RestOperations rest) {
			super(baseUrl, rest);
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see org.springframework.cloud.config.server.environment.
		 * VaultAppRoleAccessStrategySupport#extractDataFromBody(org.springframework.cloud
		 * .config.server.environment.VaultAppRoleAccessStrategy.VaultAppRoleResponse)
		 */
		@Override
		String extractDataFromBody(VaultAppRoleResponse body) {
			JsonNode nestedDataNode = body.getAuth() == null ? null
					: ((JsonNode) body.getAuth()).get("client_token");
			return nestedDataNode == null ? null : nestedDataNode.toString();
		}

	}

}
