package org.springframework.cloud.config.server;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.config.Environment;
import org.springframework.core.env.StandardEnvironment;

public class MultipleJGitEnvironmentRepositoryTests {

	private StandardEnvironment environment = new StandardEnvironment();
	private MultipleJGitEnvironmentRepository repository = new MultipleJGitEnvironmentRepository(environment);

	@Before
	public void init() throws Exception {
		String defaultUri = ConfigServerTestUtils.prepareLocalRepo("config-repo");
				
		repository.setUri(defaultUri);
		repository.setRepos(createRepositoryMappings());
	}
	
	private List<Map<String, Object>> createRepositoryMappings() throws Exception {
		String test1Uri = ConfigServerTestUtils.prepareLocalRepo("test1-config-repo");

		List<Map<String, Object>> mappings = new ArrayList<Map<String, Object>>();
		mappings.add(createRepositoryMapping("*test1*", test1Uri));
		
		return mappings;
	}
	
	private Map<String, Object> createRepositoryMapping(String pattern, String uri) {
		Map<String, Object> repoMapping = new LinkedHashMap<String, Object>();
		repoMapping.put("patterns", pattern);
		repoMapping.put("uri", uri);
		
		return repoMapping;
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
		assertEquals(getUri("*test1*") + "test1-svc.properties", environment
				.getPropertySources().get(0).getName());
	}	

	private String getUri(String pattern) {
		String uri = null;
		
		List<Map<String, Object>> repoMappings  = repository.getRepos();
		
		for (Map<String, Object> mapping : repoMappings) {
			String mappingPattern = (String)mapping.get("patterns");
			if (mappingPattern != null) {
				uri = (String)mapping.get("uri");
				break;
			}
		}
		
		return uri;
	}
}
