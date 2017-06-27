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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentRepository.PatternMatchingJGitEnvironmentRepository;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.core.env.StandardEnvironment;

/**
 * @author Andy Chan (iceycake)
 * @author Dave Syer
 * @author Spencer Gibb
 *
 */
public class MultipleJGitEnvironmentRepositoryTests {

	@Rule
	public ExpectedException exception = ExpectedException.none();

	private StandardEnvironment environment = new StandardEnvironment();
	private MultipleJGitEnvironmentRepository repository;


	@Before
	public void init() throws Exception {
		String defaultUri = ConfigServerTestUtils.prepareLocalRepo("config-repo");
		this.repository = new MultipleJGitEnvironmentRepository(this.environment);
		this.repository.setUri(defaultUri);
		this.repository.setRepos(createRepositories());
	}

	private Map<String, PatternMatchingJGitEnvironmentRepository> createRepositories()
			throws Exception {
		String test1Uri = ConfigServerTestUtils.prepareLocalRepo("test1-config-repo");

		Map<String, PatternMatchingJGitEnvironmentRepository> repos = new HashMap<>();
		repos.put("test1", createRepository("test1", "*test1*", test1Uri));
		return repos;
	}

	private PatternMatchingJGitEnvironmentRepository createRepository(String name,
			String pattern, String uri) {
		PatternMatchingJGitEnvironmentRepository repo = new PatternMatchingJGitEnvironmentRepository();
		repo.setEnvironment(this.environment);
		repo.setName(name);
		repo.setPattern(new String[] { pattern });
		repo.setUri(uri);
		repo.setBasedir(new File(this.repository.getBasedir().getParentFile(), name));
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

	private void assertVersion(Environment environment) {
		String version = environment.getVersion();
		assertNotNull("version was null", version);
		assertTrue("version length was wrong",
				version.length() >= 40 && version.length() <= 64);
	}

	@Test
	public void defaultRepoNested() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo("another-config-repo");
		this.repository.setUri(uri);
		this.repository.setSearchPaths(new String[] { "sub" });
		this.repository.findOne("bar", "staging", "master");
		Environment environment = this.repository.findOne("bar", "staging", "master");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals(this.repository.getUri() + "/sub/application.yml",
				environment.getPropertySources().get(0).getName());
		assertVersion(environment);
	}

	@Test
	public void defaultRepoBranch() {
		Environment environment = this.repository.findOne("bar", "staging", "raw");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals(this.repository.getUri() + "/bar.properties",
				environment.getPropertySources().get(0).getName());
		assertVersion(environment);
	}

	@Test
	public void defaultRepoTag() {
		Environment environment = this.repository.findOne("bar", "staging", "foo");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals(this.repository.getUri() + "/bar.properties",
				environment.getPropertySources().get(0).getName());
		assertVersion(environment);
	}

	@Test
	public void defaultRepoTwice() {
		this.repository.findOne("bar", "staging", "master");
		Environment environment = this.repository.findOne("bar", "staging", "master");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals(this.repository.getUri() + "/bar.properties",
				environment.getPropertySources().get(0).getName());
		assertVersion(environment);
	}

	@Test
	public void defaultRepoBasedir() {
		repository.setBasedir(new File("target/testBase"));
		assertThat(repository.getBasedir().toString(), containsString("target/testBase"));
		assertThat(repository.getRepos().get("test1").getBasedir().toString(),
				containsString("/test1"));
	}

	@Test
	public void mappingRepo() {
		Environment environment = this.repository.findOne("test1-svc", "staging",
				"master");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals(getUri("*test1*") + "/test1-svc.properties",
				environment.getPropertySources().get(0).getName());
		assertVersion(environment);
	}

	@Test
	public void defaultLabel() {
		this.repository.setDefaultLabel("raw");
		Environment environment = this.repository.findOne("bar", "staging", null);
		assertEquals("raw", environment.getLabel());
		assertEquals(2, environment.getPropertySources().size());
		assertEquals(this.repository.getUri() + "/bar.properties",
				environment.getPropertySources().get(0).getName());
		assertVersion(environment);
	}

	@Test
	public void mappingRepoWithDefaultLabel() {
		Environment environment = this.repository.findOne("test1-svc", "staging", null);
		assertEquals("master", environment.getLabel());
		assertEquals(2, environment.getPropertySources().size());
		assertEquals(getUri("*test1*") + "/test1-svc.properties",
				environment.getPropertySources().get(0).getName());
		assertVersion(environment);
	}

	@Test
	public void shouldSetTransportConfigCallback() throws Exception {
		TransportConfigCallback mockCallback1 = mock(TransportConfigCallback.class);
		TransportConfigCallback mockCallback2 = mock(TransportConfigCallback.class);

		PatternMatchingJGitEnvironmentRepository repo1 = createRepository("test1", "*test1*", "test1Uri");

		PatternMatchingJGitEnvironmentRepository repo2 = createRepository("test2", "*test2*", "test2Uri");
		repo2.setTransportConfigCallback(mockCallback2);

		Map<String, PatternMatchingJGitEnvironmentRepository> repos = new HashMap<>();
		repos.put("test1", repo1);
		repos.put("test2", repo2);

		this.repository.setRepos(repos);
		this.repository.setTransportConfigCallback(mockCallback1);
		this.repository.afterPropertiesSet();

		assertEquals(repo1.getTransportConfigCallback(), mockCallback1);
		assertEquals(repo2.getTransportConfigCallback(), mockCallback2);
	}

	@Test
	// test for gh-700
	public void basedirCreatedIfNotExists() throws Exception {
		Path tempDir = Files.createTempDirectory("basedirCreatedTest");
		File parent = new File(tempDir.toFile(), "parent");
		File basedir = new File(parent, "basedir");
		this.repository.setBasedir(basedir);

		assertThat(basedir).doesNotExist();

		this.repository.afterPropertiesSet();

		assertThat(basedir).exists();
	}

	@Test
	// test for gh-700
	public void exceptionThrownIfBasedirDoesnotExistAndCannotBeCreated() throws Exception {
		File basedir = mock(File.class);
		File absoluteBasedir = mock(File.class);
		when(basedir.getAbsoluteFile()).thenReturn(absoluteBasedir);

		when(absoluteBasedir.exists()).thenReturn(false);
		when(absoluteBasedir.mkdir()).thenReturn(false);

		this.repository.setBasedir(basedir);

		this.exception.expect(IllegalStateException.class);
		this.exception.expectMessage("Basedir does not exist and can not be created:");

		this.repository.afterPropertiesSet();
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
