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

import java.util.HashMap;
import java.util.Map;

import com.google.cloud.parametermanager.v1.LocationName;
import com.google.cloud.parametermanager.v1.Parameter;
import com.google.cloud.parametermanager.v1.ParameterManagerClient;
import com.google.cloud.parametermanager.v1.RenderParameterVersionResponse;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;

/**
 * @author Yash Chauhan
 */
public class GoogleParameterManagerEnvironmentRepository implements EnvironmentRepository, Ordered {

	private final ParameterManagerClient parameterManagerClient;

	private final GoogleParameterManagerEnvironmentProperties properties;

	private final int order;

	public GoogleParameterManagerEnvironmentRepository(ParameterManagerClient parameterManagerClient,
			GoogleParameterManagerEnvironmentProperties properties) {

		this.parameterManagerClient = parameterManagerClient;
		this.properties = properties;
		this.order = properties.getOrder();
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		if (!StringUtils.hasText(label)) {
			label = "master";
		}

		if (!StringUtils.hasText(profile)) {
			profile = "default";
		}

		if (!profile.startsWith("default")) {
			profile = "default," + profile;
		}

		String[] profiles = StringUtils.trimArrayElements(StringUtils.commaDelimitedListToStringArray(profile));

		Environment result = new Environment(application, profile, label, null, null);

		try {
			for (String profileUnit : profiles) {
				Map<String, String> parameters = getParameters(this.parameterManagerClient, application, profileUnit);
				if (!parameters.isEmpty()) {
					result.add(new PropertySource("gpm:" + application + "-" + profileUnit, parameters));
				}
			}
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not access Google Cloud Parameter Manager", ex);
		}

		return result;
	}

	private Map<String, String> getParameters(ParameterManagerClient client, String application, String profile) {
		Map<String, String> result = new HashMap<>();

		String projectId = properties.getProjectId();
		if (!StringUtils.hasText(projectId)) {
			throw new IllegalStateException("Google Cloud project ID must be configured");
		}

		LocationName locationName = LocationName.of(projectId, properties.getLocation());

		for (Parameter parameter : client.listParameters(locationName).iterateAll()) {
			if (parameter.getLabelsOrDefault(properties.getApplicationLabel(), "application")
				.equalsIgnoreCase(application)
					&& parameter.getLabelsOrDefault(properties.getProfileLabel(), "profile")
						.equalsIgnoreCase(profile)) {

				RenderParameterVersionResponse response = client
					.renderParameterVersion(parameter.getName() + "/versions/latest");

				String parameterName = parameter.getName();
				String prefix = "projects/" + projectId + "/locations/" + properties.getLocation() + "/parameters/";

				if (parameterName.startsWith(prefix)) {
					parameterName = parameterName.substring(prefix.length());
				}

				result.put(parameterName, response.getRenderedPayload().toStringUtf8());
			}
		}

		return result;
	}

	@Override
	public int getOrder() {
		return order;
	}

}
