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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import org.springframework.util.ReflectionUtils;
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
	public Environment findOne(String application, String profiles, String label) {
		if(StringUtils.isEmpty(application)) {
			application = configServerProperties.getDefaultApplicationName();
		}

		if(StringUtils.isEmpty(profiles)) {
			profiles = configServerProperties.getDefaultProfile();
		}

		String[] profileArray = StringUtils.commaDelimitedListToStringArray(profiles);
		Environment environment = new Environment(application, profiles, label, null, null);
		List<PropertySource> propertySources = new ArrayList<>();

		for(String profile: profileArray) {
			PropertySource source = getSecretPropertySource(application, profile, label);
			propertySources.add(source);
		}

		Map<String, String> overrides = configServerProperties.getOverrides();
		if(!overrides.isEmpty()) {
			propertySources.add(0, new PropertySource("overrides", overrides));
		}

		environment.addAll(propertySources);
		return environment;
	}

	private PropertySource getSecretPropertySource(String application, String profile, String label) {
		String path = buildSecretPath(application, profile);

		try {
			Map<String, Object> source = getPropertiesBySecretPath(path);

			if(!source.isEmpty()) {
				String propertySourceName =  environmentProperties.getOrigin() + path;
				return new PropertySource(propertySourceName, source);
			}
		}
		catch (Exception e) {
			if(environmentProperties.isFailFast()) {
				log.error("FailFast is set to True. Error encountered while retrieving configuration from AWS Secrets Manager:\n"
					+ e.getMessage());
				ReflectionUtils.rethrowRuntimeException(e);
			} else {
				log.warn("Unable to load configuration from AWS Secrets Manager for " + path, e);
			}
		}
		return null; //TODO: change this!!!
	}

	private String buildSecretPath(String application, String profile) {
		String prefix = environmentProperties.getPrefix();
		String profileSeparator = environmentProperties.getProfileSeparator();

		return prefix + DEFAULT_PATH_SEPARATOR + application + profileSeparator + profile + DEFAULT_PATH_SEPARATOR;
	}

	private Map<String, Object> getPropertiesBySecretPath(String path) throws Exception {
		Map<String, Object> properties = new HashMap();

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
		} catch (ResourceNotFoundException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return properties;
	}

}
