/*
 * Copyright © 2012 Broadcom Inc. and/or its subsidiaries. All Rights Reserved.
 * Copyright 2012-present the original author or authors.
 */

package org.springframework.cloud.config.monitor;

import org.springframework.http.server.ServletServerHttpRequest;

public interface WebhookRequestValidator {

	/** Rejects all webhook requests. */
	WebhookRequestValidator INVALID_WEBHOOK_REQUEST = new InvalidWebhookRequestValidator();

	boolean validate(ServletServerHttpRequest request);

	boolean shouldValidate(ServletServerHttpRequest request);

	class InvalidWebhookRequestValidator implements WebhookRequestValidator {

		@Override
		public boolean validate(ServletServerHttpRequest request) {
			return false;
		}

		@Override
		public boolean shouldValidate(ServletServerHttpRequest request) {
			return true;
		}

	}

}
