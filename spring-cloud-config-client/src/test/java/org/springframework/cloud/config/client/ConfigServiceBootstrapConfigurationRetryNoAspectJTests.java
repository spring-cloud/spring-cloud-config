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

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.cloud.test.ClassPathExclusions;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Without AspectJ on the classpath, Spring Boot registers an auto proxy creator that only
 * applies infrastructure role advisors, so the retry advisor has to be one.
 *
 * @author Mahammad Eminov
 */
@ClassPathExclusions({ "aspectjweaver-*.jar" })
public class ConfigServiceBootstrapConfigurationRetryNoAspectJTests {

	private final AtomicInteger invocations = new AtomicInteger();

	private AnnotationConfigApplicationContext context;

	@AfterEach
	public void tearDown() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void retriesLocateUntilMaxAttemptsIsReached() {
		this.context = new AnnotationConfigApplicationContext();
		TestPropertyValues
			.of("spring.cloud.config.enabled=true", "spring.cloud.config.fail-fast=true",
					"spring.cloud.config.retry.enabled=true", "spring.cloud.config.retry.maxAttempts=3",
					"spring.cloud.config.retry.initialInterval=10")
			.applyTo(this.context);
		this.context.getDefaultListableBeanFactory().registerSingleton("locateInvocations", this.invocations);
		this.context.register(TestConfig.class, ConfigServiceBootstrapConfiguration.class);
		this.context.refresh();

		assertThatThrownBy(
				() -> this.context.getBean(CountingPropertySourceLocator.class).locate(this.context.getEnvironment()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("boom");

		assertThat(this.invocations).hasValue(3);
	}

	@Configuration(proxyBeanMethods = false)
	static class TestConfig {

		@Bean
		CountingPropertySourceLocator countingPropertySourceLocator(ConfigClientProperties properties,
				AtomicInteger invocations) {
			return new CountingPropertySourceLocator(properties, invocations);
		}

	}

	static class CountingPropertySourceLocator extends ConfigServicePropertySourceLocator {

		private final AtomicInteger invocations;

		CountingPropertySourceLocator(ConfigClientProperties properties, AtomicInteger invocations) {
			super(properties);
			this.invocations = invocations;
		}

		@Override
		public PropertySource<?> locate(Environment environment) {
			this.invocations.incrementAndGet();
			throw new IllegalStateException("boom");
		}

	}

}
