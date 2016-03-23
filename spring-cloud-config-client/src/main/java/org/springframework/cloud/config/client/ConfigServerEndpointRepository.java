package org.springframework.cloud.config.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.cloud.config.client.ConfigClientProperties.ConfigServerEndpoint;

/**
 * @author Felix Kissel
 *
 */
public class ConfigServerEndpointRepository  {
	
	private List<ConfigServerEndpoint> configServerEndpoints = Collections.emptyList();

	public synchronized void setConfigServerEndpoints(
			List<ConfigServerEndpoint> configServerEndpoints) {
		this.configServerEndpoints = Collections
				.unmodifiableList(new ArrayList<>(configServerEndpoints));
	}
	
	public List<ConfigServerEndpoint> getConfigServerEndpoints() {
		return this.configServerEndpoints;
	}
	
}