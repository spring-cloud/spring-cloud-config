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
import java.time.Duration;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.resilience.retry.MethodRetrySpec;
import org.springframework.resilience.retry.SimpleRetryInterceptor;

/**
 * @author Dave Syer
 * @author Tristan Hanson
 *
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties
public class ConfigServiceBootstrapConfiguration {

	@Autowired
	private ConfigurableEnvironment environment;

	@Bean
	@ConditionalOnMissingBean
	public ConfigClientProperties configClientProperties() {
		ConfigClientProperties client = new ConfigClientProperties(this.environment);
		return client;
	}

	@Bean
	@ConditionalOnMissingBean(ConfigServicePropertySourceLocator.class)
	@ConditionalOnProperty(name = ConfigClientProperties.PREFIX + ".enabled", matchIfMissing = true)
	public ConfigServicePropertySourceLocator configServicePropertySource(ConfigClientProperties properties) {
		return new ConfigServicePropertySourceLocator(properties);
	}

	@ConditionalOnProperty(ConfigClientProperties.PREFIX + ".fail-fast")
	@ConditionalOnProperty(value = RetryProperties.PREFIX + ".enabled", matchIfMissing = true)
	@Configuration(proxyBeanMethods = false)
	@Import(AopAutoConfiguration.class)
	@EnableConfigurationProperties(RetryProperties.class)
	protected static class RetryConfiguration {

		@Bean
		@ConditionalOnMissingBean(name = "configServerRetryInterceptor")
		public SimpleRetryInterceptor configServerRetryInterceptor(RetryProperties properties) {
			MethodRetrySpec spec = new MethodRetrySpec((method, throwable) -> true,
					// 'maxAttempts' counts the initial call, 'maxRetries' does not
					properties.getMaxAttempts() - 1, Duration.ofMillis(properties.getInitialInterval()),
					RetryTemplateFactory.jitter(properties), properties.getMultiplier(),
					Duration.ofMillis(properties.getMaxInterval()));
			return new SimpleRetryInterceptor(spec);
		}

		/**
		 * Applies the retry interceptor to the methods that used to carry
		 * {@code @Retryable(interceptor = "configServerRetryInterceptor")}.
		 */
		@Bean
		@ConditionalOnMissingBean(name = "configServerRetryAdvisor")
		public Advisor configServerRetryAdvisor(SimpleRetryInterceptor configServerRetryInterceptor) {
			StaticMethodMatcherPointcut pointcut = new StaticMethodMatcherPointcut() {
				@Override
				public boolean matches(Method method, Class<?> targetClass) {
					if (ConfigServicePropertySourceLocator.class.isAssignableFrom(targetClass)) {
						return method.getName().startsWith("locate");
					}
					return ConfigServerInstanceProvider.class.isAssignableFrom(targetClass)
							&& "getConfigServerInstances".equals(method.getName());
				}
			};
			return new DefaultPointcutAdvisor(pointcut, configServerRetryInterceptor);
		}

	}

}
