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

import org.junit.jupiter.api.Test;

import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class BitbucketWebhookRequestValidatorTests {

	private static final String SECRET = "webhook-secret";

	private static final byte[] BODY = "{\"test\":\"payload\"}".getBytes(StandardCharsets.UTF_8);

	private final BitbucketWebhookRequestValidator validator = new BitbucketWebhookRequestValidator(SECRET);

	// shouldValidate

	@Test
	void shouldValidate_cloudPushEvent() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("X-Event-Key", "repo:push");
		req.addHeader("X-Hook-UUID", "abc-123");
		assertThat(validator.shouldValidate(wrap(req))).isTrue();
	}

	@Test
	void shouldValidate_cloudPullRequestFulfilled() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("X-Event-Key", "pullrequest:fulfilled");
		req.addHeader("X-Hook-UUID", "abc-123");
		assertThat(validator.shouldValidate(wrap(req))).isTrue();
	}

	@Test
	void shouldValidate_serverRefsChanged() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("X-Event-Key", "repo:refs_changed");
		req.addHeader("X-Request-Id", "req-456");
		assertThat(validator.shouldValidate(wrap(req))).isTrue();
	}

	@Test
	void shouldValidate_serverPullRequestMerged() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("X-Event-Key", "pr:merged");
		req.addHeader("X-Request-Id", "req-456");
		assertThat(validator.shouldValidate(wrap(req))).isTrue();
	}

	@Test
	void shouldValidate_unknownEvent() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("X-Event-Key", "issue:created");
		req.addHeader("X-Hook-UUID", "abc-123");
		assertThat(validator.shouldValidate(wrap(req))).isFalse();
	}

	@Test
	void shouldValidate_noEventKey() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("X-Hook-UUID", "abc-123");
		assertThat(validator.shouldValidate(wrap(req))).isFalse();
	}

	// validate

	@Test
	void validate_validSignature() throws Exception {
		String signature = "sha256=" + Sha256WebhookRequestValidator.sign(BODY, SECRET);
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.setContent(BODY);
		req.addHeader("X-Hub-Signature", signature);
		assertThat(validator.validate(wrap(req))).isTrue();
	}

	@Test
	void validate_invalidSignature() throws Exception {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.setContent(BODY);
		req.addHeader("X-Hub-Signature", "sha256=deadbeef");
		assertThat(validator.validate(wrap(req))).isFalse();
	}

	@Test
	void validate_missingSignatureHeader() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.setContent(BODY);
		assertThat(validator.validate(wrap(req))).isFalse();
	}

	@Test
	void validate_signatureWithoutPrefix() throws Exception {
		String signature = Sha256WebhookRequestValidator.sign(BODY, SECRET);
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.setContent(BODY);
		req.addHeader("X-Hub-Signature", signature);
		assertThat(validator.validate(wrap(req))).isFalse();
	}

	private static ServletServerHttpRequest wrap(MockHttpServletRequest req) {
		return new ServletServerHttpRequest(req);
	}

}
