/*
 * Copyright © 2012 Broadcom Inc. and/or its subsidiaries. All Rights Reserved.
 * Copyright 2012-present the original author or authors.
 */

package org.springframework.cloud.config.monitor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.StringUtils;

/**
 * Validates webhook requests from Gitea using HMAC-SHA256. Gitea sends the hex-encoded
 * HMAC-SHA256 digest of the raw request body in the {@code X-Gitea-Signature} header
 * (without any prefix).
 */
public class GiteaWebhookRequestValidator extends Sha256WebhookRequestValidator {

	private static final String X_GITEA_SIGNATURE = "X-Gitea-Signature";

	public GiteaWebhookRequestValidator(String secret) {
		super(secret, X_GITEA_SIGNATURE);
	}

	@Override
	protected String getSignature(ServletServerHttpRequest request) {
		HttpHeaders headers = request.getHeaders();
		String signature = headers.getFirst(X_GITEA_SIGNATURE);
		return StringUtils.hasText(signature) ? signature : null;
	}

	@Override
	public boolean shouldValidate(ServletServerHttpRequest request) {
		return GiteaPropertyPathNotificationExtractor.isGiteaRequest(request.getHeaders());
	}

}
