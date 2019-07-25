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
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.util.SystemReader;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentRepository.PatternMatchingJGitEnvironmentRepository;
import org.springframework.cloud.config.server.environment.SearchPathLocator.Locations;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class MultipleJGitEnvironmentProfilePlaceholderRepositoryTests {

	private StandardEnvironment environment = new StandardEnvironment();

	private MultipleJGitEnvironmentRepository repository = new MultipleJGitEnvironmentRepository(
			this.environment, new MultipleJGitEnvironmentProperties());

	@BeforeClass
	public static void initClass() {
		// mock Git configuration to make tests independent of local Git configuration
		SystemReader.setInstance(new MockSystemReader());
	}

	@Before
	public void init() throws Exception {
		String defaultUri = ConfigServerTestUtils.prepareLocalRepo("config-repo");
		this.repository.setUri(defaultUri);
		this.repository.setBasedir(new File("target/repos/parent_repo"));
		this.repository.setRepos(createRepositories());
	}

	private Map<String, PatternMatchingJGitEnvironmentRepository> createRepositories()
			throws Exception {
		String test1Uri = ConfigServerTestUtils.prepareLocalRepo("test1-config-repo");
		ConfigServerTestUtils.prepareLocalRepo("test2-config-repo");

		Map<String, PatternMatchingJGitEnvironmentRepository> repos = new HashMap<>();
		repos.put("templates", createRepository("test", "*-config-repo",
				test1Uri.replace("test1-config-repo", "{profile}")));
		return repos;
	}

	private PatternMatchingJGitEnvironmentRepository createRepository(String name,
			String pattern, String uri) {
		PatternMatchingJGitEnvironmentRepository repo = new PatternMatchingJGitEnvironmentRepository();
		repo.setEnvironment(this.environment);
		repo.setName(name);
		repo.setPattern(new String[] { pattern });
		repo.setUri(uri);
		repo.setBasedir(new File("target/repos/pattern_repos", name));
		return repo;
	}

	@Test
	public void defaultRepo() {
		Environment environment = this.repository.findOne("bar", "staging", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(environment.getPropertySources().get(0).getName())
				.isEqualTo(this.repository.getUri() + "/bar.properties");
		assertVersion(environment);
	}

	@Test
	public void mappingRepo() {
		Environment environment = this.repository.findOne("application",
				"test1-config-repo", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		String uri = getUri("*").replace("{profile}", "test1-config-repo");
		assertThat(environment.getPropertySources().get(0).getName())
				.isEqualTo(uri + "/application.yml");
		assertVersion(environment);
		assertThat(StringUtils.cleanPath(getRepository(uri).getBasedir().toString()))
				.contains("target/repos");
	}

	@Test
	public void otherMappingRepo() {
		Environment environment = this.repository.findOne("application",
				"test2-config-repo", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getPropertySources().get(0).getName())
				.isEqualTo(getUri("*").replace("{profile}", "test2-config-repo")
						+ "/application.properties");
		assertVersion(environment);
	}

	@Test
	public void locationsTwoProfiles() throws Exception {
		Locations locations = this.repository.getLocations("application",
				"test1-config-repo,test2-config-repo", "master");
		assertThat(locations.getLocations().length).isEqualTo(1);
		assertThat(new File(locations.getLocations()[0].replace("file:", ""))
				.getCanonicalPath()).isEqualTo(
						new File(getUri("*").replace("{profile}", "test2-config-repo")
								.replace("file:", "")).getCanonicalPath());
	}

	@Test
	public void locationsMissingProfile() throws Exception {
		Locations locations = this.repository.getLocations("application",
				"not-there,another-not-there", "master");
		assertThat(locations.getLocations().length).isEqualTo(1);
		assertThat(new File(locations.getLocations()[0].replace("file:", ""))
				.getCanonicalPath())
						.isEqualTo(new File(this.repository.getUri().replace("file:", ""))
								.getCanonicalPath());
	}

	@Test
	public void twoMappingRepos() {
		Environment environment = this.repository.findOne("application",
				"test1-config-repo,test2-config-repo,missing-config-repo", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getPropertySources().get(0).getName())
				.isEqualTo(getUri("*").replace("{profile}", "test2-config-repo")
						+ "/application.properties");
		assertVersion(environment);
		assertThat(new String[] { "test1-config-repo", "test2-config-repo",
				"missing-config-repo" }).isEqualTo(environment.getProfiles());
	}

	@SuppressWarnings("unchecked")
	private JGitEnvironmentRepository getRepository(String uri) {
		Map<String, JGitEnvironmentRepository> repos = (Map<String, JGitEnvironmentRepository>) ReflectionTestUtils
				.getField(this.repository, "placeholders");
		return repos.get(uri);
	}

	private void assertVersion(Environment environment) {
		String version = environment.getVersion();
		assertThat(version).as("version was null").isNotNull();
		assertThat(version.length() >= 40 && version.length() <= 64)
				.as("version length was wrong").isTrue();
	}

	private String getUri(String pattern) {
		String uri = null;

		Map<String, PatternMatchingJGitEnvironmentRepository> repoMappings = this.repository
				.getRepos();

		for (PatternMatchingJGitEnvironmentRepository repo : repoMappings.values()) {
			String[] mappingPattern = repo.getPattern();
			if (mappingPattern != null && mappingPattern.length != 0) {
				uri = repo.getUri();
				break;
			}
		}

		return uri;
	}

}
