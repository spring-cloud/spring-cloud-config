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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.boot.BootstrapRegistry;
import org.springframework.boot.BootstrapRegistryInitializer;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.cloud.config.client.ConfigClientProperties.Credentials;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.cloud.config.client.ConfigClientProperties.Discovery.DEFAULT_CONFIG_SERVER;

/**
 * @author Dave Syer
 */
public class DiscoveryClientConfigDataConfigurationTests {

	protected ConfigurableApplicationContext context;

	protected DiscoveryClient client = Mockito.mock(DiscoveryClient.class);

	protected ServiceInstance info = new DefaultServiceInstance("app:8877", "app", "foo", 8877, false);

	@AfterEach
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void offByDefault() {
		context = new SpringApplicationBuilder(TestConfig.class)
				.properties("spring.config.import=optional:configserver:")
				.addBootstrapRegistryInitializer(registry -> registry.addCloseListener(event -> {
					try {
						event.getBootstrapContext().get(ConfigServerInstanceMonitor.class);
						fail("ConfigServerInstanceMonitor was created when it shouldn't");
					}
					catch (IllegalStateException e) {
						// expected
					}
				})).run();
	}

	@Test
	public void onWhenRequested() {
		givenDiscoveryClientReturnsInfo();

		setupAndRun();

		verifyDiscoveryClientCalledOnce();
		expectConfigClientPropertiesHasConfigurationFromDiscovery();

	}

	@Test
	public void onWhenHeartbeat() {
		setupAndRun();

		givenDiscoveryClientReturnsInfo();
		verifyDiscoveryClientCalledOnce();

		this.context.publishEvent(new HeartbeatEvent(this.context, "new"));

		expectConfigClientPropertiesHasConfigurationFromDiscovery();
	}

	@Test
	public void secureWhenRequested() {
		this.info = new DefaultServiceInstance("app:443", "app", "foo", 443, true);
		givenDiscoveryClientReturnsInfo();

		setupAndRun();

		verifyDiscoveryClientCalledOnce();
		expectConfigClientPropertiesHasConfiguration("https://foo:443/");
	}

	@Test
	public void multipleInstancesReturnedFromDiscovery() {
		ServiceInstance info1 = new DefaultServiceInstance("app1:8888", "app", "localhost", 8888, true);
		ServiceInstance info2 = new DefaultServiceInstance("app2:8888", "app", "localhost1", 8888, false);
		givenDiscoveryClientReturnsInfo(info1, info2);

		setupAndRun();

		verifyDiscoveryClientCalledOnce();

		ConfigClientProperties properties = this.context.getBean(ConfigClientProperties.class);
		assertThat(properties.getUri().length).isEqualTo(2);
		Credentials credentials1 = properties.getCredentials(0);
		Credentials credentials2 = properties.getCredentials(1);
		assertThat(credentials1.getUri()).isEqualTo("https://localhost:8888/");
		assertThat(credentials2.getUri()).isEqualTo("http://localhost1:8888/");
	}

	@Test
	public void setsPasssword() {
		this.info.getMetadata().put("password", "bar");
		givenDiscoveryClientReturnsInfo();

		setupAndRun();

		ConfigClientProperties locator = this.context.getBean(ConfigClientProperties.class);
		Credentials credentials = locator.getCredentials(0);
		assertThat(credentials.getUri()).isEqualTo("http://foo:8877/");
		assertThat(credentials.getPassword()).isEqualTo("bar");
		assertThat(credentials.getUsername()).isEqualTo("user");
	}

	@Test
	public void setsPath() {
		this.info.getMetadata().put("configPath", "/bar");
		givenDiscoveryClientReturnsInfo();

		setupAndRun();

		expectConfigClientPropertiesHasConfiguration("http://foo:8877/bar");
	}

	@Test
	public void shouldFailGetConfigServerInstanceFromDiscoveryClient() {
		givenDiscoveryClientReturnsNoInfo();

		setupAndRun();

		verifyDiscoveryClientCalledOnce();
		expectConfigClientPropertiesHasDefaultConfiguration();
	}

	@Test
	public void shouldRetryAndSucceedGetConfigServerInstanceFromDiscoveryClient() {
		givenDiscoveryClientReturnsInfoOnThirdTry();

		context = setup("spring.cloud.config.retry.maxAttempts=3", "spring.cloud.config.retry.initialInterval=10",
				"spring.cloud.config.fail-fast=true").run();

		verifyDiscoveryClientCalledThreeTimes();

		this.context.publishEvent(new HeartbeatEvent(this.context, "new"));

		expectConfigClientPropertiesHasConfigurationFromDiscovery();
	}

