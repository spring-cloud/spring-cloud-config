/*
 * Copyright 2013-2024 the original author or authors.
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.support.RetryTemplate;
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
	public void exponentialBackoffPolicy() {
		TestPropertyValues.of("spring.cloud.config.enabled=true", "spring.cloud.config.fail-fast=true")
				.applyTo(this.context);
		this.context.register(ConfigServiceBootstrapConfiguration.class);
		this.context.refresh();

		RetryOperationsInterceptor retryOperationsInterceptor = this.context.getBean(RetryOperationsInterceptor.class);
		Field retryOperationsField = ReflectionUtils.findField(RetryOperationsInterceptor.class, "retryOperations");
		retryOperationsField.setAccessible(true);

		RetryTemplate retryTemplate = (RetryTemplate) ReflectionUtils.getField(retryOperationsField,
				retryOperationsInterceptor);
		Field backOffPolicyField = ReflectionUtils.findField(RetryTemplate.class, "backOffPolicy");
		backOffPolicyField.setAccessible(true);

		BackOffPolicy backOffPolicy = (BackOffPolicy) ReflectionUtils.getField(backOffPolicyField, retryTemplate);
		assertThat(backOffPolicy).isNotNull();
		assertThat(backOffPolicy).isInstanceOf(ExponentialBackOffPolicy.class);
	}

	@Test
	public void exponentialRandomBackoffPolicy() {
		TestPropertyValues.of("spring.cloud.config.enabled=true", "spring.cloud.config.fail-fast=true",
				"spring.cloud.config.retry.useRandomPolicy=true").applyTo(this.context);
		this.context.register(ConfigServiceBootstrapConfiguration.class);
		this.context.refresh();

		RetryOperationsInterceptor retryOperationsInterceptor = this.context.getBean(RetryOperationsInterceptor.class);
		Field retryOperationsField = ReflectionUtils.findField(RetryOperationsInterceptor.class, "retryOperations");
		retryOperationsField.setAccessible(true);

		RetryTemplate retryTemplate = (RetryTemplate) ReflectionUtils.getField(retryOperationsField,
				retryOperationsInterceptor);
		Field backOffPolicyField = ReflectionUtils.findField(RetryTemplate.class, "backOffPolicy");
		backOffPolicyField.setAccessible(true);

		BackOffPolicy backOffPolicy = (BackOffPolicy) ReflectionUtils.getField(backOffPolicyField, retryTemplate);
		assertThat(backOffPolicy).isNotNull();
		assertThat(backOffPolicy).isInstanceOf(ExponentialRandomBackOffPolicy.class);
	}

}
