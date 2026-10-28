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

package org.springframework.cloud.config.server.environment.compliance;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.util.Assert;

/**
 * A configuration file to store in a backend, for example {@code orders-dev.yml} for
 * label {@code v1}. Backends that are not file based map the application, profile and
 * label onto their own keys and use {@link #properties()}.
 *
 * @param application the application name, for example {@code orders} or
 * {@code application}
 * @param profile the profile, or {@code null} for configuration without a profile
 * @param label the label, or {@code null} for the backend's default label
 * @param format the file format
 * @param documents the documents in the file
 * @author Ryan Baxter
 */
public record ConfigFile(String application, String profile, String label, Format format,
		List<ConfigDocument> documents) {

	/**
	 * The name of the file without extension, for example {@code orders-dev}.
	 * @return the base name
	 */
	public String baseName() {
		return this.profile == null ? this.application : this.application + "-" + this.profile;
	}

	/**
	 * The name of the file, for example {@code orders-dev.yml}.
	 * @return the file name
	 */
	public String fileName() {
		return baseName() + this.format.extension();
	}

	/**
	 * The path of the file relative to the root of the backend, prefixed with the label
	 * if there is one, for example {@code v1/orders-dev.yml}.
	 * @return the path
	 */
	public String path() {
		return this.label == null ? fileName() : this.label + "/" + fileName();
	}

	/**
	 * Whether the file has a single document that is always active, so it can be stored
	 * in backends that hold flat key/value pairs.
	 * @return {@code true} if the file is a single document without activation
	 */
	public boolean isSingleDocument() {
		return this.documents.size() == 1 && this.documents.get(0).activateOnProfile() == null;
	}

	/**
	 * The properties of a single-document file.
	 * @return the properties
	 */
	public Map<String, Object> properties() {
		Assert.state(isSingleDocument(), () -> fileName() + " has more than one document");
		return this.documents.get(0).properties();
	}

	/**
	 * Render the file in its {@link #format()}.
	 * @return the file content
	 */
	public String content() {
		if (this.format == Format.PROPERTIES) {
			return properties().entrySet()
				.stream()
				.map(entry -> entry.getKey() + "=" + entry.getValue())
				.collect(Collectors.joining("\n", "", "\n"));
		}
		return this.documents.stream().map(ConfigFile::yaml).collect(Collectors.joining("---\n"));
	}

	private static String yaml(ConfigDocument document) {
		StringBuilder yaml = new StringBuilder();
		if (document.activateOnProfile() != null) {
			yaml.append("spring.config.activate.on-profile: \"").append(document.activateOnProfile()).append("\"\n");
		}
		document.properties().forEach((key, value) -> yaml.append(key).append(": \"").append(value).append("\"\n"));
		return yaml.toString();
	}

	/**
	 * Configuration file formats.
	 */
	public enum Format {

		/**
		 * A {@code .properties} file.
		 */
		PROPERTIES(".properties"),

		/**
		 * A {@code .yml} file.
		 */
		YAML(".yml");

		private final String extension;

		Format(String extension) {
			this.extension = extension;
		}

		public String extension() {
			return this.extension;
		}

	}

}
