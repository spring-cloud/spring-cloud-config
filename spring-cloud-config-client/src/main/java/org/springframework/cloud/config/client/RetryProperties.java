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
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

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
	 * Whether to retry failed requests to the Config Server. Only has an effect when
	 * spring.cloud.config.fail-fast is enabled.
	 */
	boolean enabled = false;

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
	 * Use a random exponential backoff policy.
	 */
	@Deprecated(since = "5.1.0", forRemoval = true)
	boolean useRandomPolicy = false;

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

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

	/**
	 * @return whether a random exponential backoff policy is used.
	 * @deprecated since 5.1.0 in favor of the deterministic exponential backoff.
	 */
	@Deprecated(since = "5.1.0", forRemoval = true)
	@DeprecatedConfigurationProperty(since = "5.1.0",
			reason = "Randomized backoff will be removed in favor of the deterministic exponential backoff.")
	public boolean isUseRandomPolicy() {
		return this.useRandomPolicy;
	}

	/**
	 * @param useRandomPolicy whether to use a random exponential backoff policy.
	 * @deprecated since 5.1.0 in favor of the deterministic exponential backoff.
	 */
	@Deprecated(since = "5.1.0", forRemoval = true)
	public void setUseRandomPolicy(boolean useRandomPolicy) {
		this.useRandomPolicy = useRandomPolicy;
	}

}
