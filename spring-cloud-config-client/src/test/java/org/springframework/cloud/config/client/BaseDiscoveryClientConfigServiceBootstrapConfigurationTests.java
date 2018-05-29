package org.springframework.cloud.config.client;

import java.util.Arrays;
import java.util.Collections;

import org.junit.After;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;
import org.springframework.cloud.config.client.ConfigClientProperties.Credentials;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.cloud.config.client.ConfigClientProperties.Discovery.DEFAULT_CONFIG_SERVER;

public abstract class BaseDiscoveryClientConfigServiceBootstrapConfigurationTests {

	@Rule
	public ExpectedException expectedException = ExpectedException.none();

	protected AnnotationConfigApplicationContext context;

	protected DiscoveryClient client = Mockito.mock(DiscoveryClient.class);

	protected ServiceInstance info = new DefaultServiceInstance("app", "foo", 8877,
			false);

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	void givenDiscoveryClientReturnsNoInfo() {
		given(this.client.getInstances(DEFAULT_CONFIG_SERVER))
				.willReturn(Collections.<ServiceInstance> emptyList());
	}

	void givenDiscoveryClientReturnsInfo() {
		given(this.client.getInstances(DEFAULT_CONFIG_SERVER))
				.willReturn(Arrays.asList(this.info));
	}

	void givenDiscoveryClientReturnsInfoForMultipleInstances(ServiceInstance info1,
			ServiceInstance info2) {
		given(this.client.getInstances(DEFAULT_CONFIG_SERVER))
				.willReturn(Arrays.asList(info1, info2));
	}

	void givenDiscoveryClientReturnsInfoOnThirdTry() {
		given(this.client.getInstances(DEFAULT_CONFIG_SERVER))
				.willReturn(Collections.<ServiceInstance> emptyList())
				.willReturn(Collections.<ServiceInstance> emptyList())
				.willReturn(Arrays.asList(this.info));
	}

	void expectNoInstancesOfConfigServerException() {
		expectedException.expect(IllegalStateException.class);
		expectedException.expectMessage(
				"No instances found of configserver (" + DEFAULT_CONFIG_SERVER + ")");
	}

	void expectDiscoveryClientConfigServiceBootstrapConfigurationIsSetup() {
		assertEquals(1, this.context.getBeanNamesForType(
				DiscoveryClientConfigServiceBootstrapConfiguration.class).length);
	}

	void expectConfigClientPropertiesHasDefaultConfiguration() {
		expectConfigClientPropertiesHasConfiguration("http://localhost:8888");
	}

	void expectConfigClientPropertiesHasConfigurationFromEureka() {
		expectConfigClientPropertiesHasConfiguration("http://foo:8877/");
	}

	void expectConfigClientPropertiesHasConfiguration(final String expectedUri) {
		ConfigClientProperties properties = this.context
				.getBean(ConfigClientProperties.class);
		Credentials credentials = properties.getCredentials(0);
		assertEquals(expectedUri, credentials.getUri());
	}

	void expectConfigClientPropertiesHasMultipleUris(final String expectedUri1,
			final String expectedUri2) {
		ConfigClientProperties properties = this.context
				.getBean(ConfigClientProperties.class);
		assertEquals(2, properties.getUri().length);
		Credentials credentials1 = properties.getCredentials(0);
		Credentials credentials2 = properties.getCredentials(1);
		assertEquals(expectedUri1, credentials1.getUri());
		assertEquals(expectedUri2, credentials2.getUri());
	}

	void verifyDiscoveryClientCalledThreeTimes() {
		verify(this.client, times(3)).getInstances(DEFAULT_CONFIG_SERVER);
	}

	void verifyDiscoveryClientCalledOnce() {
		verify(this.client).getInstances(DEFAULT_CONFIG_SERVER);
	}

	void setup(String... env) {
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
