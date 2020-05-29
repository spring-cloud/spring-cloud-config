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

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.amazonaws.services.secretsmanager.model.ResourceNotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.config.server.environment.AwsSecretsManagerEnvironmentProperties.DEFAULT_PATH_SEPARATOR;

public class AwsSecretsManagerEnvironmentRepository implements EnvironmentRepository{

	private static final Log log = LogFactory.getLog(AwsSecretsManagerEnvironmentRepository.class);

	private final ObjectMapper objectMapper;

	private final AWSSecretsManager awsSmClient;

	private final ConfigServerProperties configServerProperties;

	private final AwsSecretsManagerEnvironmentProperties environmentProperties;

	public AwsSecretsManagerEnvironmentRepository(AWSSecretsManager awsSmClient,
				ConfigServerProperties configServerProperties,
				AwsSecretsManagerEnvironmentProperties environmentProperties) {
		this.awsSmClient = awsSmClient;
		this.configServerProperties = configServerProperties;
		this.environmentProperties = environmentProperties;
		this.objectMapper = new ObjectMapper();
	}


	@Override
	public Environment findOne(String application, String profileList, String label) {
		final String defaultApplication = configServerProperties.getDefaultApplicationName();
		final String defaultProfile = configServerProperties.getDefaultProfile();

		if(StringUtils.isEmpty(application)) {
			application = defaultApplication;
		}

		if(StringUtils.isEmpty(profileList)) {
			profileList = defaultProfile;
		}

		String[] profiles = StringUtils.commaDelimitedListToStringArray(profileList);
		Environment environment = new Environment(application, profiles, label, null, null);

		Map<String, String> overrides = configServerProperties.getOverrides();
		if(!overrides.isEmpty()) {
			environment.add(new PropertySource("overrides", overrides));
		}

		for(String profile: profiles) {
			addPropertySource(environment, application, profile);
			if(!defaultApplication.equals(application)) {
				addPropertySource(environment, defaultApplication, profile);
			}
		}

		if(!Arrays.asList(profiles).contains(defaultProfile)) {
			addPropertySource(environment, application, defaultProfile);
		}

		if(!Arrays.asList(profiles).contains(defaultProfile) && !defaultApplication.equals(application)) {
			addPropertySource(environment, defaultApplication, defaultProfile);
		}

		if(!defaultApplication.equals(application)) {
			addPropertySource(environment, application, null);
		}

		addPropertySource(environment, defaultApplication, null);

		return environment;
	}

	private void addPropertySource(Environment environment, String application, String profile) {
		String path = buildPath(application, profile);

		Map<Object, Object> properties = findProperties(path);
		if(!properties.isEmpty()) {
			environment.add(new PropertySource(environmentProperties.getOrigin() + path, properties));
		}
	}

	private String buildPath(String application, String profile) {
		String prefix = environmentProperties.getPrefix();
		String profileSeparator = environmentProperties.getProfileSeparator();

		if(profile == null || profile.isEmpty()) {
			return prefix + DEFAULT_PATH_SEPARATOR + application + DEFAULT_PATH_SEPARATOR;
		} else {
			return prefix + DEFAULT_PATH_SEPARATOR + application + profileSeparator + profile + DEFAULT_PATH_SEPARATOR;
		}
	}

	private Map<Object, Object> findProperties(String path) {
		Map<Object, Object> properties = new HashMap<>();

		GetSecretValueRequest request = new GetSecretValueRequest().withSecretId(path);
		try {
			GetSecretValueResult response = awsSmClient.getSecretValue(request);

			if(response != null) {
				Map<String, Object> secretMap = objectMapper.readValue(
					response.getSecretString(), new TypeReference<Map<String, Object>>() {});

				for(Map.Entry<String, Object> secretEntry : secretMap.entrySet()) {
					properties.put(secretEntry.getKey(), secretEntry.getValue());
				}
			}
		} catch (ResourceNotFoundException | IOException e) {
			log.warn("Unable to load secrets from AWS Secrets Manager for " + path, e);
			throw new RuntimeException(e);
		}

		return properties;
	}

}
