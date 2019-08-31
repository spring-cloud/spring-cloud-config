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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeanUtils;
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
 * @author Gareth Clay
 *
 */
public class MultipleJGitEnvironmentRepository extends JGitEnvironmentRepository {

	/**
	 * Map of repository identifier to location and other properties.
	 */
	private Map<String, PatternMatchingJGitEnvironmentRepository> repos = new LinkedHashMap<>();

	private Map<String, JGitEnvironmentRepository> placeholders = new LinkedHashMap<>();

	public MultipleJGitEnvironmentRepository(ConfigurableEnvironment environment,
			MultipleJGitEnvironmentProperties properties) {
		super(environment, properties);
		properties.getRepos().forEach((name, props) -> this.repos.put(name,
				new PatternMatchingJGitEnvironmentRepository(environment, props)));
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
			if (repo.getTransportConfigCallback() == null) {
				repo.setTransportConfigCallback(getTransportConfigCallback());
			}
			if (getTimeout() != 0 && repo.getTimeout() == 0) {
				repo.setTimeout(getTimeout());
			}
			if (getRefreshRate() != 0 && repo.getRefreshRate() == 0) {
				repo.setRefreshRate(getRefreshRate());
			}
			String user = repo.getUsername();
			String passphrase = repo.getPassphrase();
			if (user == null) {
				repo.setUsername(getUsername());
				repo.setPassword(getPassword());
			}
			if (passphrase == null) {
				repo.setPassphrase(getPassphrase());
			}
			if (isSkipSslValidation()) {
				repo.setSkipSslValidation(true);
			}
			repo.afterPropertiesSet();
		}
		if (!getBasedir().exists() && !getBasedir().mkdirs()) {
			throw new IllegalStateException(
					"Basedir does not exist and can not be created: " + getBasedir());
		}
		if (!getBasedir().getParentFile().canWrite()) {
			throw new IllegalStateException(
					"Cannot write parent of basedir (please configure a writable location): "
							+ getBasedir());
		}
	}

	public Map<String, PatternMatchingJGitEnvironmentRepository> getRepos() {
		return this.repos;
	}

	public void setRepos(Map<String, PatternMatchingJGitEnvironmentRepository> repos) {
		this.repos.putAll(repos);
	}

	@Override
	public Locations getLocations(String application, String profile, String label) {
		for (PatternMatchingJGitEnvironmentRepository repository : this.repos.values()) {
			if (repository.matches(application, profile, label)) {
				for (JGitEnvironmentRepository candidate : getRepositories(repository,
						application, profile, label)) {
					try {
						Environment source = candidate.findOne(application, profile,
								label, false);
						if (source != null) {
							return candidate.getLocations(application, profile, label);
						}
					}
					catch (Exception e) {
						if (this.logger.isDebugEnabled()) {
							this.logger.debug("Cannot retrieve resource locations from "
									+ candidate.getUri() + ", cause: ("
									+ e.getClass().getSimpleName() + ") "
									+ e.getMessage(), e);
						}
						continue;
					}
				}
			}
		}
		JGitEnvironmentRepository candidate = getRepository(this, application, profile,
				label);
		if (candidate == this) {
			return super.getLocations(application, profile, label);
		}
		return candidate.getLocations(application, profile, label);
	}

	@Override
	public Environment findOne(String application, String profile, String label,
			boolean includeOrigin) {
		for (PatternMatchingJGitEnvironmentRepository repository : this.repos.values()) {
			if (repository.matches(application, profile, label)) {
				for (JGitEnvironmentRepository candidate : getRepositories(repository,
						application, profile, label)) {
					try {
						if (label == null) {
							label = candidate.getDefaultLabel();
						}
						Environment source = candidate.findOne(application, profile,
								label, includeOrigin);
						if (source != null) {
							return source;
						}
					}
					catch (Exception e) {
						if (this.logger.isDebugEnabled()) {
							this.logger.debug(
									"Cannot load configuration from " + candidate.getUri()
											+ ", cause: (" + e.getClass().getSimpleName()
											+ ") " + e.getMessage(),
									e);
						}
						continue;
					}
				}
			}
		}
		JGitEnvironmentRepository candidate = getRepository(this, application, profile,
				label);
		if (label == null) {
			label = candidate.getDefaultLabel();
		}
		if (candidate == this) {
			return super.findOne(application, profile, label, includeOrigin);
		}
		return candidate.findOne(application, profile, label, includeOrigin);
	}

	private List<JGitEnvironmentRepository> getRepositories(
			JGitEnvironmentRepository repository, String application, String profile,
			String label) {
		List<JGitEnvironmentRepository> list = new ArrayList<>();
		String[] profiles = profile == null ? new String[] { null }
				: StringUtils.commaDelimitedListToStringArray(profile);
		for (int i = profiles.length; i-- > 0;) {
			list.add(getRepository(repository, application, profiles[i], label));
		}
		return list;
	}

	JGitEnvironmentRepository getRepository(JGitEnvironmentRepository repository,
			String application, String profile, String label) {
		if (!repository.getUri().contains("{")) {
			return repository;
		}
		String key = repository.getUri();

		// cover the case where label is in the uri, but no label was sent with the
		// request
		if (key.contains("{label}") && label == null) {
			label = repository.getDefaultLabel();
		}
		if (application != null) {
			key = key.replace("{application}", application);
		}
		if (profile != null) {
			key = key.replace("{profile}", profile);
		}
		if (label != null) {
			key = key.replace("{label}", label);
		}
		if (!this.placeholders.containsKey(key)) {
			this.placeholders.put(key, getRepository(repository, key));
		}
		return this.placeholders.get(key);
	}

	private JGitEnvironmentRepository getRepository(JGitEnvironmentRepository source,
			String uri) {
		JGitEnvironmentRepository repository = new JGitEnvironmentRepository(null,
				new JGitEnvironmentProperties());
		File basedir = repository.getBasedir();
		BeanUtils.copyProperties(source, repository);
		repository.setUri(uri);
		repository.setBasedir(new File(source.getBasedir(), basedir.getName()));
		return repository;
	}

	@Override
	public void setOrder(int order) {
		super.setOrder(order);
	}

	/**
	 * A {@link JGitEnvironmentProperties} that matches patterns.
	 */
	public static class PatternMatchingJGitEnvironmentRepository
			extends JGitEnvironmentRepository {

		/**
		 * Pattern to match on application name and profiles.
		 */
		private String[] pattern = new String[0];

		/**
		 * Name of repository (same as map key by default).
		 */
		private String name;

		public PatternMatchingJGitEnvironmentRepository() {
			super(null, new JGitEnvironmentProperties());
		}

		public PatternMatchingJGitEnvironmentRepository(
				ConfigurableEnvironment environment,
				MultipleJGitEnvironmentProperties.PatternMatchingJGitEnvironmentProperties properties) {
			super(environment, properties);
			this.setPattern(properties.getPattern());
			this.name = properties.getName();
		}

		public boolean matches(String application, String profile, String label) {
			if (this.pattern == null || this.pattern.length == 0) {
				return false;
			}
			String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
			for (int i = profiles.length; i-- > 0;) {
				if (PatternMatchUtils.simpleMatch(this.pattern,
						application + "/" + profiles[i])) {
					return true;
				}
			}
			return false;
		}

		@Override
		public Environment findOne(String application, String profile, String label,
				boolean includeOrigin) {

			if (this.pattern == null || this.pattern.length == 0) {
				return null;
			}

			if (PatternMatchUtils.simpleMatch(this.pattern,
					application + "/" + profile)) {
				return super.findOne(application, profile, label, includeOrigin);
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
