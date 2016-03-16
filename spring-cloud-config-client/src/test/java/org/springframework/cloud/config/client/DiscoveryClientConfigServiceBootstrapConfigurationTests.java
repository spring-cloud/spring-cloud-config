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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.BDDMockito.given;

import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.EnvironmentTestUtils;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;
import org.springframework.cloud.config.client.ConfigClientProperties.ConfigServerEndpoint;
import org.springframework.cloud.config.client.ConfigClientProperties.Credentials;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author Dave Syer
 */
public class DiscoveryClientConfigServiceBootstrapConfigurationTests {

	private AnnotationConfigApplicationContext context;

	private DiscoveryClient client = Mockito.mock(DiscoveryClient.class);

	private ServiceInstance info = new DefaultServiceInstance("app", "foo", 8877, false);

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void offByDefault() throws Exception {
		this.context = new AnnotationConfigApplicationContext(
				DiscoveryClientConfigServiceBootstrapConfiguration.class);
		assertEquals(0, this.context.getBeanNamesForType(DiscoveryClient.class).length);
		assertEquals(0, this.context.getBeanNamesForType(
				DiscoveryClientConfigServiceBootstrapConfiguration.class).length);
	}

	@Test
	public void onWhenRequested() throws Exception {
		given(this.client
				.getInstances(ConfigClientProperties.Discovery.DEFAULT_CONFIG_SERVER))
						.willReturn(Arrays.asList(this.info));
		setup("spring.cloud.config.discovery.enabled=true");
		assertEquals(1, this.context.getBeanNamesForType(
				DiscoveryClientConfigServiceBootstrapConfiguration.class).length);
		Mockito.verify(this.client)
				.getInstances(ConfigClientProperties.Discovery.DEFAULT_CONFIG_SERVER);
		ConfigServerEndpointRepository configServerEndpointRepository = this.context
				.getBean(ConfigServerEndpointRepository.class);
		assertEquals("http://foo:8877/", configServerEndpointRepository
				.getConfigServerEndpoints().iterator().next().getRawUri());
	}

	@Test
	public void secureWhenRequested() throws Exception {
		this.info = new DefaultServiceInstance("app", "foo", 443, true);
		given(this.client.getInstances(ConfigClientProperties.Discovery.DEFAULT_CONFIG_SERVER))
				.willReturn(Arrays.asList(this.info));
		setup("spring.cloud.config.discovery.enabled=true");
		assertEquals(1, this.context.getBeanNamesForType(
				DiscoveryClientConfigServiceBootstrapConfiguration.class).length);
		Mockito.verify(this.client).getInstances(ConfigClientProperties.Discovery.DEFAULT_CONFIG_SERVER);
		ConfigServerEndpointRepository configServerEndpointRepository = this.context
				.getBean(ConfigServerEndpointRepository.class);
		assertEquals("https://foo:443/", configServerEndpointRepository
				.getConfigServerEndpoints().iterator().next().getRawUri());
	}

	@Test
	public void setsPasssword() throws Exception {
		this.info.getMetadata().put("password", "bar");
		given(this.client
				.getInstances(ConfigClientProperties.Discovery.DEFAULT_CONFIG_SERVER))
						.willReturn(Arrays.asList(this.info));
		setup("spring.cloud.config.discovery.enabled=true");
		ConfigServerEndpointRepository configServerEndpointRepository = this.context
				.getBean(ConfigServerEndpointRepository.class);
		ConfigServerEndpoint configServerEndpoint = configServerEndpointRepository
				.getConfigServerEndpoints().iterator().next();
		assertEquals("http://foo:8877/", configServerEndpoint.getRawUri());
		assertEquals("bar", configServerEndpoint.getCredentials().getPassword());
		assertEquals(ConfigServerEndpoint.DEFAULT_USERNAME_user,
				configServerEndpoint.getCredentials().getUsername());
	}

	@Test
	public void setsPath() throws Exception {
		this.info.getMetadata().put("configPath", "/bar");
		given(this.client
				.getInstances(ConfigClientProperties.Discovery.DEFAULT_CONFIG_SERVER))
						.willReturn(Arrays.asList(this.info));
		setup("spring.cloud.config.discovery.enabled=true");
		ConfigServerEndpointRepository configServerEndpointRepository = this.context
				.getBean(ConfigServerEndpointRepository.class);
		assertEquals("http://foo:8877/bar", configServerEndpointRepository
				.getConfigServerEndpoints().iterator().next().getRawUri());
	}
	
	@Test
	public void setsPathWithMissingLeadingSlash() throws Exception {
		this.info.getMetadata().put("configPath", "bar");
		given(this.client
				.getInstances(ConfigClientProperties.Discovery.DEFAULT_CONFIG_SERVER))
						.willReturn(Arrays.asList(this.info));
		setup("spring.cloud.config.discovery.enabled=true");
		ConfigServerEndpointRepository configServerEndpointRepository = this.context
				.getBean(ConfigServerEndpointRepository.class);
		assertEquals("http://foo:8877/bar", configServerEndpointRepository
				.getConfigServerEndpoints().iterator().next().getRawUri());
	}

	@Test
	public void multipleServer() throws Exception {
		ServiceInstance server2 = new DefaultServiceInstance(this.info.getServiceId(), "foo2", 8878, false);
		server2.getMetadata().put("configPath", "/bar2");
		server2.getMetadata().put("password", "secret");

		this.info.getMetadata().put("configPath", "bar");
		given(this.client
				.getInstances(ConfigClientProperties.Discovery.DEFAULT_CONFIG_SERVER))
						.willReturn(Arrays.asList(this.info, server2));
		setup("spring.cloud.config.discovery.enabled=true");
		ConfigServerEndpointRepository configServerEndpointRepository = this.context
				.getBean(ConfigServerEndpointRepository.class);
		List<ConfigServerEndpoint> configServerEndpoints = configServerEndpointRepository
				.getConfigServerEndpoints();
		assertEquals(2, configServerEndpoints.size());
		
		ConfigServerEndpoint configServerEndpoint1 = configServerEndpoints.get(0);
		Assert.assertNull(configServerEndpoint1.getCredentials());
		assertEquals("http://foo:8877/bar", configServerEndpoint1.getRawUri());
		
		ConfigServerEndpoint configServerEndpoint2 = configServerEndpoints.get(1);
		assertEquals("http://foo2:8878/bar2", configServerEndpoint2.getRawUri());
		Credentials credentials = configServerEndpoint2.getCredentials();
		assertNotNull(credentials);
		assertEquals(ConfigServerEndpoint.DEFAULT_USERNAME_user, credentials.getUsername());
		assertEquals("secret", credentials.getPassword());
	}
	
	private void setup(String... env) {
		this.context = new AnnotationConfigApplicationContext();
		EnvironmentTestUtils.addEnvironment(this.context, env);
		EnvironmentTestUtils.addEnvironment(this.context, "eureka.client.enabled=false");
		this.context.getDefaultListableBeanFactory().registerSingleton("discoveryClient",
				this.client);
		this.context.register(UtilAutoConfiguration.class,
				PropertyPlaceholderAutoConfiguration.class,
				DiscoveryClientConfigServiceBootstrapConfiguration.class,
				ConfigClientProperties.class, ConfigServerEndpointRepository.class);
		this.context.refresh();
	}

}
