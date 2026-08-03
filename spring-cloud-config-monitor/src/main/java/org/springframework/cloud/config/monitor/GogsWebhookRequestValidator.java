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

/**
 * Validates webhook requests from Gogs using HMAC-SHA256. Gogs sends the hex-encoded
 * HMAC-SHA256 digest of the raw request body in the {@code X-Gogs-Signature} header
 * (without any prefix).
 */
public class GogsWebhookRequestValidator extends Sha256WebhookRequestValidator {

	private static final String X_GOGS_SIGNATURE = "X-Gogs-Signature";

	public GogsWebhookRequestValidator(String secret) {
		super(secret, X_GOGS_SIGNATURE);
	}

	@Override
	protected String getSignature(ServletServerHttpRequest request) {
		HttpHeaders headers = request.getHeaders();
		String signature = headers.getFirst(X_GOGS_SIGNATURE);
		return StringUtils.hasText(signature) ? signature : null;
	}

	@Override
	public boolean shouldValidate(ServletServerHttpRequest request) {
		return GogsPropertyPathNotificationExtractor.isGogsRequest(request.getHeaders());
	}

}
