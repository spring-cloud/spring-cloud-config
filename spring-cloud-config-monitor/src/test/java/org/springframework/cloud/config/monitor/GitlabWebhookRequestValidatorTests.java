/*
 * Copyright © 2012 Broadcom Inc. and/or its subsidiaries. All Rights Reserved.
 * Copyright 2012-present the original author or authors.
 */

package org.springframework.cloud.config.monitor;

import org.junit.jupiter.api.Test;

import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GitlabWebhookRequestValidatorTests {

	private static final String SECRET = "webhook-secret";

	private final GitlabWebhookRequestValidator validator = new GitlabWebhookRequestValidator(SECRET);

	// shouldValidate

	@Test
	void shouldValidate_pushHookEvent() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("X-Gitlab-Event", "Push Hook");
		assertThat(validator.shouldValidate(wrap(req))).isTrue();
	}

	@Test
	void shouldValidate_issueHookEvent() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("X-Gitlab-Event", "Issue Hook");
		assertThat(validator.shouldValidate(wrap(req))).isFalse();
	}

	@Test
	void shouldValidate_noHeader() {
		assertThat(validator.shouldValidate(wrap(new MockHttpServletRequest()))).isFalse();
	}

	// validate

	@Test
	void validate_correctToken() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("X-Gitlab-Token", SECRET);
		assertThat(validator.validate(wrap(req))).isTrue();
	}

	@Test
	void validate_wrongToken() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("X-Gitlab-Token", "wrong-secret");
		assertThat(validator.validate(wrap(req))).isFalse();
	}

	@Test
	void validate_missingToken() {
		assertThat(validator.validate(wrap(new MockHttpServletRequest()))).isFalse();
	}

	private static ServletServerHttpRequest wrap(MockHttpServletRequest req) {
		return new ServletServerHttpRequest(req);
	}

}
