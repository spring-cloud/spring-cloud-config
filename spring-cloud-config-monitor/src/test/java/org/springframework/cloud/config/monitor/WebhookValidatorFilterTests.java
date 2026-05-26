/*
 * Copyright © 2012 Broadcom Inc. and/or its subsidiaries. All Rights Reserved.
 * Copyright 2012-present the original author or authors.
 */

package org.springframework.cloud.config.monitor;

import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookValidatorFilterTests {

	private final MockHttpServletRequest request = new MockHttpServletRequest();

	private final MockHttpServletResponse response = new MockHttpServletResponse();

	private final MockFilterChain chain = new MockFilterChain();

	@Test
	void noMatchingValidatorRejects() throws Exception {
		WebhookRequestValidator neverMatches = new WebhookRequestValidator() {
			@Override
			public boolean shouldValidate(ServletServerHttpRequest request) {
				return false;
			}

			@Override
			public boolean validate(ServletServerHttpRequest request) {
				return true;
			}
		};

		WebhookValidatorFilter filter = new WebhookValidatorFilter(List.of(neverMatches));
		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
		assertThat(chain.getRequest()).isNull();
	}

	@Test
	void noConfiguredValidatorsRejects() throws Exception {
		WebhookValidatorFilter filter = new WebhookValidatorFilter(List.of());
		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
		assertThat(chain.getRequest()).isNull();
	}

	@Test
	void matchingValidatorThatPassesAllowsThrough() throws Exception {
		WebhookRequestValidator alwaysValid = new WebhookRequestValidator() {
			@Override
			public boolean shouldValidate(ServletServerHttpRequest request) {
				return true;
			}

			@Override
			public boolean validate(ServletServerHttpRequest request) {
				return true;
			}
		};

		WebhookValidatorFilter filter = new WebhookValidatorFilter(List.of(alwaysValid));
		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
		assertThat(chain.getRequest()).isNotNull();
	}

	@Test
	void matchingValidatorThatFailsRejects() throws Exception {
		WebhookRequestValidator alwaysFails = new WebhookRequestValidator() {
			@Override
			public boolean shouldValidate(ServletServerHttpRequest request) {
				return true;
			}

			@Override
			public boolean validate(ServletServerHttpRequest request) {
				return false;
			}
		};

		WebhookValidatorFilter filter = new WebhookValidatorFilter(List.of(alwaysFails));
		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
		assertThat(chain.getRequest()).isNull();
	}

}
