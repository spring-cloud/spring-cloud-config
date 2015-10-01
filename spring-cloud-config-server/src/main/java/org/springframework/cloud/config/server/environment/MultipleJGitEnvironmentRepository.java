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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.PatternMatchUtils;
import org.springframework.util.StringUtils;

/**
 * {@link EnvironmentRepository} that based on one or more git repositories. Can be
 * configured just like a single {@link JGitEnvironmentRepository}, for the "default"
 * properties, and then additional repositories can be registered by name. The simplest
 * form of the registration is just a map from name to uri (plus credentials if needed),
 * where each app has its own git repository. As well as a name you can provide a pattern
 * that matches on the application name (or even a list of patterns). Each sub-repository
 * additionally can have its own search paths (subdirectories inside the top level of the
 * repository).
 *
 * @author Andy Chan (iceycake)
 * @author Dave Syer
 *
 */
@ConfigurationProperties("spring.cloud.config.server.git")
public class MultipleJGitEnvironmentRepository extends JGitEnvironmentRepository {

	private Map<String, PatternMatchingJGitEnvironmentRepository> repos = new LinkedHashMap<String, PatternMatchingJGitEnvironmentRepository>();

	public MultipleJGitEnvironmentRepository(ConfigurableEnvironment environment) {
		super(environment);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		super.afterPropertiesSet();
		for (String name : this.repos.keySet()) {
			PatternMatchingJGitEnvironmentRepository repo = this.repos.get(name);
			repo.setEnvironment(getEnvironment());
			if (!StringUtils.hasText(repo.getName())) {
				repo.setName(name);
			}
			if (repo.getPattern() == null || repo.getPattern().length == 0) {
				repo.setPattern(new String[] { name });
			}
			if (getTimeout() != 0 && repo.getTimeout() == 0) {
				repo.setTimeout(getTimeout());
			}
			repo.afterPropertiesSet();
		}
	}

	public void setRepos(Map<String, PatternMatchingJGitEnvironmentRepository> repos) {
		this.repos.putAll(repos);
	}

	public Map<String, PatternMatchingJGitEnvironmentRepository> getRepos() {
		return this.repos;
	}

	@Override
	public Locations getLocations(String application, String profile, String label) {
		for (PatternMatchingJGitEnvironmentRepository repository : this.repos.values()) {
			Environment source = repository.findOne(application, profile, label);
			if (source != null) {
				return repository.getLocations(application, profile, label);
			}
		}
		return super.getLocations(application, profile, label);
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		for (PatternMatchingJGitEnvironmentRepository repository : this.repos.values()) {
			Environment source = repository.findOne(application, profile, label);
			if (source != null) {
				return source;
			}
		}

		return super.findOne(application, profile, label);
	}

	public static class PatternMatchingJGitEnvironmentRepository
			extends JGitEnvironmentRepository {

		private String[] pattern = new String[0];
		private String name;

		public PatternMatchingJGitEnvironmentRepository() {
			super(null);
		}

		public PatternMatchingJGitEnvironmentRepository(String uri) {
			this();
			setUri(uri);
		}

		@Override
		public Environment findOne(String application, String profile, String label) {

			if (this.pattern == null || this.pattern.length == 0) {
				return null;
			}

			if (PatternMatchUtils.simpleMatch(this.pattern,
					application + "/" + profile)) {
				return super.findOne(application, profile, label);
			}

			return null;

		}

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String[] getPattern() {
			return this.pattern;
		}

		public void setPattern(String[] pattern) {
			Collection<String> patterns = new ArrayList<>();
			List<String> otherProfiles = new ArrayList<>();
			for (String p : pattern) {
				if (p != null) {
					if (!p.contains("/")) {
						// Match any profile
						patterns.add(p + "/*");
					}
					if (!p.endsWith("*")) {
						// If user supplies only one profile, allow others
						otherProfiles.add(p + ",*");
					}
				}
				patterns.add(p);
			}
			patterns.addAll(otherProfiles);
			if (!patterns.contains(null)) {
				// Make sure they are unique
				patterns = new LinkedHashSet<>(patterns);
			}
			this.pattern = patterns.toArray(new String[0]);
		}

	}

}
