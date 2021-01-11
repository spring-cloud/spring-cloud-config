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

import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Bootstrap configuration for a config client that wants to lookup the config server via
 * discovery.
 *
 * @author Dave Syer
 */
@ConditionalOnProperty(ConfigClientProperties.CONFIG_DISCOVERY_ENABLED)
@Configuration(proxyBeanMethods = false)
@Import({ UtilAutoConfiguration.class })
@EnableDiscoveryClient
public class DiscoveryClientConfigServiceBootstrapConfiguration {

	@Bean
	public ConfigServerInstanceProvider configServerInstanceProvider(
			ObjectProvider<ConfigServerInstanceProvider.Function> function,
			ObjectProvider<DiscoveryClient> discoveryClient) {
		ConfigServerInstanceProvider.Function fn = function.getIfAvailable();
		if (fn != null) {
			return new ConfigServerInstanceProvider(fn);
		}
		DiscoveryClient client = discoveryClient.getIfAvailable();
		if (client == null) {
			throw new IllegalStateException("ConfigServerInstanceProvider requires a DiscoveryClient or Function");
		}
		return new ConfigServerInstanceProvider(client::getInstances);
	}

	@Bean
	public ConfigServerInstanceMonitor configServerInstanceMonitor(ConfigClientProperties properties,
			ConfigServerInstanceProvider provider) {
		return new ConfigServerInstanceMonitor(LogFactory.getLog(ConfigServerInstanceMonitor.class), properties,
				provider);
	}

}
