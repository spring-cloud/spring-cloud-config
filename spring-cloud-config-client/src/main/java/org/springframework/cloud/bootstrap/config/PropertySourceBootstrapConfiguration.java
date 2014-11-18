/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.bootstrap.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.bootstrap.BootstrapApplicationListener;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.ConfigServerHealthIndicator;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;
import org.springframework.cloud.config.client.PropertySourceLocator;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

/**
 * @author Dave Syer
 *
 */
@Configuration
@EnableConfigurationProperties
public class PropertySourceBootstrapConfiguration implements
		ApplicationContextInitializer<ConfigurableApplicationContext> {

	private static final String BOOTSTRAP_PROPERTY_SOURCE_NAME = BootstrapApplicationListener.BOOTSTRAP_PROPERTY_SOURCE_NAME;

	private static Log logger = LogFactory
			.getLog(PropertySourceBootstrapConfiguration.class);

	@Autowired(required = false)
	private List<PropertySourceLocator> propertySourceLocators = new ArrayList<PropertySourceLocator>();

	public void setPropertySourceLocators(
			Collection<PropertySourceLocator> propertySourceLocators) {
		this.propertySourceLocators = new ArrayList<PropertySourceLocator>(
				propertySourceLocators);
	}

	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		CompositePropertySource composite = new CompositePropertySource(
				BOOTSTRAP_PROPERTY_SOURCE_NAME);
		AnnotationAwareOrderComparator.sort(propertySourceLocators);
		boolean empty = true;
		for (PropertySourceLocator locator : propertySourceLocators) {
			PropertySource<?> source = null;
			try {
				source = locator.locate(applicationContext.getEnvironment());
			}
			catch (Exception e) {
				logger.error("Could not locate PropertySource: " + e.getMessage());
			}
			if (source == null) {
				continue;
			}
			logger.info("Located property source: " + source);
			composite.addPropertySource(source);
			empty = false;
		}
		if (!empty) {
			MutablePropertySources propertySources = applicationContext.getEnvironment()
					.getPropertySources();
			if (propertySources.contains(BOOTSTRAP_PROPERTY_SOURCE_NAME)) {
				propertySources.replace(BOOTSTRAP_PROPERTY_SOURCE_NAME, composite);
			}
			else {
				propertySources.addFirst(composite);
			}
		}
	}

	@Configuration
    @ConditionalOnExpression("${spring.cloud.config.enabled:true}")
	protected static class PropertySourceLocatorConfiguration {

		@Autowired
		private ConfigurableEnvironment environment;

		@Bean
		public ConfigClientProperties configClientProperties() {
			ConfigClientProperties client = new ConfigClientProperties(environment);
			return client;
		}

		@Bean
		public ConfigServicePropertySourceLocator configServicePropertySource() {
			ConfigServicePropertySourceLocator locator = new ConfigServicePropertySourceLocator(
					configClientProperties());
			return locator;
		}

        @Bean
        public ConfigServerHealthIndicator configServerHealthIndicator(ConfigServicePropertySourceLocator locator) {
            return new ConfigServerHealthIndicator(environment, locator);
        }
	}

}
