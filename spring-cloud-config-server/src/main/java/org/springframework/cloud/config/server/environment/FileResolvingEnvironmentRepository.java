/*
 * Copyright 2026-present the original author or authors.
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

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.FileCopyUtils;

/**
 * @author Johny Cho
 */
public class FileResolvingEnvironmentRepository implements EnvironmentRepository, SearchPathLocator {

	private static final Log log = LogFactory.getLog(FileResolvingEnvironmentRepository.class);

	private final EnvironmentRepository delegate;

	private final ResourceLoader resourceLoader = new DefaultResourceLoader();

	private static final String PREFIX = "{file}";

	public FileResolvingEnvironmentRepository(EnvironmentRepository delegate) {
		this.delegate = delegate;
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		Environment env = this.delegate.findOne(application, profile, label);
		if (Objects.isNull(env)) {
			return null;
		}

		Locations locations = resolveLocations(application, profile, label);
		List<PropertySource> sources = env.getPropertySources();

		for (int i = 0; i < sources.size(); i++) {
			PropertySource source = sources.get(i);
			PropertySource resolvedSource = processPropertySource(source, locations);
			if (Objects.nonNull(resolvedSource)) {
				sources.set(i, resolvedSource);
			}
		}

		return env;
	}

	@Override
	public Locations getLocations(String application, String profile, String label) {
		return resolveLocations(application, profile, label);
	}

	private Locations resolveLocations(String application, String profile, String label) {
		if (this.delegate instanceof SearchPathLocator locator) {
			return locator.getLocations(application, profile, label);
		}
		return new Locations(application, profile, label, null, new String[0]);
	}

	/**
	 * Process a single PropertySource. Returns a new PropertySource if modification occurred, otherwise null.
	 */
	private PropertySource processPropertySource(PropertySource source, Locations locations) {
		Map<?, ?> originalMap = source.getSource();
		Map<Object, Object> modifiedMap = new LinkedHashMap<>(originalMap);
		boolean modified = false;

		for (Map.Entry<?, ?> entry : originalMap.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof String str && str.startsWith(PREFIX)) {
				String path = str.substring(PREFIX.length());
				String resolvedValue = resolveFileContent(entry.getKey().toString(), path, locations);
				if (Objects.nonNull(resolvedValue)) {
					modifiedMap.put(entry.getKey(), resolvedValue);
					modified = true;
				}
			}
		}

		return modified ? new PropertySource(source.getName(), modifiedMap) : null;
	}

	private String resolveFileContent(String key, String path, Locations locations) {
		try {
			Resource resource = findResource(path, locations);
			if (Objects.nonNull(resource) && resource.isReadable()) {
				byte[] content = FileCopyUtils.copyToByteArray(resource.getInputStream());
				return Base64.getEncoder().encodeToString(content);
			}
		}
		catch (IOException e) {
			log.warn(String.format("Failed to resolve file content for '%s'. path: %s", key, path), e);
		}
		return null;
	}

	private Resource findResource(String path, Locations locations) {
		// 1. Try relative path if locations are available
		if (path.startsWith(".") && Objects.nonNull(locations) && Objects.nonNull(locations.getLocations())) {
			for (String location : locations.getLocations()) {
				String resourceLocation = location + (location.endsWith("/") ? "" : "/") + path;
				Resource candidate = this.resourceLoader.getResource(resourceLocation);
				if (candidate.exists() && candidate.isReadable()) {
					return candidate;
				}
			}
			log.warn("Could not find relative file '" + path + "' in locations: " + Arrays.toString(locations.getLocations()));
			return null;
		}

		// 2. Fallback to absolute path or standard resource loading
		Resource resource = this.resourceLoader.getResource("file:" + path);
		if (!resource.exists()) {
			resource = this.resourceLoader.getResource(path);
		}
		return resource;
	}

}
