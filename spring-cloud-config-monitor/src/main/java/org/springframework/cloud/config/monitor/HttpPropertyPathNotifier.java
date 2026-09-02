/*
 * Copyright 2015-present the original author or authors.
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

package org.springframework.cloud.config.monitor;

import java.util.Map;
import java.util.Set;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.client.RestClient;

/**
 * Notifies applications affected by a configuration change using HTTP.
 *
 * @author Yash Chauhan
 */
public class HttpPropertyPathNotifier implements PropertyPathNotifier {

	private final DiscoveryClient discoveryClient;

	private final RestClient restClient;

	private final MonitorConfigurationProperties monitorProperties;

	public HttpPropertyPathNotifier(DiscoveryClient discoveryClient, RestClient restClient,
			MonitorConfigurationProperties monitorProperties) {
		this.discoveryClient = discoveryClient;
		this.restClient = restClient;
		this.monitorProperties = monitorProperties;
	}

	@Override
	public void notifyApplications(Set<String> services) {
		for (String service : services) {
			for (ServiceInstance instance : this.discoveryClient.getInstances(service)) {
				this.restClient.post()
					.uri(instance.getUri() + getEndpoint(service, instance))
					.retrieve()
					.toBodilessEntity();
			}
		}
	}

	private String getEndpoint(String service, ServiceInstance instance) {
		Map<String, String> endpoints = this.monitorProperties.getHttp().getEndpoints();

		if (endpoints.containsKey(service)) {
			return endpoints.get(service);
		}

		String endpoint = instance.getMetadata().get("refresh-endpoint");
		if (endpoint != null) {
			return endpoint;
		}

		return "/actuator/refresh";
	}

}
