/*
 * Copyright © 2012 Broadcom Inc. and/or its subsidiaries. All Rights Reserved.
 * Copyright 2012-present the original author or authors.
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
