package org.springframework.cloud.config.client;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.retry.annotation.Retryable;

import java.util.List;

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
		logger.debug(
				"Located configserver (" + serviceId + ") via discovery. No of instances found: " + instances.size());
		return instances;
	}
}
