package org.springframework.cloud.config.client;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.cloud.test.ClassPathExclusions;
import org.springframework.cloud.test.ModifiedClassPathRunner;

@RunWith(ModifiedClassPathRunner.class)
@ClassPathExclusions({ "spring-retry-*.jar", "spring-boot-starter-aop-*.jar" })
public class DiscoveryClientConfigServiceBootstrapConfigurationNoSpringRetryTests
		extends BaseDiscoveryClientConfigServiceBootstrapConfigurationTests {

	@Test
	public void shouldFailWithExceptionGetConfigServerInstanceFromDiscoveryClient()
			throws Exception {
		givenDiscoveryClientReturnsNoInfo();

		expectNoInstancesOfConfigServerException();

		setup("spring.cloud.config.discovery.enabled=true",
				"spring.cloud.config.fail-fast=true");
	}

	@Test
	public void shouldFailWithMessageGetConfigServerInstanceFromDiscoveryClient()
			throws Exception {
		givenDiscoveryClientReturnsNoInfo();

		setup("spring.cloud.config.discovery.enabled=true",
				"spring.cloud.config.fail-fast=false");

		expectDiscoveryClientConfigServiceBootstrapConfigurationIsSetup();
		expectConfigClientPropertiesHasDefaultConfiguration();
		verifyDiscoveryClientCalledOnce();
	}

	@Test
	public void shouldSucceedGetConfigServerInstanceFromDiscoveryClient()
			throws Exception {
		givenDiscoveryClientReturnsInfo();

		setup("spring.cloud.config.discovery.enabled=true",
				"spring.cloud.config.fail-fast=true");

		expectDiscoveryClientConfigServiceBootstrapConfigurationIsSetup();
		expectConfigClientPropertiesHasConfigurationFromEureka();
		verifyDiscoveryClientCalledOnce();
	}

}