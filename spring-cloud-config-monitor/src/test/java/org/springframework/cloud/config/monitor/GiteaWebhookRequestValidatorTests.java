/*
 * Copyright © 2012 Broadcom Inc. and/or its subsidiaries. All Rights Reserved.
 * Copyright 2012-present the original author or authors.
 */

package org.springframework.cloud.config.monitor;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GiteaWebhookRequestValidatorTests {

	private static final String SECRET = "webhook-secret";

	private static final byte[] BODY = "{\"test\":\"payload\"}".getBytes(StandardCharsets.UTF_8);

	private final GiteaWebhookRequestValidator validator = new GiteaWebhookRequestValidator(SECRET);

	// shouldValidate

	@Test
	void shouldValidate_pushEvent() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("X-Gitea-Event", "push");
		assertThat(validator.shouldValidate(wrap(req))).isTrue();
	}

	@Test
	void shouldValidate_otherEvent() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("X-Gitea-Event", "issues");
		assertThat(validator.shouldValidate(wrap(req))).isFalse();
	}

	@Test
	void shouldValidate_noHeader() {
		assertThat(validator.shouldValidate(wrap(new MockHttpServletRequest()))).isFalse();
	}

	// validate

	@Test
	void validate_validSignature() throws Exception {
		String signature = Sha256WebhookRequestValidator.sign(BODY, SECRET);
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.setContent(BODY);
		req.addHeader("X-Gitea-Signature", signature);
		assertThat(validator.validate(wrap(req))).isTrue();
	}

	@Test
	void validate_invalidSignature() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.setContent(BODY);
		req.addHeader("X-Gitea-Signature", "deadbeef");
		assertThat(validator.validate(wrap(req))).isFalse();
	}

	@Test
	void validate_missingSignatureHeader() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.setContent(BODY);
		assertThat(validator.validate(wrap(req))).isFalse();
	}

	private static ServletServerHttpRequest wrap(MockHttpServletRequest req) {
		return new ServletServerHttpRequest(req);
	}

}
