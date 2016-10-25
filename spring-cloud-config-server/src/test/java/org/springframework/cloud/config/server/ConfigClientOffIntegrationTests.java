package org.springframework.cloud.config.server;

import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.ConfigClientOffIntegrationTests.TestConfiguration;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.resource.ResourceRepository;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.anyString;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestConfiguration.class, webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
public class ConfigClientOffIntegrationTests {

	@LocalServerPort
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
				+ this.port + "/foo/development/", Environment.class);
		assertTrue(environment.getPropertySources().isEmpty());
	}

	@Test
	public void configClientDisabled() throws Exception {
		assertEquals(0, BeanFactoryUtils.beanNamesForTypeIncludingAncestors(this.context,
				ConfigServicePropertySourceLocator.class).length);
	}

	@Configuration
	@Import(ConfigServerApplication.class)
	protected static class TestConfiguration {

		@Bean
		public EnvironmentRepository environmentRepository() {
			EnvironmentRepository repository = Mockito.mock(EnvironmentRepository.class);
			given(repository.findOne(anyString(), anyString(), anyString())).willReturn(new Environment("", ""));
			return repository;
		}

		@Bean
		public ResourceRepository resourceRepository() {
			ResourceRepository repository = Mockito.mock(ResourceRepository.class);
			given(repository.findOne(anyString(), anyString(), anyString(), anyString())).willReturn(new ByteArrayResource("".getBytes()));
			return repository;
		}

	}

}
