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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.cloud.parametermanager.v1.LocationName;
import com.google.cloud.parametermanager.v1.Parameter;
import com.google.cloud.parametermanager.v1.ParameterManagerClient;
import com.google.cloud.parametermanager.v1.RenderParameterVersionResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;

/**
 * @author Yash Chauhan
 */
public class GoogleParameterManagerEnvironmentRepository implements EnvironmentRepository, Ordered, AutoCloseable {

	private static final Log log = LogFactory.getLog(GoogleParameterManagerEnvironmentRepository.class);

	private final ConfigServerProperties configServerProperties;

	private final ParameterManagerClient parameterManagerClient;

	private final GoogleParameterManagerEnvironmentProperties properties;

	private final int order;

	public GoogleParameterManagerEnvironmentRepository(ParameterManagerClient parameterManagerClient,
			GoogleParameterManagerEnvironmentProperties properties, ConfigServerProperties configServerProperties) {

		this.parameterManagerClient = parameterManagerClient;
		this.properties = properties;
		this.configServerProperties = configServerProperties;
		this.order = properties.getOrder();
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		if (!StringUtils.hasText(label)) {
			label = this.properties.getDefaultLabel();
		}

		String defaultProfile = this.configServerProperties.getDefaultProfile();

		if (!StringUtils.hasText(profile)) {
			profile = defaultProfile;
		}

		String[] profiles = StringUtils.trimArrayElements(StringUtils.commaDelimitedListToStringArray(profile));

		if (!Arrays.asList(profiles).contains(defaultProfile)) {
			profile = defaultProfile + "," + profile;
			profiles = StringUtils.trimArrayElements(StringUtils.commaDelimitedListToStringArray(profile));
		}

		String applications = application;
		if (!"application".equals(application)) {
			applications = "application," + application;
		}

		String[] applicationNames = StringUtils
			.trimArrayElements(StringUtils.commaDelimitedListToStringArray(applications));

		Environment result = new Environment(application, profiles, label, null, null);

		try {
			String projectId = this.properties.getProjectId();
			if (!StringUtils.hasText(projectId)) {
				throw new IllegalStateException("Google Cloud project ID must be configured");
			}

			LocationName locationName = LocationName.of(projectId, this.properties.getLocation());

			List<Parameter> parameterList = new ArrayList<>();
			this.parameterManagerClient.listParameters(locationName).iterateAll().forEach(parameterList::add);

			for (String applicationName : applicationNames) {
				for (String profileUnit : profiles) {
					try {
						Map<String, String> parameters = getParameters(parameterList, applicationName, profileUnit,
								projectId);

						if (!parameters.isEmpty()) {
							result.add(new PropertySource("gpm:" + applicationName + "-" + profileUnit, parameters));
						}
					}
					catch (Exception ex) {
						log.warn("Could not retrieve parameters for application " + applicationName + " and profile "
								+ profileUnit + ", continuing with the remaining combinations", ex);
					}
				}
			}
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not access Google Cloud Parameter Manager", ex);
		}
		return result;
	}

	private Map<String, String> getParameters(Iterable<Parameter> parameterList, String application, String profile,
			String projectId) {

		Map<String, String> result = new HashMap<>();

		for (Parameter parameter : parameterList) {
			if (parameter.getLabelsOrDefault(this.properties.getApplicationLabel(), "application")
				.equalsIgnoreCase(application)
					&& parameter.getLabelsOrDefault(this.properties.getProfileLabel(), "profile")
						.equalsIgnoreCase(profile)) {

				RenderParameterVersionResponse response = this.parameterManagerClient
					.renderParameterVersion(parameter.getName() + "/versions/latest");

				String parameterName = parameter.getName();
				String prefix = "projects/" + projectId + "/locations/" + this.properties.getLocation()
						+ "/parameters/";

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
		return this.order;
	}

	@Override
	public void close() {
		this.parameterManagerClient.close();
	}

}
