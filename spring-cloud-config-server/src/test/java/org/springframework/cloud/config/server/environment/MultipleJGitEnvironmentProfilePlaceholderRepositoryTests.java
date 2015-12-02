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
package org.springframework.cloud.config.server.environment;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentRepository.PatternMatchingJGitEnvironmentRepository;
import org.springframework.cloud.config.server.environment.SearchPathLocator.Locations;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.core.env.StandardEnvironment;

/**
 * @author Dave Syer
 *
 */
public class MultipleJGitEnvironmentProfilePlaceholderRepositoryTests {

	private StandardEnvironment environment = new StandardEnvironment();
	private MultipleJGitEnvironmentRepository repository = new MultipleJGitEnvironmentRepository(
			this.environment);

	@Before
	public void init() throws Exception {
		String defaultUri = ConfigServerTestUtils.prepareLocalRepo("config-repo");
		this.repository.setUri(defaultUri);
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
		return repo;
	}

	@Test
	public void defaultRepo() {
		Environment environment = this.repository.findOne("bar", "staging", "master");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals(this.repository.getUri() + "/bar.properties",
				environment.getPropertySources().get(0).getName());
		assertVersion(environment);
	}

	@Test
	public void mappingRepo() {
		Environment environment = this.repository.findOne("application",
				"test1-config-repo", "master");
		assertEquals(1, environment.getPropertySources().size());
		assertEquals(
				getUri("*").replace("{profile}", "test1-config-repo")
						+ "/application.yml",
				environment.getPropertySources().get(0).getName());
		assertVersion(environment);
	}

	@Test
	public void otherMappingRepo() {
		Environment environment = this.repository.findOne("application",
				"test2-config-repo", "master");
		assertEquals(1, environment.getPropertySources().size());
		assertEquals(
				getUri("*").replace("{profile}", "test2-config-repo")
						+ "/application.properties",
				environment.getPropertySources().get(0).getName());
		assertVersion(environment);
	}

	@Test
	public void locationsTwoProfiles() throws Exception {
		Locations locations = this.repository.getLocations("application",
				"test1-config-repo,test2-config-repo", "master");
		assertEquals(1, locations.getLocations().length);
		assertEquals(
				new File(getUri("*").replace("{profile}", "test2-config-repo")
						.replace("file:", "")).getCanonicalPath(),
				new File(locations.getLocations()[0].replace("file:", ""))
						.getCanonicalPath());
	}

	@Test
	public void locationsMissingProfile() throws Exception {
		Locations locations = this.repository.getLocations("application",
				"not-there,another-not-there", "master");
		assertEquals(1, locations.getLocations().length);
		assertEquals(
				new File(this.repository.getUri().replace("file:", ""))
						.getCanonicalPath(),
				new File(locations.getLocations()[0].replace("file:", ""))
						.getCanonicalPath());
	}

	@Test
	public void twoMappingRepos() {
		Environment environment = this.repository.findOne("application",
				"test1-config-repo,test2-config-repo,missing-config-repo", "master");
		assertEquals(1, environment.getPropertySources().size());
		assertEquals(
				getUri("*").replace("{profile}", "test2-config-repo")
						+ "/application.properties",
				environment.getPropertySources().get(0).getName());
		assertVersion(environment);
		assertArrayEquals(environment.getProfiles(), new String[] { "test1-config-repo",
				"test2-config-repo", "missing-config-repo" });
	}

	private void assertVersion(Environment environment) {
		String version = environment.getVersion();
		assertNotNull("version was null", version);
		assertTrue("version length was wrong",
				version.length() >= 40 && version.length() <= 64);
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
