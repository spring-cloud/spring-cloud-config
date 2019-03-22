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

import org.junit.Test;

import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.config.client.ConfigClientProperties.Credentials;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 */
public class DiscoveryClientConfigServiceBootstrapConfigurationTests
		extends BaseDiscoveryClientConfigServiceBootstrapConfigurationTests {

	@Test
	public void offByDefault() throws Exception {
		this.context = new AnnotationConfigApplicationContext(
				DiscoveryClientConfigServiceBootstrapConfiguration.class);

		assertThat(this.context.getBeanNamesForType(DiscoveryClient.class).length)
				.isEqualTo(0);
		assertThat(this.context.getBeanNamesForType(
				DiscoveryClientConfigServiceBootstrapConfiguration.class).length)
						.isEqualTo(0);
	}

	@Test
	public void onWhenRequested() throws Exception {
		givenDiscoveryClientReturnsInfo();

		setup("spring.cloud.config.discovery.enabled=true");

		expectDiscoveryClientConfigServiceBootstrapConfigurationIsSetup();
		verifyDiscoveryClientCalledOnce();
		expectConfigClientPropertiesHasConfigurationFromEureka();
	}

	@Test
	public void onWhenHeartbeat() throws Exception {
		setup("spring.cloud.config.discovery.enabled=true");

		expectDiscoveryClientConfigServiceBootstrapConfigurationIsSetup();

		givenDiscoveryClientReturnsInfo();
		verifyDiscoveryClientCalledOnce();

		this.context.publishEvent(new HeartbeatEvent(this.context, "new"));

		expectConfigClientPropertiesHasConfigurationFromEureka();
	}

	@Test
	public void secureWhenRequested() throws Exception {
		this.info = new DefaultServiceInstance("app", "foo", 443, true);
		givenDiscoveryClientReturnsInfo();

		setup("spring.cloud.config.discovery.enabled=true");

		expectDiscoveryClientConfigServiceBootstrapConfigurationIsSetup();

		verifyDiscoveryClientCalledOnce();
		expectConfigClientPropertiesHasConfiguration("https://foo:443/");
	}

	@Test
	public void multipleInstancesReturnedFromDiscovery() {
		ServiceInstance info1 = new DefaultServiceInstance("app", "localhost", 8888,
				true);
		ServiceInstance info2 = new DefaultServiceInstance("app", "localhost1", 8888,
				false);
		givenDiscoveryClientReturnsInfoForMultipleInstances(info1, info2);

		setup("spring.cloud.config.discovery.enabled=true");

		expectDiscoveryClientConfigServiceBootstrapConfigurationIsSetup();

		verifyDiscoveryClientCalledOnce();
		expectConfigClientPropertiesHasMultipleUris("https://localhost:8888/",
				"http://localhost1:8888/");

	}

	@Test
	public void setsPasssword() throws Exception {
		this.info.getMetadata().put("password", "bar");
		givenDiscoveryClientReturnsInfo();

		setup("spring.cloud.config.discovery.enabled=true");

		ConfigClientProperties locator = this.context
				.getBean(ConfigClientProperties.class);
		Credentials credentials = locator.getCredentials(0);
		assertThat(credentials.getUri()).isEqualTo("http://foo:8877/");
		assertThat(credentials.getPassword()).isEqualTo("bar");
		assertThat(credentials.getUsername()).isEqualTo("user");
	}

	@Test
	public void setsPath() throws Exception {
		this.info.getMetadata().put("configPath", "/bar");
		givenDiscoveryClientReturnsInfo();

		setup("spring.cloud.config.discovery.enabled=true");

		expectConfigClientPropertiesHasConfiguration("http://foo:8877/bar");
	}

	@Test
	public void shouldFailGetConfigServerInstanceFromDiscoveryClient() throws Exception {
		givenDiscoveryClientReturnsNoInfo();

		setup("spring.cloud.config.discovery.enabled=true");

		expectDiscoveryClientConfigServiceBootstrapConfigurationIsSetup();
		verifyDiscoveryClientCalledOnce();
		expectConfigClientPropertiesHasDefaultConfiguration();
	}

	@Test
	public void shouldRetryAndSucceedGetConfigServerInstanceFromDiscoveryClient()
			throws Exception {
		givenDiscoveryClientReturnsInfoOnThirdTry();

		setup("spring.cloud.config.discovery.enabled=true",
				"spring.cloud.config.retry.maxAttempts=3",
				"spring.cloud.config.retry.initialInterval=10",
				"spring.cloud.config.fail-fast=true");

		expectDiscoveryClientConfigServiceBootstrapConfigurationIsSetup();
		verifyDiscoveryClientCalledThreeTimes();

		this.context.publishEvent(new HeartbeatEvent(this.context, "new"));

		expectConfigClientPropertiesHasConfigurationFromEureka();
	}

	@Test
	public void shouldNotRetryIfNotFailFastPropertySet() throws Exception {
		givenDiscoveryClientReturnsInfoOnThirdTry();

		setup("spring.cloud.config.discovery.enabled=true",
				"spring.cloud.config.retry.maxAttempts=3",
				"spring.cloud.config.retry.initialInterval=10");

		expectDiscoveryClientConfigServiceBootstrapConfigurationIsSetup();
		verifyDiscoveryClientCalledOnce();
		expectConfigClientPropertiesHasDefaultConfiguration();
	}

	@Test
	public void shouldRetryAndFailWithExceptionGetConfigServerInstanceFromDiscoveryClient()
			throws Exception {
		givenDiscoveryClientReturnsNoInfo();

		expectNoInstancesOfConfigServerException();

		setup("spring.cloud.config.discovery.enabled=true",
				"spring.cloud.config.retry.maxAttempts=3",
				"spring.cloud.config.retry.initialInterval=10",
				"spring.cloud.config.fail-fast=true");
	}

	@Test
	public void shouldRetryAndFailWithMessageGetConfigServerInstanceFromDiscoveryClient()
			throws Exception {
		givenDiscoveryClientReturnsNoInfo();

		setup("spring.cloud.config.discovery.enabled=true",
				"spring.cloud.config.retry.maxAttempts=3",
				"spring.cloud.config.retry.initialInterval=10",
				"spring.cloud.config.fail-fast=false");

		expectDiscoveryClientConfigServiceBootstrapConfigurationIsSetup();
		expectConfigClientPropertiesHasDefaultConfiguration();
	}

}
