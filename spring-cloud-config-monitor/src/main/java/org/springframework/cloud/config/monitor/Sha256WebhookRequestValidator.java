/*
 * Copyright © 2012 Broadcom Inc. and/or its subsidiaries. All Rights Reserved.
 * Copyright 2012-present the original author or authors.
 */

package org.springframework.cloud.config.monitor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.StringUtils;

public abstract class Sha256WebhookRequestValidator implements WebhookRequestValidator {

	private static final Log LOG = LogFactory.getLog(Sha256WebhookRequestValidator.class);

	/** {@link javax.crypto.Mac} algorithm name for webhook signing. */
	private static final String HMAC_SHA_256 = "HmacSHA256";

	private final String secret;

	protected final String signatureHeader;

	public Sha256WebhookRequestValidator(String secret, String signatureHeader) {
		this.secret = secret;
		this.signatureHeader = signatureHeader;
	}

	@Override
	public boolean validate(ServletServerHttpRequest request) {
		String signature = getSignature(request);
		if (StringUtils.hasText(signature)) {
			try {
				return validateSha256(sign(request.getBody().readAllBytes(), secret), signature);
			}
			catch (Exception e) {
				LOG.warn("Failed to validate webhook signature", e);
				return false;
			}
		}

		return false;
	}

	static boolean validateSha256(String input, String expectedHash) throws NoSuchAlgorithmException {
		return MessageDigest.isEqual(input.getBytes(StandardCharsets.UTF_8),
				expectedHash.getBytes(StandardCharsets.UTF_8));
	}

	private static String bytesToHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

	static String sign(byte[] payload, String secret) throws Exception {
		Mac mac = Mac.getInstance(HMAC_SHA_256);
		SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256);
		mac.init(keySpec);
		byte[] digest = mac.doFinal(payload);
		return bytesToHex(digest);
	}

	protected abstract String getSignature(ServletServerHttpRequest request);

}
