/*
 * Copyright © 2012 Broadcom Inc. and/or its subsidiaries. All Rights Reserved.
 * Copyright 2012-present the original author or authors.
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
