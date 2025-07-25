/*
 * Copyright 2018-2025 the original author or authors.
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

package org.springframework.cloud.config.server.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.util.CollectionUtils;

/**
 * @author Spencer Gibb
 */
@ConfigurationProperties("spring.cloud.config.server.health")
public class ConfigServerHealthIndicator extends AbstractHealthIndicator {

	private static Log logger = LogFactory.getLog(ConfigServerHealthIndicator.class);

	private EnvironmentRepository environmentRepository;

	private Map<String, Repository> repositories = new LinkedHashMap<>();

	private String downHealthStatus = Status.DOWN.getCode();

	private final boolean acceptEmpty;

	// autowired required or boot constructor binding produces an error
	@Autowired
	public ConfigServerHealthIndicator(EnvironmentRepository environmentRepository, ConfigServerProperties properties) {
		this.environmentRepository = environmentRepository;
		this.acceptEmpty = properties.isAcceptEmpty();
	}

	@PostConstruct
	public void init() {
		if (this.repositories.isEmpty()) {
			this.repositories.put("app", new Repository());
		}
	}

	@Override
	protected void doHealthCheck(Health.Builder builder) {
		builder.up();
		List<Map<String, Object>> details = new ArrayList<>();
		for (String name : this.repositories.keySet()) {
			Repository repository = this.repositories.get(name);
			String application = (repository.getName() == null) ? name : repository.getName();
			String profiles = repository.getProfiles();

			try {
				Environment environment = this.environmentRepository.findOne(application, profiles,
						repository.getLabel(), false);

				HashMap<String, Object> detail = new HashMap<>();
				detail.put("name", environment.getName());
				detail.put("label", environment.getLabel());
				if (environment.getProfiles() != null && environment.getProfiles().length > 0) {
					detail.put("profiles", Arrays.asList(environment.getProfiles()));
				}

				if (!CollectionUtils.isEmpty(environment.getPropertySources())) {
					List<String> sources = new ArrayList<>();
					for (PropertySource source : environment.getPropertySources()) {
						sources.add(source.getName());
					}
					detail.put("sources", sources);
				}
				details.add(detail);
			}
			catch (Exception e) {
				logger.debug("Could not read repository: " + application, e);
				HashMap<String, String> map = new HashMap<>();
				map.put("application", application);
				map.put("profiles", profiles);
				builder.withDetail("repository", map);
				builder.status(this.downHealthStatus).withException(e);
				return;
			}
		}
		if (!this.acceptEmpty && (details.isEmpty() || details.stream().noneMatch(d -> d.containsKey("sources")))) {
			// If accept-empty is false and no repositories are found, meaning details is
			// empty, then set status to DOWN
			// If there are details but none of them have sources, then set status to DOWN
			builder.down().withDetail("acceptEmpty", this.acceptEmpty);
		}
		builder.withDetail("repositories", details);

	}

	public Map<String, Repository> getRepositories() {
		return this.repositories;
	}

	public void setRepositories(Map<String, Repository> repositories) {
		this.repositories = repositories;
	}

	public String getDownHealthStatus() {
		return downHealthStatus;
	}

	public void setDownHealthStatus(String downHealthStatus) {
		this.downHealthStatus = downHealthStatus;
	}

	/**
	 * Repository properties.
	 */
	public static class Repository {

		private String name;

		private String profiles = "default";

		private String label;

		public Repository() {
		}

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getProfiles() {
			return this.profiles;
		}

		public void setProfiles(String profiles) {
			this.profiles = profiles;
		}

		public String getLabel() {
			return this.label;
		}

		public void setLabel(String label) {
			this.label = label;
		}

	}

}
