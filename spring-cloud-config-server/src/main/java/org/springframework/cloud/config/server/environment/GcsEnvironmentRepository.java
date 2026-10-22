/*
 * Copyright 2013-present the original author or authors.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.YamlProcessor;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.support.PathUtils;
import org.springframework.core.Ordered;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.InputStreamResource;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.util.StringUtils;

/**
 * Environment repository backed by Google Cloud Storage.
 */
public class GcsEnvironmentRepository implements EnvironmentRepository, Ordered {

	private static final Log LOG = LogFactory.getLog(GcsEnvironmentRepository.class);

	private static final List<String> SUPPORTED_EXTENSIONS = List.of(".properties", ".json", ".yml", ".yaml");

	private static final List<String> EMPTY_EXTENSION = List.of("");

	private final Storage storage;

	private final GcsEnvironmentProperties properties;

	private final ConfigServerProperties serverProperties;

	private final PathMatcher pathMatcher = new AntPathMatcher();

	public GcsEnvironmentRepository(Storage storage, GcsEnvironmentProperties properties,
			ConfigServerProperties serverProperties) {
		this.storage = storage;
		this.properties = properties;
		this.serverProperties = serverProperties;

		if (!StringUtils.hasText(properties.getBucket())) {
			throw new IllegalArgumentException("Google Cloud Storage bucket must be configured");
		}
	}

	@Override
	public int getOrder() {
		return this.properties.getOrder();
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		String resolvedApplication = StringUtils.hasText(application) ? application
				: this.serverProperties.getDefaultApplicationName();
		String resolvedProfile = StringUtils.hasText(profile) ? profile : this.serverProperties.getDefaultProfile();
		String resolvedLabel = StringUtils.hasText(label) ? label : this.serverProperties.getDefaultLabel();

		validateApplication(resolvedApplication);
		validateProfiles(resolvedProfile);
		validateLabel(resolvedLabel);

		String[] profiles = StringUtils.commaDelimitedListToStringArray(resolvedProfile);
		Environment environment = new Environment(resolvedApplication, profiles);
		environment.setLabel(resolvedLabel);

		List<String> labels;
		if (StringUtils.hasText(resolvedLabel) && resolvedLabel.contains(",")) {
			labels = new ArrayList<>(Arrays.asList(StringUtils.commaDelimitedListToStringArray(resolvedLabel)));
			Collections.reverse(labels);
		}
		else {
			labels = Collections.singletonList(resolvedLabel);
		}

		List<String> applications = new ArrayList<>(
				Arrays.stream(StringUtils.commaDelimitedListToStringArray(resolvedApplication))
					.map(app -> app.replace(" ", ""))
					.toList());
		Collections.reverse(applications);

		if (!applications.contains(this.serverProperties.getDefaultApplicationName())) {
			applications.add(this.serverProperties.getDefaultApplicationName());
		}

		for (String currentLabel : labels) {
			Set<String> loadedKeys = new LinkedHashSet<>();
			Map<String, Optional<Blob>> resolvedBlobs = new HashMap<>();

			/*
			 * Load profile-specific files first. Pass the complete active profile list
			 * separately so YAML documents in base files can match any active profile.
			 */
			for (String currentProfile : profiles) {
				for (String currentApplication : applications) {
					addPropertySources(environment, currentApplication, currentProfile, currentLabel, resolvedProfile,
							loadedKeys, resolvedBlobs);
				}
			}

			/*
			 * Match S3 search-path behavior: when custom search paths are configured,
			 * don't perform a separate unprofiled lookup for an active profile. When
			 * there are no active profiles, unprofiled configuration can still load.
			 */
			if (profiles.length == 0 || this.properties.getSearchPaths().isEmpty()) {
				for (String currentApplication : applications) {
					addPropertySources(environment, currentApplication, null, currentLabel, resolvedProfile, loadedKeys,
							resolvedBlobs);
				}
			}
		}

		return environment;
	}

	private void validateApplication(String application) {
		if (!StringUtils.hasText(application) || application.contains("*") || application.contains("?")
				|| application.contains("/") || application.contains("\\") || application.contains("..")
				|| application.contains("#") || application.contains("%")) {
			throw new IllegalArgumentException("Invalid application name");
		}
	}

