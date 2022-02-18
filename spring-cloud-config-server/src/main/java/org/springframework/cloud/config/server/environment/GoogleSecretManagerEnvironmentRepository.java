/*
 * Copyright 2013-2020 the original author or authors.
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

import com.google.cloud.secretmanager.v1.Secret;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.environment.secretmanager.GoogleConfigProvider;
import org.springframework.cloud.config.server.environment.secretmanager.GoogleSecretComparatorByVersion;
import org.springframework.cloud.config.server.environment.secretmanager.GoogleSecretManagerAccessStrategy;
import org.springframework.cloud.config.server.environment.secretmanager.GoogleSecretManagerAccessStrategyFactory;
import org.springframework.cloud.config.server.environment.secretmanager.HttpHeaderGoogleConfigProvider;
import org.springframework.core.Ordered;
import org.springframework.web.client.RestTemplate;

/**
 * @author Jose Maria Alvarez
 * @author KNV Srinivas
 */
public class GoogleSecretManagerEnvironmentRepository implements EnvironmentRepository, Ordered {

	private String applicationLabel;

	private String profileLabel;

	private GoogleSecretManagerAccessStrategy accessStrategy;

	private boolean tokenMandatory;

	private GoogleConfigProvider configProvider;

	private final int order;

	public GoogleSecretManagerEnvironmentRepository(ObjectProvider<HttpServletRequest> request, RestTemplate rest,
			GoogleSecretManagerEnvironmentProperties properties) {
		this.applicationLabel = properties.getApplicationLabel();
		this.profileLabel = properties.getProfileLabel();
		this.configProvider = new HttpHeaderGoogleConfigProvider(request);
		this.accessStrategy = GoogleSecretManagerAccessStrategyFactory.forVersion(rest, configProvider, properties);
		this.tokenMandatory = properties.getTokenMandatory();
		this.order = properties.getOrder();
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
		String[] profiles = org.springframework.util.StringUtils
				.trimArrayElements(org.springframework.util.StringUtils.commaDelimitedListToStringArray(profile));
		Environment result = new Environment(application, profile, label, null, null);
		if (tokenMandatory) {
			if (accessStrategy.checkRemotePermissions()) {
				addPropertySource(application, profiles, result);
			}
		}
		else {
			addPropertySource(application, profiles, result);
		}
		return result;
	}

	private void addPropertySource(String application, String[] profiles, Environment result) {
		for (String profileUnit : profiles) {
			Map<?, ?> secrets = getSecrets(application, profileUnit);
			if (!secrets.isEmpty()) {
				result.add(new PropertySource("gsm:" + application + "-" + profileUnit, secrets));
			}
		}
	}

	/**
	 * @param application the application name
	 * @param profile the profile name
	 * @return the properties to add into the environment
	 */
	private Map<?, ?> getSecrets(String application, String profile) {
		Map<String, String> result = new HashMap<>();
		String prefix = configProvider.getValue(HttpHeaderGoogleConfigProvider.PREFIX_HEADER, false);
		for (Secret secret : accessStrategy.getSecrets()) {
			if (secret.getLabelsOrDefault(applicationLabel, "application").equalsIgnoreCase(application)
					&& secret.getLabelsOrDefault(profileLabel, "profile").equalsIgnoreCase(profile)) {
				result.put(accessStrategy.getSecretName(secret),
						accessStrategy.getSecretValue(secret, new GoogleSecretComparatorByVersion()));
			}
			else if (StringUtils.isNotBlank(prefix) && accessStrategy.getSecretName(secret).startsWith(prefix)) {
				result.put(StringUtils.removeStart(accessStrategy.getSecretName(secret), prefix),
						accessStrategy.getSecretValue(secret, new GoogleSecretComparatorByVersion()));
			}
		}
		return result;
	}

	@Override
	public int getOrder() {
		return order;
	}

}
