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

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.util.StringUtils;

/**
 * @author Scott Frederick
 */
public class HttpRequestConfigTokenProvider implements ConfigTokenProvider {

	private ObjectProvider<HttpServletRequest> httpRequest;

	public HttpRequestConfigTokenProvider(
			ObjectProvider<HttpServletRequest> httpRequest) {
		this.httpRequest = httpRequest;
	}

	@Override
	public String getToken() {
		HttpServletRequest request = httpRequest.getIfAvailable();
		if (request == null) {
			throw new IllegalStateException("No HttpServletRequest available");
		}

		String token = request.getHeader(ConfigClientProperties.TOKEN_HEADER);
		if (!StringUtils.hasLength(token)) {
			throw new IllegalArgumentException(
					"Missing required header in HttpServletRequest: "
							+ ConfigClientProperties.TOKEN_HEADER);
		}

		return token;
	}

}