	@Test
	public void shouldNotRetryIfNotFailFastPropertySet() {
		givenDiscoveryClientReturnsInfoOnThirdTry();

		context = setup("spring.cloud.config.retry.maxAttempts=3", "spring.cloud.config.retry.initialInterval=10")
				.run();

		verifyDiscoveryClientCalledOnce();
		expectConfigClientPropertiesHasDefaultConfiguration();
	}

	@Test
	public void shouldRetryAndFailWithExceptionGetConfigServerInstanceFromDiscoveryClient() {
		givenDiscoveryClientReturnsNoInfo();

		assertThatThrownBy(() -> context = setup("spring.cloud.config.retry.maxAttempts=3",
				"spring.cloud.config.retry.initialInterval=10", "spring.cloud.config.fail-fast=true").run())
						.isInstanceOf(IllegalStateException.class)
						.hasMessageContaining("No instances found of configserver");
	}

	@Test
	public void shouldRetryAndFailWithMessageGetConfigServerInstanceFromDiscoveryClient() {
		givenDiscoveryClientReturnsNoInfo();

		context = setup("spring.cloud.config.retry.maxAttempts=3", "spring.cloud.config.retry.initialInterval=10",
				"spring.cloud.config.fail-fast=false").run();

		expectConfigClientPropertiesHasDefaultConfiguration();
	}

	SpringApplicationBuilder setup(String... env) {
		return setup(true, env);
	}

	SpringApplicationBuilder setup(boolean addInstanceProvider, String... env) {
		SpringApplicationBuilder builder = new SpringApplicationBuilder(TestConfig.class)
				.properties(addDefaultEnv(env));
		if (addInstanceProvider) {
			builder.addBootstrapRegistryInitializer(instanceProviderBootstrapper());
			// ignore actual calls to config server since we're just testing discovery
			// client.
			builder.addBootstrapRegistryInitializer(registry -> registry
					.register(ConfigServerBootstrapper.LoaderInterceptor.class, ctx -> loadContext -> null));
		}
		return builder.addBootstrapRegistryInitializer(registry -> registry.addCloseListener(event -> {
			ConfigServerInstanceMonitor monitor = event.getBootstrapContext().get(ConfigServerInstanceMonitor.class);
			assertThat(monitor).as("ConfigServerInstanceMonitor was not created when it should").isNotNull();
		}));
	}

	protected BootstrapRegistryInitializer instanceProviderBootstrapper() {
		return registry -> registry.register(ConfigServerInstanceProvider.Function.class,
				BootstrapRegistry.InstanceSupplier.from(() -> this.client::getInstances));
	}

	protected void setupAndRun(String... env) {
		context = setup(addDefaultEnv(env)).run();
	}

	private String[] addDefaultEnv(String[] env) {
		Set<String> set = new LinkedHashSet<>();
		if (env != null && env.length > 0) {
			set.addAll(Arrays.asList(env));
		}
		set.add("spring.cloud.config.discovery.enabled=true");
		set.add("spring.config.import=optional:configserver:");
		return set.toArray(new String[0]);
	}

	void givenDiscoveryClientReturnsInfo() {
		givenDiscoveryClientReturnsInfo(this.info);
	}

	void givenDiscoveryClientReturnsInfo(ServiceInstance... instances) {
		given(this.client.getInstances(DEFAULT_CONFIG_SERVER)).willReturn(Arrays.asList(instances));
	}

	void givenDiscoveryClientReturnsNoInfo() {
		given(this.client.getInstances(DEFAULT_CONFIG_SERVER)).willReturn(Collections.emptyList());
	}

	void givenDiscoveryClientReturnsInfoOnThirdTry() {
		given(this.client.getInstances(DEFAULT_CONFIG_SERVER)).willReturn(Collections.emptyList())
				.willReturn(Collections.emptyList()).willReturn(Collections.singletonList(this.info));
	}

	void verifyDiscoveryClientCalledOnce() {
		verify(this.client).getInstances(DEFAULT_CONFIG_SERVER);
	}

	void verifyDiscoveryClientCalledThreeTimes() {
		verify(this.client, times(3)).getInstances(DEFAULT_CONFIG_SERVER);
	}

	void expectConfigClientPropertiesHasConfigurationFromDiscovery() {
		expectConfigClientPropertiesHasConfiguration("http://foo:8877/");
	}

	void expectConfigClientPropertiesHasDefaultConfiguration() {
		expectConfigClientPropertiesHasConfiguration("http://localhost:8888");
	}

	void expectConfigClientPropertiesHasConfiguration(final String expectedUri) {
		ConfigClientProperties properties = this.context.getBean(ConfigClientProperties.class);
		Credentials credentials = properties.getCredentials(0);
		assertThat(credentials.getUri()).isEqualTo(expectedUri);
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class TestConfig {

	}

}
