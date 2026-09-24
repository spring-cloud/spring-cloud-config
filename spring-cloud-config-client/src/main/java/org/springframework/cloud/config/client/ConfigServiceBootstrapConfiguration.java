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
import java.lang.reflect.UndeclaredThrowableException;

import org.aopalliance.intercept.MethodInterceptor;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ProxyType;
import org.springframework.context.annotation.Proxyable;
import org.springframework.context.annotation.Role;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.retry.RetryTemplate;

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
	@Proxyable(ProxyType.TARGET_CLASS)
	public ConfigServicePropertySourceLocator configServicePropertySource(ConfigClientProperties properties) {
		return new ConfigServicePropertySourceLocator(properties);
	}

	@ConditionalOnProperty(ConfigClientProperties.PREFIX + ".fail-fast")
	@ConditionalOnProperty(name = RetryProperties.PREFIX + ".enabled", matchIfMissing = true)
	@Configuration(proxyBeanMethods = false)
	@Import(AopAutoConfiguration.class)
	@EnableConfigurationProperties(RetryProperties.class)
	protected static class RetryConfiguration {

		@Bean
		@ConditionalOnMissingBean(name = "configServerRetryInterceptor")
		public MethodInterceptor configServerRetryInterceptor(RetryProperties properties) {
			RetryTemplate retryTemplate = RetryTemplateFactory.createForLegacyBootstrap(properties,
					LogFactory.getLog(ConfigServiceBootstrapConfiguration.class));
			return invocation -> retryTemplate.invoke(() -> {
				try {
					return invocation.proceed();
				}
				catch (RuntimeException | Error ex) {
					throw ex;
				}
				catch (Throwable ex) {
					throw new UndeclaredThrowableException(ex);
				}
			});
		}

		@Bean
		@ConditionalOnMissingBean(name = "configServerRetryAdvisor")
		@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
		public Advisor configServerRetryAdvisor(
				@Qualifier("configServerRetryInterceptor") MethodInterceptor configServerRetryInterceptor) {
			return new DefaultPointcutAdvisor(new ConfigServerRetryPointcut(), configServerRetryInterceptor);
		}

	}

	/**
	 * Matches the operations that used to carry Spring Retry's
	 * {@code @Retryable(interceptor = "configServerRetryInterceptor")}.
	 */
	static class ConfigServerRetryPointcut extends StaticMethodMatcherPointcut {

		@Override
		public boolean matches(Method method, Class<?> targetClass) {
			if (ConfigServicePropertySourceLocator.class.isAssignableFrom(targetClass)) {
				return "locate".equals(method.getName()) || "locateCollection".equals(method.getName());
			}
			if (ConfigServerInstanceProvider.class.isAssignableFrom(targetClass)) {
				return "getConfigServerInstances".equals(method.getName());
			}
			return false;
		}

	}

}
