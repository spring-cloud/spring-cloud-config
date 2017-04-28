package org.springframework.cloud.config.client;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.cloud.ClassPathExclusions;
import org.springframework.cloud.FilteredClassPathRunner;

@RunWith(FilteredClassPathRunner.class)
@ClassPathExclusions({ "spring-retry-*.jar", "spring-boot-starter-aop-*.jar" })
public class DiscoveryClientConfigServiceBootstrapConfigurationNoSpringRetryTests
		extends BaseDiscoveryClientConfigServiceBootstrapConfigurationTests {

	@Test
	public void shouldFailWithExceptionGetConfigServerInstanceFromDiscoveryClient()
			throws Exception {
		givenDiscoveryClientReturnsNoInfo();

		expectNoInstancesOfConfigServerException();

		setup("spring.cloud.config.discovery.enabled=true",
				"spring.cloud.config.failFast=true");
	}

	@Test
	public void shouldFailWithMessageGetConfigServerInstanceFromDiscoveryClient()
			throws Exception {
		givenDiscoveryClientReturnsNoInfo();

		setup("spring.cloud.config.discovery.enabled=true",
				"spring.cloud.config.failFast=false");

		expectDiscoveryClientConfigServiceBootstrapConfigurationIsSetup();
		expectConfigClientPropertiesHasDefaultConfiguration();
		verifyDiscoveryClientCalledOnce();
	}

	@Test
	public void shouldSucceedGetConfigServerInstanceFromDiscoveryClient()
			throws Exception {
		givenDiscoveryClientReturnsInfo();

		setup("spring.cloud.config.discovery.enabled=true",
				"spring.cloud.config.failFast=true");

		expectDiscoveryClientConfigServiceBootstrapConfigurationIsSetup();
		expectConfigClientPropertiesHasConfigurationFromEureka();
		verifyDiscoveryClientCalledOnce();
	}

}