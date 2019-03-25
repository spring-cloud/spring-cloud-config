/*
 * Copyright 2018-2019 the original author or authors.
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

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.retry.annotation.Retryable;

/**
 * Fetches config server instances.
 *
 * @author Nastya Smirnova
 */
public class ConfigServerInstanceProvider {

	private static Log logger = LogFactory.getLog(ConfigServerInstanceProvider.class);

	private final DiscoveryClient client;

	public ConfigServerInstanceProvider(DiscoveryClient client) {
		this.client = client;
	}

	@Retryable(interceptor = "configServerRetryInterceptor")
	public List<ServiceInstance> getConfigServerInstances(String serviceId) {
		logger.debug("Locating configserver (" + serviceId + ") via discovery");
		List<ServiceInstance> instances = this.client.getInstances(serviceId);
		if (instances.isEmpty()) {
			throw new IllegalStateException(
					"No instances found of configserver (" + serviceId + ")");
		}
		logger.debug("Located configserver (" + serviceId
				+ ") via discovery. No of instances found: " + instances.size());
		return instances;
	}

}
