package org.springframework.cloud.config.server.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.util.CollectionUtils;

/**
 * @author Spencer Gibb
 */
@ConfigurationProperties("spring.cloud.config.server.health")
public class ConfigServerHealthIndicator extends AbstractHealthIndicator {

	private EnvironmentRepository environmentRepository;

	private Map<String, Repository> repositories = new LinkedHashMap<>();

	public ConfigServerHealthIndicator(EnvironmentRepository environmentRepository) {
		this.environmentRepository = environmentRepository;
	}

	@PostConstruct
	public void init() {
		if (this.repositories.isEmpty()) {
			this.repositories.put("app", new Repository());
		}
	}

	@Override
	protected void doHealthCheck(Health.Builder builder) throws Exception {
		builder.up();
		List<Map<String, Object>> details = new ArrayList<>();
		for (String name : this.repositories.keySet()) {
			Repository repository = this.repositories.get(name);
			String application = (repository.getName() == null)? name : repository.getName();
			String profiles = repository.getProfiles();

			try {
				Environment environment = this.environmentRepository.findOne(application, profiles, repository.getLabel());

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
			} catch (Exception e) {
				HashMap<String, String> map = new HashMap<>();
				map.put("application", application);
				map.put("profiles", profiles);
				builder.withDetail("repository", map);
				builder.down(e);
				return;
			}
		}
		builder.withDetail("repositories", details);

	}

	public Map<String, Repository> getRepositories() {
		return this.repositories;
	}

	public void setRepositories(Map<String, Repository> repositories) {
		this.repositories = repositories;
	}

	public static class Repository {
		private String name;
		private String profiles = "default";
		private String label;

		public Repository() {  }

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
