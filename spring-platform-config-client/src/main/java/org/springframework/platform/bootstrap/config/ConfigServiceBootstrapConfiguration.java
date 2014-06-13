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

package org.springframework.platform.bootstrap.config;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.PropertySource;

/**
 * @author Dave Syer
 *
 */
@Configuration
@EnableConfigurationProperties
public class ConfigServiceBootstrapConfiguration implements
		ApplicationContextInitializer<ConfigurableApplicationContext> {

	private static Log logger = LogFactory.getLog(ConfigServiceBootstrapConfiguration.class);

	@Autowired(required = false)
	private List<PropertySourceLocator> propertySourceLocators = new ArrayList<PropertySourceLocator>();

	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		CompositePropertySource composite = new CompositePropertySource("bootstrap");
		AnnotationAwareOrderComparator.sort(propertySourceLocators);
		boolean empty = true;
		for (PropertySourceLocator locator : propertySourceLocators) {
			PropertySource<?> source = null;
			try {
				source = locator.locate();
			} catch (Exception e) {
				logger.error("Could not locate PropertySource: " + e.getMessage());
			}
			if (source == null) {
				continue;
			}
			composite.addPropertySource(source);
			empty = false;
		}
		if (!empty) {
			applicationContext.getEnvironment().getPropertySources().addFirst(composite);
		}
	}

	@Bean
	@ConfigurationProperties("spring.platform.config")
	public ConfigServicePropertySourceLocator configServicePropertySource() {
		return new ConfigServicePropertySourceLocator();
	}

}
