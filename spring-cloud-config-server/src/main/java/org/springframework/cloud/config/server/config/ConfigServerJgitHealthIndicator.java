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
package org.springframework.cloud.config.server.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentRepository;
import org.springframework.util.CollectionUtils;

/**
 * @author Ivan Vasyliev
 */
public class ConfigServerJgitHealthIndicator extends AbstractHealthIndicator {

	private MultipleJGitEnvironmentRepository environmentRepository;
	private ConfigServerProperties configServerProperties;

	public ConfigServerJgitHealthIndicator(
			MultipleJGitEnvironmentRepository environmentRepository,
			ConfigServerProperties configServerProperties) {
		this.environmentRepository = environmentRepository;
		this.configServerProperties = configServerProperties;
	}

	@Override
	protected void doHealthCheck(Health.Builder builder) throws Exception {
		List<Map<String, Object>> details = new ArrayList<>();
		String profile = configServerProperties.getDefaultProfile();
		String label = configServerProperties.getDefaultLabel();
		String defaultApplicationName = configServerProperties
				.getDefaultApplicationName();
		try {
			Environment environment = environmentRepository
					.findOne(defaultApplicationName, profile, label);
			Map<String, Object> detail = new LinkedHashMap<>();
			if (environment.getProfiles() != null
					&& environment.getProfiles().length > 0) {
				detail.put("environment", Arrays.asList(environment.getProfiles()));
			}
			if (!CollectionUtils.isEmpty(environment.getPropertySources())) {
				List<String> sources = new ArrayList<>();
				for (PropertySource source : environment.getPropertySources()) {
					sources.add(source.getName());
				}
				detail.put("sources", sources);
			}
			detail.put("branch", environment.getLabel());
			detail.put("lastSeenCommitId", environment.getVersion());
			detail.put("lastSeenCommitInfo", environment.getDescription());
			detail.put("settings", getSettings());
			details.add(detail);
		}
		catch (Exception e) {
			builder.withDetail("settings", getSettings());
			builder.down(e);
			return;
		}
		builder.withDetail("gitRepos", details);
		builder.up();
	}

	private Map<String, Object> getSettings() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("defaultApplication", configServerProperties.getDefaultApplicationName());
		map.put("environment", configServerProperties.getDefaultProfile());
		map.put("branch", configServerProperties.getDefaultLabel());
		map.put("settingsOverrides", configServerProperties.getOverrides());
		map.put("decryptionByClients", !configServerProperties.getEncrypt().isEnabled());
		map.put("localBasedir", environmentRepository.getBasedir());
		map.put("remoteUri", environmentRepository.getUri());
		map.put("gitTimeoutSecs", environmentRepository.getTimeout());
		map.put("user", environmentRepository.getUsername());
		map.put("password", escapePassword(environmentRepository.getPassword()));
		return map;
	}

	private String escapePassword(String password) {
		if (password == null || password.isEmpty()) {
			return "";
		}
		if (password.length() < 3) {
			return "******";
		}
		return password.substring(0, 3) + "***";
	}
}
