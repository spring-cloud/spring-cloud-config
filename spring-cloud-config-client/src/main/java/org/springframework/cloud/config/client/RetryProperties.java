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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Dave Syer
 *
 */
@ConfigurationProperties(RetryProperties.PREFIX)
public class RetryProperties {

	/**
	 * ConfigurationProperties prefix.
	 */
	public static final String PREFIX = "spring.cloud.config.retry";

	/**
	 * Initial retry interval in milliseconds.
	 */
	long initialInterval = 1000;

	/**
	 * Multiplier for next interval.
	 */
	double multiplier = 1.1;

	/**
	 * Maximum interval for backoff.
	 */
	long maxInterval = 2000;

	/**
	 * Maximum number of attempts.
	 */
	int maxAttempts = 6;

	/**
	 * Whether retry is enabled. Retry only applies when fail-fast is also enabled.
	 */
	boolean enabled = true;

	/**
	 * Use a random exponential backoff policy.
	 * @deprecated since the move to Spring Framework's retry support, which applies
	 * jitter symmetrically around the interval rather than randomising upwards from it.
	 * The interval spread is approximated, but the distribution is not identical.
	 */
	@Deprecated(since = "5.0.6", forRemoval = true)
	boolean useRandomPolicy = false;

	public long getInitialInterval() {
		return this.initialInterval;
	}

	public void setInitialInterval(long initialInterval) {
		this.initialInterval = initialInterval;
	}

	public double getMultiplier() {
		return this.multiplier;
	}

	public void setMultiplier(double multiplier) {
		this.multiplier = multiplier;
	}

	public long getMaxInterval() {
		return this.maxInterval;
	}

	public void setMaxInterval(long maxInterval) {
		this.maxInterval = maxInterval;
	}

	public int getMaxAttempts() {
		return this.maxAttempts;
	}

	public void setMaxAttempts(int maxAttempts) {
		this.maxAttempts = maxAttempts;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	@Deprecated(since = "5.0.6", forRemoval = true)
	public boolean isUseRandomPolicy() {
		return this.useRandomPolicy;
	}

	@Deprecated(since = "5.0.6", forRemoval = true)
	public void setUseRandomPolicy(boolean useRandomPolicy) {
		this.useRandomPolicy = useRandomPolicy;
	}

}
