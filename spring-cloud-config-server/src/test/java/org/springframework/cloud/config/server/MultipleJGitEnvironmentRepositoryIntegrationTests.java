/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.config.server;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.EnvironmentRepositoryConfiguration;
import org.springframework.cloud.config.server.ConfigServerTestUtils;
import org.springframework.cloud.config.server.EnvironmentRepository;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author Andy Chan (iceycake)
 *
 */
public class MultipleJGitEnvironmentRepositoryIntegrationTests {

	private ConfigurableApplicationContext context;

	private File basedir = new File("target/config");

	@Before
	public void init() throws Exception {
		if (basedir.exists()) {
			FileUtils.delete(basedir, FileUtils.RECURSIVE);
		}
		ConfigServerTestUtils.deleteLocalRepo("config-copy");
	}

	@After
	public void close() {
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void defaultRepo() throws IOException {
		String defaultRepoUri = ConfigServerTestUtils.prepareLocalRepo("config-repo");
		context = new SpringApplicationBuilder(TestConfiguration.class).web(false)
				.properties("spring.cloud.config.server.git.uri:" + defaultRepoUri).run();
		EnvironmentRepository repository = context.getBean(EnvironmentRepository.class);
		repository.findOne("bar", "staging", "master");
		Environment environment = repository.findOne("bar", "staging", "master");
		assertEquals(2, environment.getPropertySources().size());
	}

	@Test
	public void mappingRepo() throws IOException {
		String defaultRepoUri = ConfigServerTestUtils.prepareLocalRepo("config-repo");
		String test1RepoUri = ConfigServerTestUtils.prepareLocalRepo("test1-config-repo");

		Map<String, Object> repoMapping = new LinkedHashMap<String, Object>();
		repoMapping.put("spring.cloud.config.server.git.repos[test1].pattern", "*test1*");
		repoMapping.put("spring.cloud.config.server.git.repos[test1].uri", test1RepoUri);		
		context = new SpringApplicationBuilder(TestConfiguration.class).web(false)
				.properties("spring.cloud.config.server.git.uri:" + defaultRepoUri)
				.properties(repoMapping).run();
		EnvironmentRepository repository = context.getBean(EnvironmentRepository.class);
		repository.findOne("test1-svc", "staging", "master");
		Environment environment = repository.findOne("test1-svc", "staging", "master");
		assertEquals(2, environment.getPropertySources().size());
	}

	@Test
	public void mappingRepoWithJustUri() throws IOException {
		String defaultRepoUri = ConfigServerTestUtils.prepareLocalRepo("config-repo");
		String test1RepoUri = ConfigServerTestUtils.prepareLocalRepo("test1-config-repo");

		Map<String, Object> repoMapping = new LinkedHashMap<String, Object>();
		repoMapping.put("spring.cloud.config.server.git.repos.test1-svc", test1RepoUri);		
		context = new SpringApplicationBuilder(TestConfiguration.class).web(false)
				.properties("spring.cloud.config.server.git.uri:" + defaultRepoUri)
				.properties(repoMapping).run();
		EnvironmentRepository repository = context.getBean(EnvironmentRepository.class);
		repository.findOne("test1-svc", "staging", "master");
		Environment environment = repository.findOne("test1-svc", "staging", "master");
		assertEquals(2, environment.getPropertySources().size());
	}

	@Configuration
	@Import({ PropertyPlaceholderAutoConfiguration.class, EnvironmentRepositoryConfiguration.class })
	protected static class TestConfiguration {
	}

}
