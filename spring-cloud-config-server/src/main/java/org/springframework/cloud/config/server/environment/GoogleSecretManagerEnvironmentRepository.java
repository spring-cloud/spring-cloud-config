/*
 * Copyright 2013-2019 the original author or authors.
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

import javax.servlet.http.HttpServletRequest;

import com.google.cloud.secretmanager.v1.Secret;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.environment.secretManager.GoogleSecretComparatorByVersion;
import org.springframework.cloud.config.server.environment.secretManager.GoogleSecretManagerAccessStrategy;
import org.springframework.cloud.config.server.environment.secretManager.GoogleSecretManagerAccessStrategyFactory;
import org.springframework.cloud.config.server.environment.secretManager.HttpHeaderGoogleConfigProvider;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * @author Jose Maria Alvarez
 */
public class GoogleSecretManagerEnvironmentRepository implements EnvironmentRepository {

	private String applicationLabel;

	private String profileLabel;

	private GoogleSecretManagerAccessStrategy accessStrategy;

	private static Log logger = LogFactory
		.getLog(GoogleSecretManagerEnvironmentRepository.class);

	public GoogleSecretManagerEnvironmentRepository(
		ObjectProvider<HttpServletRequest> request, RestTemplate rest,
		GoogleSecretManagerEnvironmentProperties properties) {
		this.applicationLabel = properties.getApplicationLabel();
		this.profileLabel = properties.getProfileLabel();
		this.accessStrategy = GoogleSecretManagerAccessStrategyFactory
			.forVersion(rest, new HttpHeaderGoogleConfigProvider(request), properties.getVersion());
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		if (StringUtils.isEmpty(label)) {
			label = "master";
		}
		if (StringUtils.isEmpty(profile)) {
			profile = "default";
		}
		if (!profile.startsWith("default")) {
			profile = "default," + profile;
		}
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		Environment result = new Environment(application, profile, label, null, null);
		for (String profileUnit : profiles) {
			Map<?, ?> secrets = getSecrets(application, profileUnit);
			if (!secrets.isEmpty()) {
				result.add(new PropertySource("gsm:" + application + "-" + profileUnit,
					secrets));
			}
		}
		return result;
	}

	/**
	 * @param application the application name
	 * @param profile the profile name
	 * @return the properties to add into the environment
	 */
	private Map<?, ?> getSecrets(String application, String profile) {
		Map<String, String> result = new HashMap<>();
		for (Secret secret : accessStrategy.getSecrets()) {
			if (secret.getLabelsOrDefault(applicationLabel, "application")
				.equalsIgnoreCase(application)
				&& secret.getLabelsOrDefault(profileLabel, "profile")
				.equalsIgnoreCase(profile)) {
				result.put(accessStrategy.getSecretName(secret), accessStrategy
					.getSecretValue(secret, new GoogleSecretComparatorByVersion()));
			}

		}
		return result;
	}
}
