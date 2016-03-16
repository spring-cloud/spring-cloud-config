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

package org.springframework.cloud.config.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;
import org.springframework.cloud.config.client.ConfigClientProperties.ConfigServerEndpoint;
import org.springframework.cloud.config.client.ConfigClientProperties.UsernamePasswordPair;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

/**
 * Bootstrap configuration for a config client that wants to lookup the config server via
 * discovery.
 *
 * @author Dave Syer
 * @author Felix Kissel
 */
@ConditionalOnProperty(value = "spring.cloud.config.discovery.enabled", matchIfMissing = false)
@Configuration
@Import({ UtilAutoConfiguration.class })
@EnableDiscoveryClient
public class DiscoveryClientConfigServiceBootstrapConfiguration {

	private static Log logger = LogFactory
			.getLog(DiscoveryClientConfigServiceBootstrapConfiguration.class);

	@Autowired
	private ConfigClientProperties config;
	
	@Autowired
	private ConfigServerEndpointRepository configServerEndpointSelector;

	@Autowired
	private DiscoveryClient client;

	@EventListener(ContextRefreshedEvent.class)
	public void onApplicationEvent(ContextRefreshedEvent event) {
		refresh();
	}

	private void refresh() {
		try {
			logger.debug("Locating configserver via discovery");
			String serviceId = this.config.getDiscovery().getServiceId();
			List<ServiceInstance> instances = this.client.getInstances(serviceId);
			if (instances.isEmpty()) {
				logger.warn("No instances found of configserver (" + serviceId + ")");
				return;
			}
			List<ConfigServerEndpoint> configServerEndpoints = new ArrayList<>();
			for (ServiceInstance server : instances) {
				UsernamePasswordPair usernamePasswordPair = determineUsernamePasswordPair(
						server.getMetadata());
				StringBuilder uri = new StringBuilder();
				uri.append(server.getUri().toString());
				if (server.getMetadata().containsKey("configPath")) {
					String path = server.getMetadata().get("configPath");
					if(!path.startsWith("/")) {
						uri.append("/");
					}
					uri.append(path);
				} else if(!uri.toString().endsWith("/")) {
					// should we always append "/", even if configPath was given?
					uri.append("/");
				}
				ConfigServerEndpoint configServerEndpoint = ConfigServerEndpoint
						.create(uri.toString(), usernamePasswordPair);
				logger.debug("add configServerEndpoint " + configServerEndpoint);
				configServerEndpoints.add(configServerEndpoint);
			}
			this.configServerEndpointSelector.setConfigServerEndpoints(configServerEndpoints);
		}
		catch (Exception ex) {
			logger.warn("Could not locate configserver via discovery", ex);
		}
	}

	private static UsernamePasswordPair determineUsernamePasswordPair(
			Map<String, String> serverMetadata) {
		UsernamePasswordPair usernamePasswordPair;
		if (serverMetadata.containsKey("password")) {
			String user = serverMetadata.get("user");
			String password = serverMetadata.get("password");
			usernamePasswordPair = new UsernamePasswordPair(user, password);
		}
		else {
			usernamePasswordPair = new UsernamePasswordPair(null, null);
		}
		return usernamePasswordPair;
	}

}
