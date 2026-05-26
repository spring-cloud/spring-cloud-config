/*
 * Copyright © 2012 Broadcom Inc. and/or its subsidiaries. All Rights Reserved.
 * Copyright 2012-present the original author or authors.
 */

package org.springframework.cloud.config.monitor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.StringUtils;

/**
 * Validates webhook requests from Gitee. Gitee supports two authentication modes:
 * <ul>
 * <li><strong>Signature mode</strong> (recommended): Gitee sends an
 * {@code X-Gitee-Timestamp} header (milliseconds) alongside {@code X-Gitee-Token}. The
 * token is the Base64-encoded HMAC-SHA256 of {@code timestamp + "\n" + secret}, keyed by
 * the secret. Requests with a timestamp older than one hour are rejected.</li>
 * <li><strong>Password mode</strong>: When {@code X-Gitee-Timestamp} is absent, the
 * {@code X-Gitee-Token} header is compared directly against the configured secret using
 * constant-time comparison.</li>
 * </ul>
 */
public class GiteeWebhookRequestValidator implements WebhookRequestValidator {

	private static final Log LOG = LogFactory.getLog(GiteeWebhookRequestValidator.class);

	private static final String X_GITEE_TOKEN = "X-Gitee-Token";

	private static final String X_GITEE_TIMESTAMP = "X-Gitee-Timestamp";

	private static final String HMAC_SHA_256 = "HmacSHA256";

	/** Maximum allowed clock skew between Gitee server and this server. */
	private static final long MAX_TIMESTAMP_DELTA_MS = 3_600_000L;

	private final String secret;

	public GiteeWebhookRequestValidator(String secret) {
		this.secret = secret;
	}

	@Override
	public boolean validate(ServletServerHttpRequest request) {
		HttpHeaders headers = request.getHeaders();
		String token = headers.getFirst(X_GITEE_TOKEN);
		if (!StringUtils.hasText(token)) {
			return false;
		}

		String timestamp = headers.getFirst(X_GITEE_TIMESTAMP);
		if (StringUtils.hasText(timestamp)) {
			return validateSignatureMode(token, timestamp);
		}
		return validatePasswordMode(token);
	}

	private boolean validateSignatureMode(String token, String timestamp) {
		try {
			long ts = Long.parseLong(timestamp);
			if (Math.abs(System.currentTimeMillis() - ts) > MAX_TIMESTAMP_DELTA_MS) {
				LOG.warn("Gitee webhook timestamp is too old or too far in the future");
				return false;
			}
			String stringToSign = timestamp + "\n" + secret;
			Mac mac = Mac.getInstance(HMAC_SHA_256);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
			byte[] digest = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
			String expectedToken = Base64.getEncoder().encodeToString(digest);
			return MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.UTF_8),
					token.getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception e) {
			LOG.warn("Failed to validate Gitee webhook signature", e);
			return false;
		}
	}

	private boolean validatePasswordMode(String token) {
		return MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public boolean shouldValidate(ServletServerHttpRequest request) {
		return GiteePropertyPathNotificationExtractor.isGiteeRequest(request.getHeaders());
	}

}
