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

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Randomizes the exponential interval upwards, into
 * {@code [interval, interval * multiplier)}, as Spring Retry's
 * {@code ExponentialRandomBackOffPolicy} did. {@link ExponentialBackOff#setJitter(long)}
 * randomizes symmetrically around the interval instead, so it is not equivalent.
 *
 * @author Mahammad Eminov
 */
class ExponentialRandomBackOff implements BackOff {

	private final ExponentialBackOff delegate;

	ExponentialRandomBackOff(ExponentialBackOff delegate) {
		this.delegate = delegate;
	}

	@Override
	public BackOffExecution start() {
		BackOffExecution execution = this.delegate.start();
		double multiplier = this.delegate.getMultiplier();
		long maxInterval = this.delegate.getMaxInterval();
		return () -> {
			long interval = execution.nextBackOff();
			if (interval == BackOffExecution.STOP) {
				return BackOffExecution.STOP;
			}
			long randomized = (long) (interval * (1 + ThreadLocalRandom.current().nextFloat() * (multiplier - 1)));
			return Math.min(randomized, maxInterval);
		};
	}

}
