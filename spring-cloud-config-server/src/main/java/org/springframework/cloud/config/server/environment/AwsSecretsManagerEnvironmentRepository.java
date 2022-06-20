/*
 * Copyright 2018-2020 the original author or authors.
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
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.config.server.environment.AwsSecretsManagerEnvironmentProperties.DEFAULT_PATH_SEPARATOR;

/**
 * @author Tejas Pandilwar
 * @author KNV Srinivas
 */
public class AwsSecretsManagerEnvironmentRepository implements EnvironmentRepository, Ordered {

	private static final Log log = LogFactory.getLog(AwsSecretsManagerEnvironmentRepository.class);

	private final ObjectMapper objectMapper;

	private final SecretsManagerClient awsSmClient;

	private final ConfigServerProperties configServerProperties;

	private final AwsSecretsManagerEnvironmentProperties environmentProperties;

	private final int order;

	public AwsSecretsManagerEnvironmentRepository(SecretsManagerClient awsSmClient,
			ConfigServerProperties configServerProperties,
			AwsSecretsManagerEnvironmentProperties environmentProperties) {
		this.awsSmClient = awsSmClient;
		this.configServerProperties = configServerProperties;
		this.environmentProperties = environmentProperties;
		this.order = environmentProperties.getOrder();
		this.objectMapper = new ObjectMapper();
	}

	@Override
	public Environment findOne(String application, String profileList, String label) {
		final String defaultApplication = configServerProperties.getDefaultApplicationName();
		final String defaultProfile = configServerProperties.getDefaultProfile();
		if (environmentProperties.isLabelEnabled()) {
			if (label == null) {
				label = environmentProperties.getDefaultLabel();
			}
		}

		if (StringUtils.isEmpty(application)) {
			application = defaultApplication;
		}

		if (StringUtils.isEmpty(profileList)) {
			profileList = defaultProfile;
		}

		String[] profiles = StringUtils.trimArrayElements(StringUtils.commaDelimitedListToStringArray(profileList));
		Environment environment = new Environment(application, profiles, label, null, null);

		Map<String, String> overrides = configServerProperties.getOverrides();
		if (!overrides.isEmpty()) {
			environment.add(new PropertySource("overrides", overrides));
		}

		for (String profile : profiles) {
			addPropertySource(environment, application, profile, label);
			if (!defaultApplication.equals(application)) {
				addPropertySource(environment, defaultApplication, profile, label);
			}
		}

		if (!Arrays.asList(profiles).contains(defaultProfile)) {
			addPropertySource(environment, application, defaultProfile, label);
		}

		if (!Arrays.asList(profiles).contains(defaultProfile) && !defaultApplication.equals(application)) {
			addPropertySource(environment, defaultApplication, defaultProfile, label);
		}

		if (!defaultApplication.equals(application)) {
			addPropertySource(environment, application, null, label);
		}

		addPropertySource(environment, defaultApplication, null, label);

		return environment;
	}

	private void addPropertySource(Environment environment, String application, String profile, String label) {
		String path = buildPath(application, profile, label);

		Map<Object, Object> properties = findProperties(path);
		if (!properties.isEmpty()) {
			environment.add(new PropertySource(environmentProperties.getOrigin() + path, properties));
		}
	}

	private String buildPath(String application, String profile, String label) {
		String prefix = environmentProperties.getPrefix();
		String profileSeparator = environmentProperties.getProfileSeparator();

		StringBuilder path = new StringBuilder().append(prefix).append(DEFAULT_PATH_SEPARATOR).append(application);

		if (StringUtils.hasText(profile)) {
			path.append(profileSeparator).append(profile);
		}

		path.append(DEFAULT_PATH_SEPARATOR);

		if (environmentProperties.isLabelEnabled() && StringUtils.hasText(label)) {
			path.append(label).append(DEFAULT_PATH_SEPARATOR);
		}

		return path.toString();
	}

	private Map<Object, Object> findProperties(String path) {
		Map<Object, Object> properties = new HashMap<>();

		GetSecretValueRequest request = GetSecretValueRequest.builder().secretId(path).build();
		try {
			GetSecretValueResponse response = awsSmClient.getSecretValue(request);

			if (response != null) {
				Map<String, Object> secretMap = objectMapper.readValue(response.secretString(),
						new TypeReference<Map<String, Object>>() {
						});

				for (Map.Entry<String, Object> secretEntry : secretMap.entrySet()) {
					properties.put(secretEntry.getKey(), secretEntry.getValue());
				}
			}
		}
		catch (ResourceNotFoundException | IOException e) {
			log.debug(String.format(
					"Skip adding propertySource. Unable to load secrets from AWS Secrets Manager for secretId=%s",
					path), e);
		}

		return properties;
	}

	@Override
	public int getOrder() {
		return order;
	}

}
