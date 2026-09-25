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

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Ryan Baxter
 * @author Mahammad Eminov
 */
public class ConfigServiceBootstrapConfigurationRetryTest {

	private final AtomicInteger invocations = new AtomicInteger();

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
	public void retriesLocateUntilMaxAttemptsIsReached() {
		setup("spring.cloud.config.fail-fast=true", "spring.cloud.config.retry.enabled=true",
				"spring.cloud.config.retry.maxAttempts=3");

		assertThatThrownBy(this::locate).isInstanceOf(IllegalStateException.class).hasMessage("boom");

		assertThat(this.invocations).hasValue(3);
	}

	@Test
	public void doesNotRetryWhenFailFastIsNotSet() {
		setup("spring.cloud.config.retry.maxAttempts=3");

		assertThatThrownBy(this::locate).isInstanceOf(IllegalStateException.class).hasMessage("boom");

		assertThat(this.invocations).hasValue(1);
		assertThat(this.context.getBeanNamesForType(MethodInterceptor.class)).isEmpty();
	}

	@Test
	public void doesNotRetryByDefault() {
		setup("spring.cloud.config.fail-fast=true", "spring.cloud.config.retry.maxAttempts=3");

		assertThatThrownBy(this::locate).isInstanceOf(IllegalStateException.class).hasMessage("boom");

		assertThat(this.invocations).hasValue(1);
		assertThat(this.context.getBeanNamesForType(MethodInterceptor.class)).isEmpty();
	}

	@Test
	public void doesNotRetryWhenRetryIsDisabled() {
		setup("spring.cloud.config.fail-fast=true", "spring.cloud.config.retry.enabled=false",
				"spring.cloud.config.retry.maxAttempts=3");

		assertThatThrownBy(this::locate).isInstanceOf(IllegalStateException.class).hasMessage("boom");

		assertThat(this.invocations).hasValue(1);
		assertThat(this.context.getBeanNamesForType(MethodInterceptor.class)).isEmpty();
	}

	@Test
	public void retriesLocateCollectionUntilMaxAttemptsIsReached() {
		setup("spring.cloud.config.fail-fast=true", "spring.cloud.config.retry.enabled=true",
				"spring.cloud.config.retry.maxAttempts=3");

		assertThatThrownBy(() -> this.context.getBean(CountingPropertySourceLocator.class)
			.locateCollection(this.context.getEnvironment())).isInstanceOf(IllegalStateException.class)
			.hasMessage("boom");

		assertThat(this.invocations).hasValue(3);
	}

	@Test
	public void customRetryInterceptorBeanIsUsed() {
		AtomicInteger interceptions = new AtomicInteger();
		this.context.getDefaultListableBeanFactory()
			.registerSingleton("configServerRetryInterceptor", (MethodInterceptor) invocation -> {
				interceptions.incrementAndGet();
				return invocation.proceed();
			});
		setup("spring.cloud.config.fail-fast=true", "spring.cloud.config.retry.enabled=true",
				"spring.cloud.config.retry.maxAttempts=3");

		assertThatThrownBy(this::locate).isInstanceOf(IllegalStateException.class).hasMessage("boom");

		assertThat(interceptions).hasValue(1);
		assertThat(this.invocations).hasValue(1);
	}

	@Test
	public void acceptsInitialIntervalGreaterThanMaxInterval() {
		setup("spring.cloud.config.fail-fast=true", "spring.cloud.config.retry.enabled=true",
				"spring.cloud.config.retry.maxAttempts=3", "spring.cloud.config.retry.maxInterval=5");

		assertThatThrownBy(this::locate).isInstanceOf(IllegalStateException.class).hasMessage("boom");

		assertThat(this.invocations).hasValue(3);
	}

	@Test
	public void acceptsMultiplierOfOne() {
		setup("spring.cloud.config.fail-fast=true", "spring.cloud.config.retry.enabled=true",
				"spring.cloud.config.retry.maxAttempts=3", "spring.cloud.config.retry.multiplier=1.0");

		assertThatThrownBy(this::locate).isInstanceOf(IllegalStateException.class).hasMessage("boom");

		assertThat(this.invocations).hasValue(3);
	}

	@Test
	public void customRetryAdvisorBeanReplacesTheDefault() {
		this.context.register(CustomRetryAdvisorConfig.class);
		setup("spring.cloud.config.fail-fast=true", "spring.cloud.config.retry.enabled=true",
				"spring.cloud.config.retry.maxAttempts=3");

		assertThat(this.context.getBeansOfType(Advisor.class)).containsOnlyKeys("configServerRetryAdvisor");
		assertThat(this.context.getBean("configServerRetryAdvisor"))
			.isInstanceOf(StaticMethodMatcherPointcutAdvisor.class);

		assertThatThrownBy(this::locate).isInstanceOf(IllegalStateException.class).hasMessage("boom");

		assertThat(this.invocations).hasValue(1);
	}

	private void locate() {
		this.context.getBean(CountingPropertySourceLocator.class).locate(this.context.getEnvironment());
	}

	private void setup(String... env) {
		TestPropertyValues.of("spring.cloud.config.enabled=true", "spring.cloud.config.retry.initialInterval=10")
			.and(env)
			.applyTo(this.context);
		this.context.getDefaultListableBeanFactory().registerSingleton("locateInvocations", this.invocations);
		this.context.register(TestConfig.class, ConfigServiceBootstrapConfiguration.class);
		this.context.refresh();
	}

	@Configuration(proxyBeanMethods = false)
	static class TestConfig {

		@Bean
		CountingPropertySourceLocator countingPropertySourceLocator(ConfigClientProperties properties,
				AtomicInteger invocations) {
			return new CountingPropertySourceLocator(properties, invocations);
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomRetryAdvisorConfig {

		@Bean
		Advisor configServerRetryAdvisor() {
			return new StaticMethodMatcherPointcutAdvisor() {
				@Override
				public boolean matches(Method method, Class<?> targetClass) {
					return false;
				}
			};
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