	private void validateProfiles(String profile) {
		if (profile != null && !profile.isEmpty() && PathUtils.isInvalidProfiles(profile)) {
			throw new IllegalArgumentException("Invalid profile");
		}
	}

	private void validateLabel(String label) {
		if (label != null
				&& (!StringUtils.hasText(label) || label.contains("/") || label.contains("\\") || label.contains("..")
						|| label.contains("*") || label.contains("?") || label.contains("#") || label.contains("%"))) {
			throw new IllegalArgumentException("Invalid label");
		}
	}

	private void addPropertySources(Environment environment, String application, String profile, String label,
			String activeProfiles, Set<String> loadedKeys, Map<String, Optional<Blob>> resolvedBlobs) {
		for (String key : resolveKeys(application, profile, label, resolvedBlobs)) {
			if (!loadedKeys.add(key)) {
				continue;
			}

			try {
				Blob blob = resolvedBlobs
					.computeIfAbsent(key,
							k -> Optional.ofNullable(this.storage.get(BlobId.of(this.properties.getBucket(), k))))
					.orElse(null);

				if (blob == null) {
					continue;
				}

				byte[] content = blob.getContent();
				if (content == null) {
					continue;
				}

				Properties config = readProperties(key, content, activeProfiles);
				if (config.isEmpty()) {
					continue;
				}

				Properties overrides = new Properties();
				overrides.putAll(config);
				overrides.putAll(this.serverProperties.getOverrides());

				String sourceName = "gcs:" + key;

				boolean exists = environment.getPropertySources()
					.stream()
					.anyMatch(source -> source.getName().equals(sourceName) && source.getSource().equals(overrides));

				if (exists) {
					continue;
				}

				environment.setVersion(blob.getGeneration() == null ? null : blob.getGeneration().toString());
				environment.add(new PropertySource(sourceName, overrides));
			}
			catch (Exception ex) {
				LOG.warn("Could not read configuration from Google Cloud Storage object " + key, ex);
			}
		}
	}

	private List<String> resolveKeys(String application, String profile, String label,
			Map<String, Optional<Blob>> resolvedBlobs) {
		List<String> keys = new ArrayList<>();
		Set<String> seenKeys = new LinkedHashSet<>();

		if (this.properties.getSearchPaths().isEmpty()) {
			String applicationPrefix = this.properties.isUseDirectoryLayout() ? application + "/application"
					: application;
			String prefix = StringUtils.hasText(label) ? label + "/" + applicationPrefix : applicationPrefix;

			addKeys(keys, seenKeys, prefix, profile);
			return keys;
		}

		for (String template : this.properties.getSearchPaths()) {
			String pattern = resolvePattern(template, application, profile, label);

			if (!this.pathMatcher.isPattern(pattern)) {
				boolean fileFound = probeLiteralPattern(pattern, seenKeys, keys, resolvedBlobs);
				if (!fileFound) {
					scanDirectoryPattern(pattern, seenKeys, keys);
				}
			}
			else if (pattern.endsWith(".*")) {
				probeDotWildcardPattern(pattern, seenKeys, keys);
			}
			else {
				scanWildcardPattern(pattern, seenKeys, keys);
			}
		}

		return keys;
	}

	private String resolvePattern(String template, String application, String profile, String label) {
		String pattern = template.replace("{application}", application).replace("{label}", label == null ? "" : label);

		if (profile == null) {
			pattern = pattern.replace("-{profile}", "").replace("{profile}", "");
		}
		else {
			pattern = pattern.replace("{profile}", profile);
		}

		return StringUtils.trimLeadingCharacter(pattern.replaceAll("/{2,}", "/"), '/');
	}

	private void addKeys(List<String> keys, Set<String> seenKeys, String base, String profile) {
		String prefix = base;

		if (StringUtils.hasText(profile)) {
			prefix += "-" + profile;
		}

		addCandidateKeys(prefix, seenKeys, keys);
	}

	private void addCandidateKeys(String pattern, Set<String> seenKeys, List<String> keys) {
		List<String> extensions = hasSupportedExtension(pattern) ? EMPTY_EXTENSION : SUPPORTED_EXTENSIONS;

		for (String extension : extensions) {
			String key = pattern + extension;
			if (seenKeys.add(key)) {
				keys.add(key);
			}
		}
	}

