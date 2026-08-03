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
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GiteeWebhookRequestValidatorTests {

	private static final String SECRET = "webhook-secret";

	private final GiteeWebhookRequestValidator validator = new GiteeWebhookRequestValidator(SECRET);

	// shouldValidate

	@Test
	void shouldValidate_oschinaHeader() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("x-git-oschina-event", "Push Hook");
		assertThat(validator.shouldValidate(wrap(req))).isTrue();
	}

	@Test
	void shouldValidate_giteeHeader() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("X-Gitee-Event", "Push Hook");
		assertThat(validator.shouldValidate(wrap(req))).isTrue();
	}

	@Test
	void shouldValidate_otherEvent() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("X-Gitee-Event", "Issue Hook");
		assertThat(validator.shouldValidate(wrap(req))).isFalse();
	}

	@Test
	void shouldValidate_noHeader() {
		assertThat(validator.shouldValidate(wrap(new MockHttpServletRequest()))).isFalse();
	}

	// validate — signature mode

	@Test
	void validate_signatureMode_valid() throws Exception {
		String timestamp = String.valueOf(System.currentTimeMillis());
		String token = computeSignatureToken(timestamp, SECRET);
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("X-Gitee-Token", token);
		req.addHeader("X-Gitee-Timestamp", timestamp);
		assertThat(validator.validate(wrap(req))).isTrue();
	}

	@Test
	void validate_signatureMode_expiredTimestamp() throws Exception {
		long twoHoursAgo = System.currentTimeMillis() - 7_200_000L;
		String timestamp = String.valueOf(twoHoursAgo);
		String token = computeSignatureToken(timestamp, SECRET);
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("X-Gitee-Token", token);
		req.addHeader("X-Gitee-Timestamp", timestamp);
		assertThat(validator.validate(wrap(req))).isFalse();
	}

	@Test
	void validate_signatureMode_invalidToken() {
		String timestamp = String.valueOf(System.currentTimeMillis());
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("X-Gitee-Token", "not-a-valid-token");
		req.addHeader("X-Gitee-Timestamp", timestamp);
		assertThat(validator.validate(wrap(req))).isFalse();
	}

	// validate — password mode

	@Test
	void validate_passwordMode_correctSecret() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("X-Gitee-Token", SECRET);
		assertThat(validator.validate(wrap(req))).isTrue();
	}

	@Test
	void validate_passwordMode_wrongSecret() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.addHeader("X-Gitee-Token", "wrong-secret");
		assertThat(validator.validate(wrap(req))).isFalse();
	}

	@Test
	void validate_missingToken() {
		assertThat(validator.validate(wrap(new MockHttpServletRequest()))).isFalse();
	}

	private static String computeSignatureToken(String timestamp, String secret) throws Exception {
		String stringToSign = timestamp + "\n" + secret;
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
	}

	private static ServletServerHttpRequest wrap(MockHttpServletRequest req) {
		return new ServletServerHttpRequest(req);
	}

}
