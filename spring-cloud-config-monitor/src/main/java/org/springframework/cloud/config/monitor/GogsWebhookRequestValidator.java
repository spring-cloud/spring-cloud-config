/*
 * Copyright © 2012 Broadcom Inc. and/or its subsidiaries. All Rights Reserved.
 * Copyright 2012-present the original author or authors.
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
