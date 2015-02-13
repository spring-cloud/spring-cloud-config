package org.springframework.cloud.bootstrap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.bootstrap.BootstrapOrderingAutoConfigurationIntegrationTests.Application;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
@IntegrationTest("encrypt.key:deadbeef")
@ActiveProfiles("encrypt")
public class BootstrapOrderingAutoConfigurationIntegrationTests {
	
	@Autowired
	private ConfigurableEnvironment environment;

	@Test
	public void bootstrapPropertiesExist() {
		assertTrue(environment.getPropertySources().contains("bootstrap"));
	}

	@Test
	public void normalPropertiesDecrypted() {
		assertEquals("foo", environment.resolvePlaceholders("${foo}"));
	}

	@Test
	public void bootstrapPropertiesDecrypted() {
		assertEquals("bar", environment.resolvePlaceholders("${bar}"));
	}

	@EnableAutoConfiguration
	@Configuration
	protected static class Application {

	}

}
