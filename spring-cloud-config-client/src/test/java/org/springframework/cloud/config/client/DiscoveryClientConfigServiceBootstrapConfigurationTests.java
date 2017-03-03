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

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.cloud.config.client.ConfigClientProperties.Discovery.DEFAULT_CONFIG_SERVER;

/**
 * @author Dave Syer
 */
public class DiscoveryClientConfigServiceBootstrapConfigurationTests {

	@Rule
	public ExpectedException expectedException = ExpectedException.none();

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
		given(this.client.getInstances(DEFAULT_CONFIG_SERVER))
				.willReturn(Arrays.asList(this.info));
		setup("spring.cloud.config.discovery.enabled=true");
		assertEquals(1, this.context.getBeanNamesForType(
				DiscoveryClientConfigServiceBootstrapConfiguration.class).length);
		verify(this.client).getInstances(DEFAULT_CONFIG_SERVER);
		ConfigClientProperties locator = this.context
				.getBean(ConfigClientProperties.class);
		assertEquals("http://foo:8877/", locator.getRawUri());
	}

	@Test
	public void onWhenHeartbeat() throws Exception {
		setup("spring.cloud.config.discovery.enabled=true");
		assertEquals(1, this.context.getBeanNamesForType(
				DiscoveryClientConfigServiceBootstrapConfiguration.class).length);
		given(this.client.getInstances(DEFAULT_CONFIG_SERVER))
				.willReturn(Arrays.asList(this.info));
		verify(this.client).getInstances(DEFAULT_CONFIG_SERVER);
		context.publishEvent(new HeartbeatEvent(context, "new"));
		ConfigClientProperties locator = this.context
				.getBean(ConfigClientProperties.class);
		assertEquals("http://foo:8877/", locator.getRawUri());
	}

	@Test
	public void secureWhenRequested() throws Exception {
		this.info = new DefaultServiceInstance("app", "foo", 443, true);
		given(this.client.getInstances(DEFAULT_CONFIG_SERVER))
				.willReturn(Arrays.asList(this.info));
		setup("spring.cloud.config.discovery.enabled=true");
		assertEquals(1, this.context.getBeanNamesForType(
				DiscoveryClientConfigServiceBootstrapConfiguration.class).length);
		verify(this.client).getInstances(DEFAULT_CONFIG_SERVER);
		ConfigClientProperties locator = this.context
				.getBean(ConfigClientProperties.class);
		assertEquals("https://foo:443/", locator.getRawUri());
	}

	@Test
	public void setsPasssword() throws Exception {
		this.info.getMetadata().put("password", "bar");
		given(this.client.getInstances(DEFAULT_CONFIG_SERVER))
				.willReturn(Arrays.asList(this.info));
		setup("spring.cloud.config.discovery.enabled=true");
		ConfigClientProperties locator = this.context
				.getBean(ConfigClientProperties.class);
		assertEquals("http://foo:8877/", locator.getRawUri());
		assertEquals("bar", locator.getPassword());
		assertEquals("user", locator.getUsername());
	}

	@Test
	public void setsPath() throws Exception {
		this.info.getMetadata().put("configPath", "/bar");
		given(this.client.getInstances(DEFAULT_CONFIG_SERVER))
				.willReturn(Arrays.asList(this.info));
		setup("spring.cloud.config.discovery.enabled=true");
		ConfigClientProperties locator = this.context
				.getBean(ConfigClientProperties.class);
		assertEquals("http://foo:8877/bar", locator.getRawUri());
	}

	@Test
	public void shouldFailGetConfigServerInstanceFromDiscoveryClient() throws Exception {
		given(this.client.getInstances(DEFAULT_CONFIG_SERVER))
				.willReturn(Collections.<ServiceInstance> emptyList());

		setup("spring.cloud.config.discovery.enabled=true");

		assertEquals(1, this.context.getBeanNamesForType(
				DiscoveryClientConfigServiceBootstrapConfiguration.class).length);
		verify(client).getInstances(DEFAULT_CONFIG_SERVER);
		ConfigClientProperties locator = this.context
				.getBean(ConfigClientProperties.class);
		assertEquals("http://localhost:8888", locator.getRawUri());
	}

	@Test
	public void shouldRetryAndSucceedGetConfigServerInstanceFromDiscoveryClient()
			throws Exception {
		given(this.client.getInstances(DEFAULT_CONFIG_SERVER))
				.willReturn(Collections.<ServiceInstance> emptyList())
				.willReturn(Collections.<ServiceInstance> emptyList())
				.willReturn(Arrays.asList(this.info));

		setup("spring.cloud.config.discovery.enabled=true",
				"spring.cloud.config.retry.maxAttempts=3",
				"spring.cloud.config.retry.initialInterval=10",
				"spring.cloud.config.failFast=true");

		assertEquals(1, this.context.getBeanNamesForType(
				DiscoveryClientConfigServiceBootstrapConfiguration.class).length);

		context.publishEvent(new HeartbeatEvent(context, "new"));
		ConfigClientProperties locator = this.context
				.getBean(ConfigClientProperties.class);
		assertEquals("http://foo:8877/", locator.getRawUri());
	}

	@Test
	public void shouldRetryAndFailWithExceptionGetConfigServerInstanceFromDiscoveryClient()
			throws Exception {
		given(this.client.getInstances(DEFAULT_CONFIG_SERVER))
				.willReturn(Collections.<ServiceInstance> emptyList());

		expectedException.expect(IllegalStateException.class);
		expectedException.expectMessage(
				"No instances found of configserver (" + DEFAULT_CONFIG_SERVER + ")");

		setup("spring.cloud.config.discovery.enabled=true",
				"spring.cloud.config.retry.maxAttempts=3",
				"spring.cloud.config.retry.initialInterval=10",
				"spring.cloud.config.failFast=true");
	}

	@Test
	public void shouldRetryAndFailWithMessageGetConfigServerInstanceFromDiscoveryClient()
			throws Exception {
		given(this.client.getInstances(DEFAULT_CONFIG_SERVER))
				.willReturn(Collections.<ServiceInstance> emptyList());

		setup("spring.cloud.config.discovery.enabled=true",
				"spring.cloud.config.retry.maxAttempts=3",
				"spring.cloud.config.retry.initialInterval=10",
				"spring.cloud.config.failFast=false");

		assertEquals(1, this.context.getBeanNamesForType(
				DiscoveryClientConfigServiceBootstrapConfiguration.class).length);
		ConfigClientProperties locator = this.context
				.getBean(ConfigClientProperties.class);
		assertEquals("http://localhost:8888", locator.getRawUri());
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
				ConfigServiceBootstrapConfiguration.class, ConfigClientProperties.class);
		this.context.refresh();
	}

}
