/*
 * Copyright 2014-present the original author or authors.
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

package org.springframework.cloud.config.client;

import java.time.Duration;

import org.apache.commons.logging.Log;

import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryState;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;

public final class RetryTemplateFactory {

	private RetryTemplateFactory() {

	}

	public static RetryTemplate create(RetryProperties properties, Log log) {
		RetryPolicy.Builder policy = RetryPolicy.builder()
			// 'maxAttempts' counts the initial call, 'maxRetries' does not
			.maxRetries(properties.getMaxAttempts() - 1)
			.delay(Duration.ofMillis(properties.getInitialInterval()))
			.multiplier(properties.getMultiplier())
			.maxDelay(Duration.ofMillis(properties.getMaxInterval()));

		Duration jitter = jitter(properties);
		if (!jitter.isZero()) {
			policy.jitter(jitter);
		}

		RetryTemplate retryTemplate = new RetryTemplate(policy.build());
		retryTemplate.setRetryListener(new LoggingRetryListener(log));
		return retryTemplate;
	}

	/**
	 * Approximates the old {@code ExponentialRandomBackOffPolicy}, which randomised each
	 * interval within {@code [interval, interval * multiplier)}. Framework applies jitter
	 * symmetrically around the interval, so this matches the spread but not the centre.
	 */
	static Duration jitter(RetryProperties properties) {
		if (!properties.isUseRandomPolicy()) {
			return Duration.ZERO;
		}
		return Duration.ofMillis((long) (properties.getInitialInterval() * (properties.getMultiplier() - 1)));
	}

	/**
	 * Restores the retry logging that {@code RetryTemplate.Builder#withLogger} used to
	 * provide.
	 */
	private static final class LoggingRetryListener implements RetryListener {

		private final Log log;

		private LoggingRetryListener(Log log) {
			this.log = log;
		}

		@Override
		public void beforeRetry(RetryPolicy retryPolicy, Retryable<?> retryable, RetryState retryState) {
			if (this.log.isDebugEnabled()) {
				this.log.debug("Retry: count=" + retryState.getRetryCount());
			}
		}

	}

}
