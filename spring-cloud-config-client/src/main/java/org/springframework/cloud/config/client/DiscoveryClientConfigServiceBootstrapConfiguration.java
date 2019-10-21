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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.client.discovery.event.HeartbeatMonitor;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.SmartApplicationListener;

/**
 * Bootstrap configuration for a config client that wants to lookup the config server via
 * discovery.
 *
 * @author Dave Syer
 */
@ConditionalOnProperty(value = "spring.cloud.config.discovery.enabled",
		matchIfMissing = false)
@Configuration(proxyBeanMethods = false)
@Import({ UtilAutoConfiguration.class })
@EnableDiscoveryClient
public class DiscoveryClientConfigServiceBootstrapConfiguration
		implements SmartApplicationListener {

	private static Log logger = LogFactory
			.getLog(DiscoveryClientConfigServiceBootstrapConfiguration.class);

	@Autowired
	private ConfigClientProperties config;

	@Autowired
	private ConfigServerInstanceProvider instanceProvider;

	private HeartbeatMonitor monitor = new HeartbeatMonitor();

	@Bean
	public ConfigServerInstanceProvider configServerInstanceProvider(
			DiscoveryClient discoveryClient) {
		return new ConfigServerInstanceProvider(discoveryClient);
	}

	@Override
	public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
		return ContextRefreshedEvent.class.isAssignableFrom(eventType)
				|| HeartbeatEvent.class.isAssignableFrom(eventType);
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof ContextRefreshedEvent) {
			startup((ContextRefreshedEvent) event);
		}
		else if (event instanceof HeartbeatEvent) {
			heartbeat((HeartbeatEvent) event);
		}
	}

	public void startup(ContextRefreshedEvent event) {
		refresh();
	}

	public void heartbeat(HeartbeatEvent event) {
		if (this.monitor.update(event.getValue())) {
			refresh();
		}
	}

	private void refresh() {
		try {
			String serviceId = this.config.getDiscovery().getServiceId();
			List<String> listOfUrls = new ArrayList<>();
			List<ServiceInstance> serviceInstances = this.instanceProvider
					.getConfigServerInstances(serviceId);

			for (int i = 0; i < serviceInstances.size(); i++) {

				ServiceInstance server = serviceInstances.get(i);
				String url = getHomePage(server);

				if (server.getMetadata().containsKey("password")) {
					String user = server.getMetadata().get("user");
					user = user == null ? "user" : user;
					this.config.setUsername(user);
					String password = server.getMetadata().get("password");
					this.config.setPassword(password);
				}

				if (server.getMetadata().containsKey("configPath")) {
					String path = server.getMetadata().get("configPath");
					if (url.endsWith("/") && path.startsWith("/")) {
						url = url.substring(0, url.length() - 1);
					}
					url = url + path;
				}

				listOfUrls.add(url);
			}

			String[] uri = new String[listOfUrls.size()];
			uri = listOfUrls.toArray(uri);
			this.config.setUri(uri);

		}
		catch (Exception ex) {
			if (this.config.isFailFast()) {
				throw ex;
			}
			else {
				logger.warn("Could not locate configserver via discovery", ex);
			}
		}
	}

	private String getHomePage(ServiceInstance server) {
		return server.getUri().toString() + "/";
	}

}
