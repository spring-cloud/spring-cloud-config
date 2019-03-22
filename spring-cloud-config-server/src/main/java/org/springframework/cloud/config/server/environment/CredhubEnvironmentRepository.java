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

import java.util.HashMap;
import java.util.Map;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.credhub.core.CredHubOperations;
import org.springframework.credhub.support.CredentialDetails;
import org.springframework.credhub.support.SimpleCredentialName;
import org.springframework.credhub.support.json.JsonCredential;
import org.springframework.util.StringUtils;

import static java.util.stream.Collectors.toMap;

/**
 * @author Alberto C. Ríos
 */
public class CredhubEnvironmentRepository implements EnvironmentRepository {

	private CredHubOperations credHubOperations;

	public CredhubEnvironmentRepository(CredHubOperations credHubOperations) {
		this.credHubOperations = credHubOperations;
	}

	@Override
	public Environment findOne(String application, String profilesList, String label) {
		if (StringUtils.isEmpty(profilesList)) {
			profilesList = "default";
		}
		if (StringUtils.isEmpty(label)) {
			label = "master";
		}

		String[] profiles = StringUtils.commaDelimitedListToStringArray(profilesList);

		Environment environment = new Environment(application, profiles, label, null,
				null);
		Map<Object, Object> properties = new HashMap<>();
		for (String profile : profiles) {
			properties.putAll(findProperties(application, profile, label));
		}
		environment.add(new PropertySource("credhub-" + application, properties));

		return environment;
	}

	private Map<Object, Object> findProperties(String application, String profile,
			String label) {
		String path = "/" + application + "/" + profile + "/" + label;

		return this.credHubOperations.credentials().findByPath(path).stream()
				.map(credentialSummary -> credentialSummary.getName().getName())
				.map(name -> this.credHubOperations.credentials()
						.getByName(new SimpleCredentialName(name), JsonCredential.class))
				.map(CredentialDetails::getValue)
				.flatMap(jsonCredential -> jsonCredential.entrySet().stream())
				.collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b));
	}

}
