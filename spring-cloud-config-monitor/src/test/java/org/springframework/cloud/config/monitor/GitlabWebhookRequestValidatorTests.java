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
