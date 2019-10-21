/*
 * Copyright 2013-2019 the original author or authors.
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

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Expose a ConfigClientProperties just so that there is a way to inspect the properties
 * bound to it. It won't be available in time for autowiring into the bootstrap context,
 * but the values in this properties object will be the same as the ones used to bind to
 * the config server, if there is one.
 *
 * @author Dave Syer
 * @author Marcos Barbero
 *
 */
@Configuration(proxyBeanMethods = false)
public class ConfigClientAutoConfiguration {

	@Bean
	public ConfigClientProperties configClientProperties(Environment environment,
			ApplicationContext context) {
		if (context.getParent() != null
				&& BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
						context.getParent(), ConfigClientProperties.class).length > 0) {
			return BeanFactoryUtils.beanOfTypeIncludingAncestors(context.getParent(),
					ConfigClientProperties.class);
		}
		ConfigClientProperties client = new ConfigClientProperties(environment);
		return client;
	}

	@Bean
	public ConfigClientHealthProperties configClientHealthProperties() {
		return new ConfigClientHealthProperties();
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(HealthIndicator.class)
	@ConditionalOnBean(ConfigServicePropertySourceLocator.class)
	@ConditionalOnProperty(value = "health.config.enabled", matchIfMissing = true)
	protected static class ConfigServerHealthIndicatorConfiguration {

		@Bean
		public ConfigServerHealthIndicator clientConfigServerHealthIndicator(
				ConfigServicePropertySourceLocator locator,
				ConfigClientHealthProperties properties, Environment environment) {
			return new ConfigServerHealthIndicator(locator, environment, properties);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(ContextRefresher.class)
	@ConditionalOnBean(ContextRefresher.class)
	@ConditionalOnProperty("spring.cloud.config.watch.enabled")
	protected static class ConfigClientWatchConfiguration {

		@Bean
		public ConfigClientWatch configClientWatch(ContextRefresher contextRefresher) {
			return new ConfigClientWatch(contextRefresher);
		}

	}

}
