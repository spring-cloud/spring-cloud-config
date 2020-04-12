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

package org.springframework.cloud.config.server.environment.secretManager;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

public class HttpHeaderGoogleConfigProvider implements GoogleConfigProvider {

	/**
	 * The Project ID Header admited to get the project name for google cloud secret
	 * manager.
	 */
	public static final String PROJECT_ID_HEADER = "X-Project-ID";

	/**
	 * The Config Token ID Header admited to get the access token from the client.
	 */
	public static final String ACCESS_TOKEN_HEADER = "X-Config-Token";

	private ObjectProvider<HttpServletRequest> httpRequest;

	public HttpHeaderGoogleConfigProvider(ObjectProvider<HttpServletRequest> request) {
		this.httpRequest = request;
	}

	@Override
	public String getValue(String key) {
		HttpServletRequest request = httpRequest.getIfAvailable();
		if (request == null) {
			throw new IllegalStateException("No HttpServletRequest available");
		}
		String value = request.getHeader(key);
		if (!StringUtils.hasLength(value)) {
			throw new IllegalArgumentException(
					"Missing required header in HttpServletRequest: " + key);
		}
		return value;
	}

}
