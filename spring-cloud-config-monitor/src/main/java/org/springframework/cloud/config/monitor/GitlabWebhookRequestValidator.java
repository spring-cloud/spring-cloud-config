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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.StringUtils;

/**
 * Validates webhook requests from GitLab using a plain secret token. GitLab sends the
 * configured secret token verbatim in the {@code X-Gitlab-Token} header. Comparison is
 * performed in constant time to prevent timing attacks.
 */
public class GitlabWebhookRequestValidator implements WebhookRequestValidator {

	private static final String X_GITLAB_TOKEN = "X-Gitlab-Token";

	private final String secret;

	public GitlabWebhookRequestValidator(String secret) {
		this.secret = secret;
	}

	@Override
	public boolean validate(ServletServerHttpRequest request) {
		HttpHeaders headers = request.getHeaders();
		String token = headers.getFirst(X_GITLAB_TOKEN);
		if (!StringUtils.hasText(token)) {
			return false;
		}
		return MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public boolean shouldValidate(ServletServerHttpRequest request) {
		return GitlabPropertyPathNotificationExtractor.isGitlabRequest(request.getHeaders());
	}

}
