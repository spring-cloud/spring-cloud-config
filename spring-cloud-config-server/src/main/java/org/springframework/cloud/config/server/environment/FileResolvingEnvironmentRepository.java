/*
 * Copyright 2024-2026 the original author or authors.
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
import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.ResourceUtils;

/**
 * @author Johny Cho
 */
public class FileResolvingEnvironmentRepository implements EnvironmentRepository {

	private static final Log log = LogFactory.getLog(FileResolvingEnvironmentRepository.class);
	private final EnvironmentRepository delegate;
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

		List<PropertySource> sources = env.getPropertySources();

		for (int i = 0; i < sources.size(); i++) {
			PropertySource source = sources.get(i);
			Map<?, ?> originalMap = source.getSource();

			Map<Object, Object> modifiedMap = new LinkedHashMap<>(originalMap);
			boolean modified = false;

			for (Map.Entry<?, ?> entry : originalMap.entrySet()) {
				Object value = entry.getValue();

				if (value instanceof String str && str.startsWith(PREFIX)) {
					String filePath = str.substring(PREFIX.length());
					try {
						String base64Content = readFileToBase64(filePath);
						modifiedMap.put(entry.getKey(), base64Content);
						modified = true;
					}
					catch (IOException e) {
						log.warn(String.format("Failed to resolve file content for property '%s'. path: %s", entry.getKey(), filePath), e);
					}
				}
			}

			if (modified) {
				PropertySource newSource = new PropertySource(source.getName(), modifiedMap);
				sources.set(i, newSource);
			}
		}

		return env;
	}

	private String readFileToBase64(String filePath) throws IOException {
		File file = ResourceUtils.getFile(filePath);
		byte[] fileContent = FileCopyUtils.copyToByteArray(file);
		return Base64.getEncoder().encodeToString(fileContent);
	}
}
