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

import org.apache.commons.logging.Log;

import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryState;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.util.Assert;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;

public final class RetryTemplateFactory {

	private RetryTemplateFactory() {

	}

	public static RetryTemplate create(RetryProperties properties, Log log) {
		validateMaxAttempts(properties);
		Assert.isTrue(properties.getInitialInterval() >= 1, "Initial interval should be >= 1");
		Assert.isTrue(properties.getMultiplier() > 1, "Multiplier should be > 1");
		Assert.isTrue(properties.getMaxInterval() > properties.getInitialInterval(),
				"Max interval should be > than initial interval");
		return create(createBackOff(properties, properties.getInitialInterval(), properties.getMultiplier(),
				properties.getMaxInterval()), log);
	}

	/**
	 * Clamps the back off values like Spring Retry's {@code ExponentialBackOffPolicy},
	 * which legacy bootstrap used, instead of rejecting them.
	 */
	static RetryTemplate createForLegacyBootstrap(RetryProperties properties, Log log) {
		validateMaxAttempts(properties);
		return create(createBackOff(properties, Math.max(properties.getInitialInterval(), 1),
				Math.max(properties.getMultiplier(), 1.0), Math.max(properties.getMaxInterval(), 1)), log);
	}

	private static void validateMaxAttempts(RetryProperties properties) {
		Assert.isTrue(properties.getMaxAttempts() > 0, "Number of attempts should be positive");
	}

	private static RetryTemplate create(BackOff backOff, Log log) {
		// Spring Retry's default classifier retried Exception but not Error
		RetryPolicy retryPolicy = RetryPolicy.builder().includes(Exception.class).backOff(backOff).build();
		RetryTemplate retryTemplate = new RetryTemplate(retryPolicy);
		retryTemplate.setRetryListener(new RetryListener() {
			@Override
			public void onRetryableExecution(RetryPolicy policy, Retryable<?> retryable, RetryState retryState) {
				if (log.isDebugEnabled()) {
					log.debug("Retry: count=" + retryState.getRetryCount());
				}
			}
		});
		return retryTemplate;
	}

	@SuppressWarnings("removal")
	private static BackOff createBackOff(RetryProperties properties, long initialInterval, double multiplier,
			long maxInterval) {
		ExponentialBackOff backOff = new ExponentialBackOff();
		// max-attempts counts the initial invocation, the back off only counts retries
		backOff.setMaxAttempts(properties.getMaxAttempts() - 1L);
		backOff.setInitialInterval(initialInterval);
		backOff.setMultiplier(multiplier);
		backOff.setMaxInterval(maxInterval);
		return properties.isUseRandomPolicy() ? new ExponentialRandomBackOff(backOff) : backOff;
	}

}
