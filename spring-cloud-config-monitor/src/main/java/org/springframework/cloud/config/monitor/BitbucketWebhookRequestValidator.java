/*
 * Copyright © 2012 Broadcom Inc. and/or its subsidiaries. All Rights Reserved.
 * Copyright 2012-present the original author or authors.
 */

package org.springframework.cloud.config.monitor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.StringUtils;

/**
 * Validates webhook requests from Bitbucket Cloud and Bitbucket Server (Data Center)
 * using HMAC-SHA256. Both products send the hex-encoded HMAC-SHA256 digest of the raw
 * request body in the {@code X-Hub-Signature} header with a {@code sha256=} prefix.
 */
public class BitbucketWebhookRequestValidator extends Sha256WebhookRequestValidator {

	private static final String X_HUB_SIGNATURE = "X-Hub-Signature";

	private static final String SIGNATURE_PREFIX = "sha256=";

	public BitbucketWebhookRequestValidator(String secret) {
		super(secret, X_HUB_SIGNATURE);
	}

	@Override
	protected String getSignature(ServletServerHttpRequest request) {
		HttpHeaders headers = request.getHeaders();
		String header = headers.getFirst(X_HUB_SIGNATURE);
		if (StringUtils.hasText(header) && header.startsWith(SIGNATURE_PREFIX)) {
			return header.substring(SIGNATURE_PREFIX.length());
		}
		return null;
	}

	@Override
	public boolean shouldValidate(ServletServerHttpRequest request) {
		return BitbucketPropertyPathNotificationExtractor.isBitbucketRequest(request.getHeaders());
	}

}
