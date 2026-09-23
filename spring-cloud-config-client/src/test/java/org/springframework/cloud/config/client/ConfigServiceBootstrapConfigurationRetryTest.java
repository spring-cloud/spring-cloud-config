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

package org.springframework.cloud.config.client;

import java.lang.reflect.Field;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.aop.Advisor;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.resilience.retry.MethodRetrySpec;
import org.springframework.resilience.retry.SimpleRetryInterceptor;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ryan Baxter
 */
public class ConfigServiceBootstrapConfigurationRetryTest {

	private AnnotationConfigApplicationContext context;

	@BeforeEach
	public void setUp() {
		this.context = new AnnotationConfigApplicationContext();
	}

	@AfterEach
	public void tearDown() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void exponentialBackoff() {
		MethodRetrySpec spec = retrySpec("spring.cloud.config.enabled=true", "spring.cloud.config.fail-fast=true");

		// defaults from RetryProperties, 'maxAttempts' of 6 means 5 retries
		assertThat(spec.maxRetries()).isEqualTo(5);
		assertThat(spec.delay()).isEqualTo(Duration.ofMillis(1000));
		assertThat(spec.maxDelay()).isEqualTo(Duration.ofMillis(2000));
		assertThat(spec.multiplier()).isEqualTo(1.1);
		assertThat(spec.jitter()).isZero();
	}

	@Test
	public void exponentialRandomBackoff() {
		MethodRetrySpec spec = retrySpec("spring.cloud.config.enabled=true", "spring.cloud.config.fail-fast=true",
				"spring.cloud.config.retry.useRandomPolicy=true");

		// jitter approximates the interval spread of the old
		// ExponentialRandomBackOffPolicy
		assertThat(spec.jitter()).isEqualTo(Duration.ofMillis(100));
		assertThat(spec.delay()).isEqualTo(Duration.ofMillis(1000));
		assertThat(spec.multiplier()).isEqualTo(1.1);
	}

	@Test
	public void retryAdvisorIsRegistered() {
		TestPropertyValues.of("spring.cloud.config.enabled=true", "spring.cloud.config.fail-fast=true")
			.applyTo(this.context);
		this.context.register(ConfigServiceBootstrapConfiguration.class);
		this.context.refresh();

		assertThat(this.context.getBean("configServerRetryAdvisor", Advisor.class)).isNotNull();
	}

	private MethodRetrySpec retrySpec(String... properties) {
		TestPropertyValues.of(properties).applyTo(this.context);
		this.context.register(ConfigServiceBootstrapConfiguration.class);
		this.context.refresh();

		SimpleRetryInterceptor interceptor = this.context.getBean(SimpleRetryInterceptor.class);
		Field retrySpecField = ReflectionUtils.findField(SimpleRetryInterceptor.class, "retrySpec");
		retrySpecField.setAccessible(true);
		return (MethodRetrySpec) ReflectionUtils.getField(retrySpecField, interceptor);
	}

}