	private boolean probeLiteralPattern(String pattern, Set<String> seenKeys, List<String> keys,
			Map<String, Optional<Blob>> resolvedBlobs) {
		List<String> candidates = new ArrayList<>();
		addCandidateKeys(pattern, new LinkedHashSet<>(), candidates);

		boolean fileFound = false;
		for (String key : candidates) {
			Optional<Blob> blob = resolvedBlobs.computeIfAbsent(key,
					k -> Optional.ofNullable(this.storage.get(BlobId.of(this.properties.getBucket(), k))));

			if (blob.isPresent()) {
				if (seenKeys.add(key)) {
					keys.add(key);
				}
				fileFound = true;
			}
		}

		return fileFound;
	}

	private void probeDotWildcardPattern(String pattern, Set<String> seenKeys, List<String> keys) {
		String base = pattern.substring(0, pattern.length() - 2);
		addCandidateKeys(base, seenKeys, keys);
	}

	private void scanDirectoryPattern(String pattern, Set<String> seenKeys, List<String> keys) {
		String prefix = pattern.endsWith("/") ? pattern : pattern + "/";

		scanBlobs(prefix, blob -> {
			String key = blob.getName();
			if (hasSupportedExtension(key) && seenKeys.add(key)) {
				keys.add(key);
			}
		});
	}

	private void scanWildcardPattern(String pattern, Set<String> seenKeys, List<String> keys) {
		String prefix = extractPrefix(pattern);

		scanBlobs(prefix, blob -> {
			String key = blob.getName();
			if (this.pathMatcher.match(pattern, key) && hasSupportedExtension(key) && seenKeys.add(key)) {
				keys.add(key);
			}
		});
	}

	private void scanBlobs(String prefix, Consumer<Blob> blobConsumer) {
		Page<Blob> page = this.storage.list(this.properties.getBucket(), Storage.BlobListOption.prefix(prefix));

		if (page == null) {
			return;
		}

		for (Blob blob : page.iterateAll()) {
			if (blob != null && blob.getName() != null) {
				blobConsumer.accept(blob);
			}
		}
	}

	private String extractPrefix(String pattern) {
		int starIndex = pattern.indexOf('*');
		int questionIndex = pattern.indexOf('?');
		int wildcardIndex;

		if (starIndex != -1 && questionIndex != -1) {
			wildcardIndex = Math.min(starIndex, questionIndex);
		}
		else {
			wildcardIndex = Math.max(starIndex, questionIndex);
		}

		if (wildcardIndex <= 0) {
			return "";
		}

		int slash = pattern.lastIndexOf('/', wildcardIndex);
		return slash == -1 ? "" : pattern.substring(0, slash + 1);
	}

	private boolean hasSupportedExtension(String key) {
		return SUPPORTED_EXTENSIONS.stream().anyMatch(key::endsWith);
	}

	private Properties readProperties(String key, byte[] content, String profile) throws IOException {
		String extension = key.substring(key.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);

		if ("properties".equals(extension)) {
			Properties result = new Properties();
			try (InputStream input = new ByteArrayInputStream(content)) {
				result.load(input);
			}
			return result;
		}

		YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
		try (InputStream input = new ByteArrayInputStream(content)) {
			yaml.setResources(new InputStreamResource(input));
			yaml.setDocumentMatchers(properties -> {
				Object value = properties.get("spring.config.activate.on-profile");
				if (value == null) {
					value = properties.get("spring.config.activate.onProfile");
				}

				if (value == null) {
					return YamlProcessor.MatchStatus.FOUND;
				}

				if (!StringUtils.hasText(profile)) {
					return YamlProcessor.MatchStatus.NOT_FOUND;
				}

				String expression = value.toString().trim();
				List<String> activeProfiles = Arrays.asList(StringUtils.commaDelimitedListToStringArray(profile));
				boolean matches = Profiles.of(expression).matches(activeProfiles::contains);

				return matches ? YamlProcessor.MatchStatus.FOUND : YamlProcessor.MatchStatus.NOT_FOUND;
			});

			Properties result = yaml.getObject();
			return result == null ? new Properties() : result;
		}
	}

}
