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

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import org.springframework.boot.BootstrapRegistry;
import org.springframework.boot.BootstrapRegistryInitializer;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.config.client.ConfigClientProperties.Credentials;
import org.springframework.cloud.test.ClassPathExclusions;
import org.springframework.cloud.test.ModifiedClassPathRunner;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.cloud.config.client.ConfigClientProperties.Discovery.DEFAULT_CONFIG_SERVER;

/**
 * @author Dave Syer
 */
@RunWith(ModifiedClassPathRunner.class)
@ClassPathExclusions({ "spring-retry-*.jar", "spring-boot-starter-aop-*.jar" })
public class DiscoveryClientConfigDataConfigurationNoRetryTests {

	protected ConfigurableApplicationContext context;

	protected DiscoveryClient client = Mockito.mock(DiscoveryClient.class);

	protected ServiceInstance info = new DefaultServiceInstance("app:8877", "app", "foo", 8877, false);

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void shouldFailWithExceptionGetConfigServerInstanceFromDiscoveryClient() throws Exception {
		givenDiscoveryClientReturnsNoInfo();

		assertThatThrownBy(() -> context = setup("spring.cloud.config.discovery.enabled=true",
				"spring.cloud.config.fail-fast=true").run()).isInstanceOf(IllegalStateException.class)
						.hasMessageContaining("No instances found of configserver");
	}

	@Test
	public void shouldFailWithMessageGetConfigServerInstanceFromDiscoveryClient() throws Exception {
		givenDiscoveryClientReturnsNoInfo();

		context = setup("spring.cloud.config.discovery.enabled=true", "spring.cloud.config.fail-fast=false").run();

		// expectDiscoveryClientConfigServiceBootstrapConfigurationIsSetup();
		expectConfigClientPropertiesHasDefaultConfiguration();
		verifyDiscoveryClientCalledOnce();
	}

	@Test
	public void shouldSucceedGetConfigServerInstanceFromDiscoveryClient() throws Exception {
		givenDiscoveryClientReturnsInfo();

		context = setup("spring.cloud.config.discovery.enabled=true", "spring.cloud.config.fail-fast=true").run();

		// expectDiscoveryClientConfigServiceBootstrapConfigurationIsSetup();
		// expectConfigClientPropertiesHasConfigurationFromEureka();
		verifyDiscoveryClientCalledOnce();
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

	void verifyDiscoveryClientCalledOnce() {
		verify(this.client).getInstances(DEFAULT_CONFIG_SERVER);
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
