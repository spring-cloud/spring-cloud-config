/*
 * Copyright 2018-2019 the original author or authors.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.Ordered;
import org.springframework.credhub.core.CredHubOperations;
import org.springframework.credhub.support.CredentialDetails;
import org.springframework.credhub.support.SimpleCredentialName;
import org.springframework.credhub.support.json.JsonCredential;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import static java.util.stream.Collectors.toMap;

/**
 * @author Alberto C. Ríos
 * @author KNV Srinivas
 */
public class CredhubEnvironmentRepository implements EnvironmentRepository, Ordered {

	private static final String DEFAULT_PROFILE = "default";

	private static final String DEFAULT_LABEL = "master";

	private static final String DEFAULT_APPLICATION = "application";

	private int order = Ordered.LOWEST_PRECEDENCE;

	private final CredHubOperations credHubOperations;

	public CredhubEnvironmentRepository(CredHubOperations credHubOperations) {
		this.credHubOperations = credHubOperations;
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		if (ObjectUtils.isEmpty(profile)) {
			profile = DEFAULT_PROFILE;
		}
		if (ObjectUtils.isEmpty(label)) {
			label = DEFAULT_LABEL;
		}

		List<String> applications = normalize(application, DEFAULT_APPLICATION);
		List<String> profiles = normalize(profile, DEFAULT_PROFILE);

		Environment environment = new Environment(application, split(profile), label, null, null);
		for (String prof : profiles) {
			for (String app : applications) {
				addPropertySource(environment, app, prof, label);
			}
		}

		return environment;
	}

	/**
	 * Splits the comma delimited items and returns the reversed distinct items with given
	 * default item at the end.
	 */
	private List<String> normalize(String commaDelimitedItems, String defaultItem) {
		var items = Stream.concat(Stream.of(defaultItem), Arrays.stream(split(commaDelimitedItems)))
			.distinct()
			.collect(Collectors.toList());

		Collections.reverse(items);
		return items;
	}

	private void addPropertySource(Environment environment, String application, String profile, String label) {
		Map<Object, Object> properties = findProperties(application, profile, label);
		// The main PropertySource (the first one) should be always there, even if it is
		// empty.
		if (!properties.isEmpty() || environment.getPropertySources().isEmpty()) {
			PropertySource propertySource = new PropertySource("credhub-" + application + "-" + profile + "-" + label,
					properties);
			environment.add(propertySource);
		}
	}

	private Map<Object, Object> findProperties(String application, String profile, String label) {
		String path = "/" + application + "/" + profile + "/" + label;

		return this.credHubOperations.credentials()
			.findByPath(path)
			.stream()
			.map(credentialSummary -> credentialSummary.getName().getName())
			.map(name -> this.credHubOperations.credentials()
				.getByName(new SimpleCredentialName(name), JsonCredential.class))
			.map(CredentialDetails::getValue)
			.flatMap(jsonCredential -> jsonCredential.entrySet().stream())
			.collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b));
	}

	private String[] split(String str) {
		return StringUtils.commaDelimitedListToStringArray(str);
	}

	@Override
	public int getOrder() {
		return order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

}
