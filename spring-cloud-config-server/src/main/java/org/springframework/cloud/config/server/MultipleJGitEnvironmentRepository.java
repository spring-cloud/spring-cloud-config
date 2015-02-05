/*
 * Copyright 2013-2014 the original author or authors.
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

	private String uri;
	private String username;
	private String password;

	private List<Map<String, String>> repos = new ArrayList<Map<String, String>>();
	private Map<String, JGitEnvironmentRepository> repoCache = new LinkedHashMap<String, JGitEnvironmentRepository>();
	private JGitEnvironmentRepository defaultRepo;

	private ConfigurableEnvironment environment;

	@Override
	public void afterPropertiesSet() throws Exception {
		Assert.state(uri != null,
				"You need to configure a uri for the default git repository");
		
		defaultRepo = createJGitEnvironmentRepository(this.uri, this.username,
				this.password);		
	}

	public MultipleJGitEnvironmentRepository(ConfigurableEnvironment environment) {
		this.environment = environment;
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

	public void setRepos(List<Map<String, String>> repos) {
		this.repos.addAll(repos);
	}

	public List<Map<String, String>> getRepos() {
		return this.repos;
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		JGitEnvironmentRepository repo = this.defaultRepo;

		for (Map<String, String> repoKeyValue : repos) {
			String repoName = repoKeyValue.get(REPO_NAME);
			String repoUri = repoKeyValue.get(REPO_URI);
			String repoUsername = repoKeyValue.get(REPO_USERNAME);
			String repoPassword = repoKeyValue.get(REPO_PASSWORD);

			if (PatternMatchUtils.simpleMatch(repoName, application)) {
				repo = repoCache.get(repoName);

				if (repo == null) {
					repo = createJGitEnvironmentRepository(repoUri, repoUsername,
							repoPassword);

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
			String username, String password) {
		JGitEnvironmentRepository repo = new JGitEnvironmentRepository(this.environment);
		repo.setUri(uri);
		repo.setUsername(username);
		repo.setPassword(password);

		return repo;
	}
}
