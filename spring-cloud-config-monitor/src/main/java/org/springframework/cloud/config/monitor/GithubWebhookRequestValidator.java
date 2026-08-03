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

package org.springframework.cloud.config.monitor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.StringUtils;

public class GithubWebhookRequestValidator extends Sha256WebhookRequestValidator {

	private static final String X_HUB_SIGNATURE = "X-Hub-Signature-256";

	private static final String SIGNATURE_PREFIX = "sha256=";

	public GithubWebhookRequestValidator(String secret) {
		super(secret, X_HUB_SIGNATURE);
	}

	@Override
	protected String getSignature(ServletServerHttpRequest request) {
		HttpHeaders headers = request.getHeaders();
		if (headers.containsHeader(X_HUB_SIGNATURE)) {
			String header = headers.getFirst(X_HUB_SIGNATURE);
			// Signature header starts with sha256=
			if (StringUtils.hasText(header) && header.startsWith(SIGNATURE_PREFIX)) {
				return header.substring(header.indexOf("=") + 1);
			}
		}
		return null;
	}

	@Override
	public boolean shouldValidate(ServletServerHttpRequest request) {
		HttpHeaders headers = request.getHeaders();
		return GithubPropertyPathNotificationExtractor.isGithubRequest(headers);
	}

}
