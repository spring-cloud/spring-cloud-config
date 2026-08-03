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
