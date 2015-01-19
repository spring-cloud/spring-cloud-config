package org.springframework.cloud.bootstrap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.bootstrap.BootstrapDisabledAutoConfigurationIntegrationTests.Application;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
@IntegrationTest({ "server.port:0", "spring.cloud.bootstrap.enabled:false" })
@WebAppConfiguration
@ActiveProfiles("test")
public class BootstrapDisabledAutoConfigurationIntegrationTests {

	@Value("${local.server.port}")
	private int port;

	@Test
	public void contextLoads() {
	}

	@EnableAutoConfiguration
	@Configuration
	protected static class Application {

	}

}
