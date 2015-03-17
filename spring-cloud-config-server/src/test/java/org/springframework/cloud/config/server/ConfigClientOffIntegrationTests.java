package org.springframework.cloud.config.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.TestRestTemplate;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.ConfigClientOffIntegrationTests.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestConfiguration.class)
@IntegrationTest("server.port:0")
@WebAppConfiguration
@ActiveProfiles("test")
public class ConfigClientOffIntegrationTests {

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private ApplicationContext context;

	@BeforeClass
	public static void init() throws IOException {
		ConfigServerTestUtils.prepareLocalRepo();
	}

	@Test
	public void contextLoads() {
		Environment environment = new TestRestTemplate().getForObject("http://localhost:"
				+ port + "/foo/development/", Environment.class);
		assertTrue(environment.getPropertySources().isEmpty());
	}

	@Test
	public void configClientDisabled() throws Exception {
		assertEquals(0, BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context,
				ConfigServicePropertySourceLocator.class).length);
	}
	
	@Configuration
	@Import(ConfigServerApplication.class)
	protected static class TestConfiguration {
		
		@Bean
		public EnvironmentRepository environmentRepository() {
			return Mockito.mock(EnvironmentRepository.class);
		}
		
	}

}
