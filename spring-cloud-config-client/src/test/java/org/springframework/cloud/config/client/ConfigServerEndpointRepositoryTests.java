package org.springframework.cloud.config.client;

import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.cloud.config.client.ConfigClientProperties.ConfigServerEndpoint;

public class ConfigServerEndpointRepositoryTests {

	@Test
	public void newRepoIsEmpty() {
		Assert.assertTrue(new ConfigServerEndpointRepository().getConfigServerEndpoints()
				.isEmpty());
	}

	@Test
	public void vanilla() {
		ConfigServerEndpointRepository configServerEndpointRepository = new ConfigServerEndpointRepository();
		ConfigServerEndpoint endpoint1 = mock(ConfigServerEndpoint.class);
		ConfigServerEndpoint endpoint2 = mock(ConfigServerEndpoint.class);
		List<ConfigServerEndpoint> list = Arrays.asList(endpoint1, endpoint2);
		configServerEndpointRepository.setConfigServerEndpoints(list);

		Assert.assertEquals(list,
				configServerEndpointRepository.getConfigServerEndpoints());
	}

}
