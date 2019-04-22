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

package org.springframework.cloud.config.server.environment;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.SystemReader;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.internal.matchers.ThrowableMessageMatcher;
import org.junit.rules.ExpectedException;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.config.EnvironmentRepositoryConfiguration;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;

/**
 * @author Andy Chan (iceycake)
 * @author Dave Syer
 *
 */
public class MultipleJGitEnvironmentRepositoryIntegrationTests {

	@Rule
	public ExpectedException expected = ExpectedException.none();

	private ConfigurableApplicationContext context;

	private File basedir = new File("target/config");

	@BeforeClass
	public static void initClass() {
		// mock Git configuration to make tests independent of local Git configuration
		SystemReader.setInstance(new MockSystemReader());
	}

	@Before
	public void init() throws Exception {
		if (this.basedir.exists()) {
			FileUtils.delete(this.basedir, FileUtils.RECURSIVE);
		}
		ConfigServerTestUtils.deleteLocalRepo("config-copy");
	}

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void defaultRepo() throws IOException {
		String defaultRepoUri = ConfigServerTestUtils.prepareLocalRepo("config-repo");
		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				.properties("spring.cloud.config.server.git.uri:" + defaultRepoUri).run();
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		repository.findOne("bar", "staging", "master");
		Environment environment = repository.findOne("bar", "staging", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
	}

	@Test
	public void mappingRepo() throws IOException {
		String defaultRepoUri = ConfigServerTestUtils.prepareLocalRepo("config-repo");
		String test1RepoUri = ConfigServerTestUtils.prepareLocalRepo("test1-config-repo");

		Map<String, Object> repoMapping = new LinkedHashMap<String, Object>();
		repoMapping.put("spring.cloud.config.server.git.repos[test1].pattern", "*test1*");
		repoMapping.put("spring.cloud.config.server.git.repos[test1].uri", test1RepoUri);
		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				.properties("spring.cloud.config.server.git.uri:" + defaultRepoUri)
				.properties(repoMapping).run();
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		repository.findOne("test1-svc", "staging", "master");
		Environment environment = repository.findOne("test1-svc", "staging", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
	}

	@Test
	public void mappingRepoWithRefreshRate() throws IOException {
		String defaultRepoUri = ConfigServerTestUtils.prepareLocalRepo("config-repo");
		String test1RepoUri = ConfigServerTestUtils.prepareLocalRepo("test1-config-repo");

		Map<String, Object> repoMapping = new LinkedHashMap<String, Object>();
		repoMapping.put("spring.cloud.config.server.git.repos[test1].pattern", "*test1*");
		repoMapping.put("spring.cloud.config.server.git.repos[test1].uri", test1RepoUri);
		repoMapping.put("spring.cloud.config.server.git.refresh-rate", "30");
		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				.properties("spring.cloud.config.server.git.uri:" + defaultRepoUri)
				.properties(repoMapping).run();
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		repository.findOne("test1-svc", "staging", "master");
		Environment environment = repository.findOne("test1-svc", "staging", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(30).isEqualTo(((MultipleJGitEnvironmentRepository) repository)
				.getRepos().get("test1").getRefreshRate());
	}

	@Test
	public void mappingRepoWithProfile() throws IOException {
		String defaultRepoUri = ConfigServerTestUtils.prepareLocalRepo("config-repo");
		String test1RepoUri = ConfigServerTestUtils.prepareLocalRepo("test1-config-repo");

		Map<String, Object> repoMapping = new LinkedHashMap<String, Object>();
		repoMapping.put("spring.cloud.config.server.git.repos[test1].pattern",
				"*/staging");
		repoMapping.put("spring.cloud.config.server.git.repos[test1].uri", test1RepoUri);
		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				.properties("spring.cloud.config.server.git.uri:" + defaultRepoUri)
				.properties(repoMapping).run();
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		repository.findOne("test1-svc", "staging", "master");
		Environment environment = repository.findOne("test1-svc", "staging", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
	}

	@Test
	public void mappingRepoWithProfileDefaultPatterns() throws IOException {
		String defaultRepoUri = ConfigServerTestUtils.prepareLocalRepo("config-repo");
		String test1RepoUri = ConfigServerTestUtils.prepareLocalRepo("test1-config-repo");

		Map<String, Object> repoMapping = new LinkedHashMap<String, Object>();
		repoMapping.put("spring.cloud.config.server.git.repos[test1].pattern",
				"*/staging");
		repoMapping.put("spring.cloud.config.server.git.repos[test1].uri", test1RepoUri);
		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				.properties("spring.cloud.config.server.git.uri:" + defaultRepoUri)
				.properties(repoMapping).run();
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		repository.findOne("test1-svc", "staging", "master");
		Environment environment = repository.findOne("test1-svc", "staging,cloud",
				"master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
	}

	@Test
	public void mappingRepoWithProfiles() throws IOException {
		String defaultRepoUri = ConfigServerTestUtils.prepareLocalRepo("config-repo");
		String test1RepoUri = ConfigServerTestUtils.prepareLocalRepo("test1-config-repo");

		Map<String, Object> repoMapping = new LinkedHashMap<String, Object>();
		repoMapping.put("spring.cloud.config.server.git.repos[test1].pattern[0]",
				"*/staging,*");
		repoMapping.put("spring.cloud.config.server.git.repos[test1].pattern[1]",
				"*/*,staging");
		repoMapping.put("spring.cloud.config.server.git.repos[test1].pattern[2]",
				"*/staging");
		repoMapping.put("spring.cloud.config.server.git.repos[test1].uri", test1RepoUri);
		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				.properties("spring.cloud.config.server.git.uri:" + defaultRepoUri)
				.properties(repoMapping).run();
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		repository.findOne("test1-svc", "staging", "master");
		Environment environment = repository.findOne("test1-svc", "cloud,staging",
				"master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		environment = repository.findOne("test1-svc", "staging,cloud", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
	}

	@Test
	public void mappingRepoWithJustUri() throws IOException {
		String defaultRepoUri = ConfigServerTestUtils.prepareLocalRepo("config-repo");
		String test1RepoUri = ConfigServerTestUtils.prepareLocalRepo("test1-config-repo");

		Map<String, Object> repoMapping = new LinkedHashMap<String, Object>();
		repoMapping.put("spring.cloud.config.server.git.repos.test1-svc.uri",
				test1RepoUri);
		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				.properties("spring.cloud.config.server.git.uri:" + defaultRepoUri)
				.properties(repoMapping).run();
		EnvironmentRepository repository = this.context
				.getBean(EnvironmentRepository.class);
		repository.findOne("test1-svc", "staging", "master");
		Environment environment = repository.findOne("test1-svc", "staging", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
	}

	@Test
	public void nonWritableBasedir() throws IOException {
		String defaultRepoUri = ConfigServerTestUtils.prepareLocalRepo("config-repo");
		this.expected.expectCause(ThrowableMessageMatcher
				.hasMessage(containsString("Cannot write parent")));
		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				.properties("spring.cloud.config.server.git.uri:" + defaultRepoUri,
						"spring.cloud.config.server.git.basedir:/tmp")
				.run();
	}

	@Test
	public void patternMatchingShortcutSyntax() throws IOException {
		String defaultRepoUri = ConfigServerTestUtils.prepareLocalRepo("config-repo");
		String test1RepoUri = ConfigServerTestUtils.prepareLocalRepo("test1-config-repo");

		this.context = new SpringApplicationBuilder(TestConfiguration.class)
				.web(WebApplicationType.NONE)
				.properties("spring.cloud.config.server.git.repos.admin:" + test1RepoUri,
						"spring.cloud.config.server.git.uri:" + defaultRepoUri)
				.run();

		MultipleJGitEnvironmentProperties properties = this.context
				.getBean(MultipleJGitEnvironmentProperties.class);
		Assertions.assertThat(properties.getRepos()).hasSize(1).containsOnlyKeys("admin");
	}

	@Configuration
	@EnableConfigurationProperties(ConfigServerProperties.class)
	@Import({ PropertyPlaceholderAutoConfiguration.class,
			EnvironmentRepositoryConfiguration.class })
	protected static class TestConfiguration {

	}

}
