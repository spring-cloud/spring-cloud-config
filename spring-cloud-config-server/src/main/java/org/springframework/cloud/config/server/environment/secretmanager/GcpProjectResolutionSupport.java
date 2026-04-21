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

package org.springframework.cloud.config.server.environment.secretmanager;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.config.server.environment.GoogleSecretManagerEnvironmentProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * Resolves which Google Cloud project to use for GSM. When {@code token-mandatory} is
 * {@code false}, client-supplied {@code X-Project-ID} is checked against
 * {@code allowed-project-ids}. When {@code token-mandatory} is {@code true}, that
 * allow-list is not applied for the header (authorization uses
 * {@link GoogleSecretManagerV1AccessStrategy#checkRemotePermissions} instead).
 */
public final class GcpProjectResolutionSupport {

	private static final Log logger = LogFactory.getLog(GcpProjectResolutionSupport.class);

	private final GoogleSecretManagerEnvironmentProperties properties;

	public GcpProjectResolutionSupport(GoogleSecretManagerEnvironmentProperties properties) {
		this.properties = properties;
	}

	/**
	 * Resolves the project for the current request. Returns {@code null} if no project
	 * can be determined or (when {@code tokenMandatory} is {@code false}) the
	 * client-supplied project is not allow-listed.
	 */
	public String resolve(GoogleConfigProvider configProvider, RestTemplate rest) {
		String headerValue = null;
		try {
			headerValue = configProvider.getValue(HttpHeaderGoogleConfigProvider.PROJECT_ID_HEADER, false);
		}
		catch (IllegalStateException ex) {
			logger.debug("No HttpServletRequest; cannot resolve GCP project for Secret Manager");
			return null;
		}
		if (StringUtils.hasText(headerValue)) {
			String projectId = headerValue.trim();
			if (properties.getTokenMandatory() || isClientProjectAllowed(projectId, properties)) {
				return projectId;
			}
			logger.warn("Rejecting Secret Manager access for disallowed X-Project-ID: " + projectId);
			return null;
		}
		String metadataProject = fetchProjectFromMetadata(rest);
		if (StringUtils.hasText(metadataProject)) {
			return metadataProject.trim();
		}
		if (StringUtils.hasText(properties.getProjectId())) {
			return properties.getProjectId().trim();
		}
		return null;
	}

	static boolean isClientProjectAllowed(String projectId, GoogleSecretManagerEnvironmentProperties properties) {
		List<String> allowed = properties.getAllowedProjectIds();
		if (allowed == null || allowed.isEmpty()) {
			return false;
		}
		for (String candidate : allowed) {
			if (candidate != null && projectId.equals(candidate.trim())) {
				return true;
			}
		}
		return false;
	}

	private static String fetchProjectFromMetadata(RestTemplate rest) {
		try {
			HttpEntity<String> entity = new HttpEntity<>("parameters", getMetadataHttpHeaders());
			return rest
				.exchange(GoogleSecretManagerEnvironmentProperties.GOOGLE_METADATA_PROJECT_URL, HttpMethod.GET, entity,
						String.class)
				.getBody();
		}
		catch (Exception ex) {
			logger.debug("Could not read project id from metadata server: " + ex.getMessage());
			return null;
		}
	}

	private static HttpHeaders getMetadataHttpHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Metadata-Flavor", "Google");
		return headers;
	}

}
