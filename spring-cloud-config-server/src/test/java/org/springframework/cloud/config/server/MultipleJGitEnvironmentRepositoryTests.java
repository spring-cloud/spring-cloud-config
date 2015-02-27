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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.ConfigServerTestUtils;
import org.springframework.cloud.config.server.MultipleJGitEnvironmentRepository;
import org.springframework.cloud.config.server.MultipleJGitEnvironmentRepository.PatternMatchingJGitEnvironmentRepository;
import org.springframework.core.env.StandardEnvironment;

/**
 * 
 * @author Andy Chan (iceycake)
 *
 */
public class MultipleJGitEnvironmentRepositoryTests {

	private StandardEnvironment environment = new StandardEnvironment();
	private MultipleJGitEnvironmentRepository repository = new MultipleJGitEnvironmentRepository(environment);

	@Before
	public void init() throws Exception {
		String defaultUri = ConfigServerTestUtils.prepareLocalRepo("config-repo");
				
		repository.setUri(defaultUri);
		repository.setRepos(createRepositories());
	}
	
	private Map<String, PatternMatchingJGitEnvironmentRepository> createRepositories() throws Exception {
		String test1Uri = ConfigServerTestUtils.prepareLocalRepo("test1-config-repo");

		Map<String, PatternMatchingJGitEnvironmentRepository> repos = new HashMap<String, PatternMatchingJGitEnvironmentRepository>();
		repos.put("test1", createRepository("test1", "*test1*", test1Uri));
		
		return repos;
	}
	
	private PatternMatchingJGitEnvironmentRepository createRepository(String name, String pattern, String uri) {
		PatternMatchingJGitEnvironmentRepository repo = new PatternMatchingJGitEnvironmentRepository();
		repo.setEnvironment(environment);
		repo.setName(name);
		repo.setPattern(new String[] {pattern});
		repo.setUri(uri);
		return repo;
	}

	@Test
	public void defaultRepo() {
		Environment environment = repository.findOne("bar", "staging", "master");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals(repository.getUri() + "/bar.properties", environment
				.getPropertySources().get(0).getName());
	}	

	@Test
	public void defaultRepoNested() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo("another-config-repo");
		repository.setUri(uri);
		repository.setSearchPaths(new String[] {"sub"});
		repository.findOne("bar", "staging", "master");
		Environment environment = repository.findOne("bar", "staging", "master");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals(repository.getUri() + "/sub/application.yml", environment
				.getPropertySources().get(0).getName());
	}

	@Test
	public void defaultRepoBranch() {
		Environment environment = repository.findOne("bar", "staging", "raw");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals(repository.getUri() + "/bar.properties", environment
				.getPropertySources().get(0).getName());
	}

	@Test
	public void defaultRepoTag() {
		Environment environment = repository.findOne("bar", "staging", "foo");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals(repository.getUri() + "/bar.properties", environment
				.getPropertySources().get(0).getName());
	}

	@Test
	public void defaultRepoBasedir() {
		repository.findOne("bar", "staging", "master");
		Environment environment = repository.findOne("bar", "staging", "master");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals(repository.getUri() + "/bar.properties", environment
				.getPropertySources().get(0).getName());
	}

	@Test
	public void mappingRepo() {
		Environment environment = repository.findOne("test1-svc", "staging", "master");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals(getUri("*test1*") + "/test1-svc.properties", environment
				.getPropertySources().get(0).getName());
	}	

	private String getUri(String pattern) {
		String uri = null;
		
		Map<String, PatternMatchingJGitEnvironmentRepository> repoMappings  = repository.getRepos();
		
		for (PatternMatchingJGitEnvironmentRepository repo : repoMappings.values()) {
			String[] mappingPattern = repo.getPattern();
			if (mappingPattern != null && mappingPattern.length!=0) {
				uri = repo.getUri();
				break;
			}
		}
		
		return uri;
	}
}
