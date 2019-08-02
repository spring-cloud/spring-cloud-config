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

package org.springframework.cloud.config.server;

import java.io.IOException;

import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.util.SystemReader;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.ConfigClientOnIntegrationTests.TestConfiguration;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.resource.ResourceRepository;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.anyString;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestConfiguration.class,
		properties = "spring.cloud.config.enabled:true",
		webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext
public class ConfigClientOnIntegrationTests {

	private static String localRepo = null;

	@LocalServerPort
	private int port;

	@Autowired
	private ApplicationContext context;

	@BeforeClass
	public static void init() throws IOException {
		// mock Git configuration to make tests independent of local Git configuration
		SystemReader.setInstance(new MockSystemReader());

		localRepo = ConfigServerTestUtils.prepareLocalRepo();
	}

	@AfterClass
	public static void after() throws IOException {
		ConfigServerTestUtils.deleteLocalRepo(localRepo);
	}

	@Test
	public void contextLoads() {
		Environment environment = new TestRestTemplate().getForObject(
				"http://localhost:" + this.port + "/foo/development/", Environment.class);
		assertThat(environment.getPropertySources().isEmpty()).isTrue();
	}

	@Test
	public void configClientEnabled() throws Exception {
		assertThat(BeanFactoryUtils.beanNamesForTypeIncludingAncestors(this.context,
				ConfigServicePropertySourceLocator.class).length).isEqualTo(1);
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableConfigServer
	protected static class TestConfiguration {

		@Bean
		public EnvironmentRepository environmentRepository() {
			EnvironmentRepository repository = Mockito.mock(EnvironmentRepository.class);
			given(repository.findOne(anyString(), anyString(), anyString(), anyBoolean()))
					.willReturn(new Environment("", ""));
			return repository;
		}

		@Bean
		public ResourceRepository resourceRepository() {
			ResourceRepository repository = Mockito.mock(ResourceRepository.class);
			given(repository.findOne(anyString(), anyString(), anyString(), anyString()))
					.willReturn(new ByteArrayResource("".getBytes()));
			return repository;
		}

	}

}
