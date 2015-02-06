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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.config.Environment;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.Assert;
import org.springframework.util.PatternMatchUtils;

/**
 * @author Andy Chan (iceycake)
 *
 */
@ConfigurationProperties("spring.cloud.config.server.git")
public class MultipleJGitEnvironmentRepository implements EnvironmentRepository,
		InitializingBean {

	private static final String REPO_NAME = "name";
	private static final String REPO_URI = "uri";
	private static final String REPO_USERNAME = "username";
	private static final String REPO_PASSWORD = "password";
	private static final String REPO_SEARCHPATHS = "searchPaths";

	private String uri;
	private String username;
	private String password;
	private String[] searchPaths = new String[0];

	private List<Map<String, Object>> repos = new ArrayList<Map<String, Object>>();
	private Map<String, JGitEnvironmentRepository> repoCache = new LinkedHashMap<String, JGitEnvironmentRepository>();
	private JGitEnvironmentRepository defaultRepo;

	private ConfigurableEnvironment environment;

	@Override
	public void afterPropertiesSet() throws Exception {
		Assert.state(uri != null,
				"You need to configure a uri for the default git repository");
		
		defaultRepo = createJGitEnvironmentRepository(this.uri, this.username,
				this.password, this.searchPaths);		
	}

	public MultipleJGitEnvironmentRepository(ConfigurableEnvironment environment) {
		this.environment = environment;
	}

	public void setSearchPaths(String... searchPaths) {
		this.searchPaths = searchPaths;
	}
	
	public void setUri(String uri) {
		while (uri.endsWith("/")) {
			uri = uri.substring(0, uri.length() - 1);
		}
		this.uri = uri;
	}

	public String getUri() {
		return uri;
	}

	public void setRepos(List<Map<String, Object>> repos) {
		this.repos.addAll(repos);
	}

	public List<Map<String, Object>> getRepos() {
		return this.repos;
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		JGitEnvironmentRepository repo = this.defaultRepo;

		for (Map<String, Object> repoKeyValue : repos) {
			String repoName = (String)repoKeyValue.get(REPO_NAME);
			String repoUri = (String)repoKeyValue.get(REPO_URI);
			String repoUsername = (String)repoKeyValue.get(REPO_USERNAME);
			String repoPassword = (String)repoKeyValue.get(REPO_PASSWORD);
			String[] repoSearchPaths = new String[0];
			LinkedHashMap<String, String> o = (LinkedHashMap<String, String>)repoKeyValue.get(REPO_SEARCHPATHS);
			if (o != null) {
				repoSearchPaths = o.values().toArray(new String[o.values().size()]);
			}

			if (PatternMatchUtils.simpleMatch(repoName, application)) {
				repo = repoCache.get(repoName);

				if (repo == null) {
					repo = createJGitEnvironmentRepository(repoUri, repoUsername,
							repoPassword, repoSearchPaths);

					synchronized (repoCache) {
						repoCache.put(repoName, repo);
					}
				}

				break;
			}
		}

		return repo.findOne(application, profile, label);
	}

	private JGitEnvironmentRepository createJGitEnvironmentRepository(String uri,
			String username, String password, String[] searchPaths) {
		JGitEnvironmentRepository repo = new JGitEnvironmentRepository(this.environment);
		repo.setUri(uri);
		repo.setUsername(username);
		repo.setPassword(password);
		repo.setSearchPaths(searchPaths);

		return repo;
	}
}
