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
import java.util.Map;
import java.util.stream.Collectors;

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

		String[] applications = deDuplicateAndAddDefault(application, DEFAULT_APPLICATION);
		String[] profiles = deDuplicateAndAddDefault(profile, DEFAULT_PROFILE);

		Environment environment = new Environment(application, profiles, label, null, null);
		for (String prof : profiles) {
			for (String app : applications) {
				addPropertySource(environment, app, prof, label);
			}
		}

		return environment;
	}

	/**
	 * Converts the comma delimited items to a List and then: - Removes duplicates and
	 * keeps unique items only. - Moves or Adds the given default item to the end of List.
	 */
	private String[] deDuplicateAndAddDefault(String commaDelimitedItems, String defaultItem) {
		var items = Arrays.stream(StringUtils.commaDelimitedListToStringArray(commaDelimitedItems))
			.distinct()
			.filter(item -> !defaultItem.equals(item))
			.collect(Collectors.toList());

		items.add(defaultItem);
		return items.toArray(new String[0]);
	}

	private void addPropertySource(Environment environment, String application, String profile, String label) {
		Map<Object, Object> properties = findProperties(application, profile, label);
		// The main PropertySource (the first one) should be always there, even if it is empty.
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

	@Override
	public int getOrder() {
		return order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

}
