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

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.mockito.Mockito;

import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;
import org.springframework.cloud.config.client.ConfigClientProperties.Credentials;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.cloud.config.client.ConfigClientProperties.Discovery.DEFAULT_CONFIG_SERVER;

public abstract class BaseDiscoveryClientConfigServiceBootstrapConfigurationTests {

	protected AnnotationConfigApplicationContext context;

	protected DiscoveryClient client = Mockito.mock(DiscoveryClient.class);

	protected ServiceInstance info = new DefaultServiceInstance("app:8877", "app", "foo", 8877, false);

	@AfterEach
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	void givenDiscoveryClientReturnsNoInfo() {
		given(this.client.getInstances(DEFAULT_CONFIG_SERVER)).willReturn(Collections.<ServiceInstance>emptyList());
	}

	void givenDiscoveryClientReturnsInfo() {
		given(this.client.getInstances(DEFAULT_CONFIG_SERVER)).willReturn(Collections.singletonList(this.info));
	}

	void givenDiscoveryClientReturnsInfoForMultipleInstances(ServiceInstance info1, ServiceInstance info2) {
		given(this.client.getInstances(DEFAULT_CONFIG_SERVER)).willReturn(Arrays.asList(info1, info2));
	}

	void givenDiscoveryClientReturnsInfoOnThirdTry() {
		given(this.client.getInstances(DEFAULT_CONFIG_SERVER)).willReturn(Collections.<ServiceInstance>emptyList())
			.willReturn(Collections.<ServiceInstance>emptyList())
			.willReturn(Collections.singletonList(this.info));
	}

	void expectDiscoveryClientConfigServiceBootstrapConfigurationIsSetup() {
		assertThat(this.context.getBeanNamesForType(DiscoveryClientConfigServiceBootstrapConfiguration.class).length)
			.isEqualTo(1);
	}

	void expectConfigClientPropertiesHasDefaultConfiguration() {
		expectConfigClientPropertiesHasConfiguration("http://localhost:8888");
	}

	void expectConfigClientPropertiesHasConfigurationFromEureka() {
		expectConfigClientPropertiesHasConfiguration("http://foo:8877/");
	}

	void expectConfigClientPropertiesHasConfiguration(final String expectedUri) {
		ConfigClientProperties properties = this.context.getBean(ConfigClientProperties.class);
		Credentials credentials = properties.getCredentials(0);
		assertThat(credentials.getUri()).isEqualTo(expectedUri);
	}

	void expectConfigClientPropertiesHasMultipleUris(final String expectedUri1, final String expectedUri2) {
		ConfigClientProperties properties = this.context.getBean(ConfigClientProperties.class);
		assertThat(properties.getUri().length).isEqualTo(2);
		Credentials credentials1 = properties.getCredentials(0);
		Credentials credentials2 = properties.getCredentials(1);
		assertThat(credentials1.getUri()).isEqualTo(expectedUri1);
		assertThat(credentials2.getUri()).isEqualTo(expectedUri2);
	}

	void verifyDiscoveryClientCalledThreeTimes() {
		verify(this.client, times(3)).getInstances(DEFAULT_CONFIG_SERVER);
	}

	void verifyDiscoveryClientCalledOnce() {
		verify(this.client).getInstances(DEFAULT_CONFIG_SERVER);
	}

	void setup(String... env) {
		setup(true, true, env);
	}

	void setup(boolean refresh, boolean registerDiscoveryClient, String... env) {
		this.context = new AnnotationConfigApplicationContext();
		TestPropertyValues.of(env).applyTo(this.context);
		TestPropertyValues.of("eureka.client.enabled=false").applyTo(this.context);
		if (registerDiscoveryClient) {
			this.context.getDefaultListableBeanFactory().registerSingleton("discoveryClient", this.client);
		}
		this.context.register(UtilAutoConfiguration.class, PropertyPlaceholderAutoConfiguration.class,
				DiscoveryClientConfigServiceBootstrapConfiguration.class, ConfigServiceBootstrapConfiguration.class,
				ConfigClientProperties.class);
		if (refresh) {
			this.context.refresh();
		}
	}

}
