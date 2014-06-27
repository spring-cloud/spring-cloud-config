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
import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.boot.actuate.endpoint.EnvironmentEndpoint;
import org.springframework.boot.actuate.endpoint.mvc.MvcEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationBeanFactoryMetaData;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.integration.monitor.IntegrationMBeanExporter;
import org.springframework.platform.bootstrap.config.ConfigServiceBootstrapConfiguration;
import org.springframework.platform.config.client.RefreshEndpoint;
import org.springframework.platform.context.environment.EnvironmentManager;
import org.springframework.platform.context.environment.EnvironmentManagerMvcEndpoint;
import org.springframework.platform.context.properties.ConfigurationPropertiesRebinder;
import org.springframework.platform.context.restart.RestartEndpoint;
import org.springframework.platform.context.restart.RestartMvcEndpoint;
import org.springframework.platform.context.scope.refresh.RefreshScope;
import org.springframework.platform.endpoint.GenericPostableMvcEndpoint;

@Configuration
@ConditionalOnClass(RefreshScope.class)
@AutoConfigureAfter(WebMvcAutoConfiguration.class)
public class RefreshAutoConfiguration {

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

	protected static class ConfigurationPropertiesRebinderConfiguration {

		@Autowired
		private ConfigurationPropertiesBindingPostProcessor binder;

		@Autowired
		private ConfigurationBeanFactoryMetaData metaData;

		@Bean
		@ConditionalOnMissingBean
		public ConfigurationPropertiesRebinder configurationPropertiesRebinder() {
			ConfigurationPropertiesRebinder rebinder = new ConfigurationPropertiesRebinder(
					binder);
			rebinder.setBeanMetaDataStore(metaData);
			return rebinder;
		}

	}

	@ConditionalOnClass(Endpoint.class)
	protected static class RefreshEndpointsConfiguration {

		@ConditionalOnClass(IntegrationMBeanExporter.class)
		protected static class RefreshEndpointWithIntegration {

			@Autowired(required=false)
			private IntegrationMBeanExporter exporter;

			@Bean
			@ConditionalOnMissingBean
			public RestartEndpoint restartEndpoint() {
				RestartEndpoint endpoint = new RestartEndpoint();
				if (exporter != null) {
					endpoint.setIntegrationMBeanExporter(exporter);
				}
				return endpoint;
			}

		}

		@ConditionalOnMissingClass(name = "org.springframework.integration.monitor.IntegrationMBeanExporter")
		protected static class RefreshEndpointWithoutIntegration {

			@Bean
			@ConditionalOnMissingBean
			public RestartEndpoint restartEndpoint() {
				return new RestartEndpoint();
			}
		}

		@Bean
		@ConfigurationProperties("endpoints.pause")
		public Endpoint<Boolean> pauseEndpoint(RestartEndpoint restartEndpoint) {
			return restartEndpoint.getPauseEndpoint();
		}

		@Bean
		@ConfigurationProperties("endpoints.resume")
		public Endpoint<Boolean> resumeEndpoint(RestartEndpoint restartEndpoint) {
			return restartEndpoint.getResumeEndpoint();
		}

		@Configuration
		@ConditionalOnExpression("${endpoints.refresh.enabled:true}")
		@ConditionalOnBean(ConfigServiceBootstrapConfiguration.class)
		protected static class RefreshEndpointConfiguration {

			@Bean
			@ConditionalOnMissingBean
			public RefreshEndpoint refreshEndpoint(ConfigurableApplicationContext context) {
				RefreshEndpoint endpoint = new RefreshEndpoint(context);
				return endpoint;
			}

			@Bean
			public MvcEndpoint refreshMvcEndpoint(RefreshEndpoint endpoint) {
				return new GenericPostableMvcEndpoint(endpoint);
			}

		}

		@Configuration
		@ConditionalOnWebApplication
		@ConditionalOnClass(EnvironmentEndpoint.class)
		@ConditionalOnExpression("${endpoints.env.enabled:true}")
		@ConditionalOnBean(EnvironmentEndpoint.class)
		protected static class EnvironmentEndpointConfiguration {

			@Autowired
			private RestartEndpoint restartEndpoint;

			@Bean
			public EnvironmentManagerMvcEndpoint environmentManagerEndpoint(
					EnvironmentEndpoint delegate, EnvironmentManager environment) {
				return new EnvironmentManagerMvcEndpoint(delegate, environment);
			}

			@Bean
			public RestartMvcEndpoint restartMvcEndpoint() {
				return new RestartMvcEndpoint(restartEndpoint);
			}

			@Bean
			public MvcEndpoint pauseMvcEndpoint(RestartMvcEndpoint restartEndpoint) {
				return restartEndpoint.getPauseEndpoint();
			}

			@Bean
			public MvcEndpoint resumeMvcEndpoint(RestartMvcEndpoint restartEndpoint) {
				return restartEndpoint.getResumeEndpoint();
			}

		}

	}
}
