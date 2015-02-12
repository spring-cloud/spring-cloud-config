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

package org.springframework.cloud.autoconfigure;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.EndpointAutoConfiguration;
import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.boot.actuate.endpoint.InfoEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationBeanFactoryMetaData;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.cloud.bootstrap.config.PropertySourceBootstrapConfiguration;
import org.springframework.cloud.config.client.RefreshEndpoint;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.context.environment.EnvironmentManager;
import org.springframework.cloud.context.properties.ConfigurationPropertiesRebinder;
import org.springframework.cloud.context.restart.RestartEndpoint;
import org.springframework.cloud.context.scope.refresh.RefreshScope;
import org.springframework.cloud.logging.LoggingRebinder;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.integration.monitor.IntegrationMBeanExporter;

/**
 * Autoconfiguration for the refresh scope and associated features to do with changes in
 * the Environment (e.g. rebinding logger levels).
 * 
 * @author Dave Syer
 *
 */
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
	public static LoggingRebinder loggingRebinder() {
		return new LoggingRebinder();
	}

	@Bean
	@ConditionalOnMissingBean
	public EnvironmentManager environmentManager(ConfigurableEnvironment environment) {
		return new EnvironmentManager(environment);
	}

	@Configuration
	@ConditionalOnClass(InfoEndpoint.class)
	@ConditionalOnBean(EndpointAutoConfiguration.class)
	protected static class InfoEndpointRebinderConfiguration implements
			ApplicationListener<EnvironmentChangeEvent> {

		@Autowired
		private EndpointAutoConfiguration endpoints;

		@Autowired
		private ConfigurableEnvironment environment;

		private Map<String, Object> map = new LinkedHashMap<String, Object>();

		@Override
		public void onApplicationEvent(EnvironmentChangeEvent event) {
			for (String key : event.getKeys()) {
				if (key.startsWith("info.")) {
					map.put(key.substring("info.".length()), environment.getProperty(key));
				}
			}
		}

		@Bean
		public InfoEndpoint infoEndpoint() throws Exception {
			return new InfoEndpoint(endpoints.infoEndpoint().invoke()) {
				@Override
				public Map<String, Object> invoke() {
					Map<String, Object> info = new LinkedHashMap<String, Object>(
							super.invoke());
					info.putAll(map);
					return info;
				}
			};
		}

	}

	@Configuration
	@ConditionalOnBean(ConfigurationPropertiesBindingPostProcessor.class)
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
		protected static class RestartEndpointWithIntegration {

			@Autowired(required = false)
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
		protected static class RestartEndpointWithoutIntegration {

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
		@ConditionalOnProperty(value = "endpoints.refresh.enabled", matchIfMissing = true)
		@ConditionalOnBean(PropertySourceBootstrapConfiguration.class)
		protected static class RefreshEndpointConfiguration {

			@Bean
			@ConditionalOnMissingBean
			public RefreshEndpoint refreshEndpoint(
					ConfigurableApplicationContext context, RefreshScope scope) {
				RefreshEndpoint endpoint = new RefreshEndpoint(context, scope);
				return endpoint;
			}

		}

	}
}
