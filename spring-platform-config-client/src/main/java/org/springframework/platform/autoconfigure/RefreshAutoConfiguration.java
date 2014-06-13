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
 *
 */
package org.springframework.platform.autoconfigure;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.EndpointAutoConfiguration;
import org.springframework.boot.actuate.endpoint.EnvironmentEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.ConfigurationBeanFactoryMetaData;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.platform.context.environment.EnvironmentManager;
import org.springframework.platform.context.environment.EnvironmentManagerMvcEndpoint;
import org.springframework.platform.context.properties.ConfigurationPropertiesRebinder;
import org.springframework.platform.context.scope.refresh.RefreshScope;

@Configuration
@ConditionalOnClass(RefreshScope.class)
@AutoConfigureAfter(EndpointAutoConfiguration.class)
public class RefreshAutoConfiguration {
	
	@Autowired
	private ConfigurationPropertiesBindingPostProcessor binder;

	@Autowired
	private ConfigurationBeanFactoryMetaData metaData;

	@Bean
	@ConditionalOnMissingBean
	public static RefreshScope refreshScope() {
		return new RefreshScope();
	}
	
	@Bean
	@ConditionalOnMissingBean
	public EnvironmentManager environmentManager(ConfigurableEnvironment environment) {
		return new EnvironmentManager(environment);
	}
	
	@Bean
	@ConditionalOnMissingBean
	public ConfigurationPropertiesRebinder configurationPropertiesRebinder() {
		ConfigurationPropertiesRebinder rebinder = new ConfigurationPropertiesRebinder(binder);
		rebinder.setBeanMetaDataStore(metaData);
		return rebinder;
	}
	
	@Configuration
	@ConditionalOnWebApplication
	@ConditionalOnClass(EnvironmentEndpoint.class)
	@ConditionalOnExpression("${endpoints.env.enabled:true}")
	@ConditionalOnBean(EnvironmentEndpoint.class)
	protected static class EnvironmentEndpointConfiguration {
		
		@Bean
		public EnvironmentManagerMvcEndpoint environmentManagerEndpoint(EnvironmentEndpoint delegate, EnvironmentManager environment) {
			return new EnvironmentManagerMvcEndpoint(delegate, environment);
		}
		
	}
	
}
