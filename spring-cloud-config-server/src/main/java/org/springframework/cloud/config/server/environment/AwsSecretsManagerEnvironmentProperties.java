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

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.config.server.support.EnvironmentRepositoryProperties;
import org.springframework.core.Ordered;

/**
 * @author Tejas Pandilwar
 */
@ConfigurationProperties("spring.cloud.config.server.aws-secretsmanager")
public class AwsSecretsManagerEnvironmentProperties implements EnvironmentRepositoryProperties {

	static final String DEFAULT_PATH_SEPARATOR = "/";

	private static final String DEFAULT_PREFIX = DEFAULT_PATH_SEPARATOR + "secret";

	private static final String DEFAULT_PROFILE_SEPARATOR = "-";

	private static final String DEFAULT_ORIGIN = "aws:secrets:";

	/**
	 * The region to be used by AWS Secrets Manager client.
	 */
	private String region;

	/**
	 * The endpoint to be used by AWS Secrets Manager client. This can be used to specify
	 * an alternate endpoint for the API requests.
	 */
	private String endpoint;

	/**
	 * The default staging label to be used to fetch the secret values. If unset, an
	 * active version of the secret will be fetched (AWSCURRENT).
	 */
	private String defaultLabel;

	/**
	 * Do not set staging label when fetching the secret values.
	 */
	private boolean ignoreLabel;

	/**
	 * The order of the environment repository.
	 */
	private int order = Ordered.LOWEST_PRECEDENCE;

	/**
	 * Prefix indicating first level for every property loaded from AWS Secrets Manager.
	 * Value must start with a forward slash followed by a valid path segment or be empty.
	 * Defaults to "/secret".
	 */
	@NotNull
	@Pattern(regexp = "(/[a-zA-Z0-9.\\-_]+)*")
	private String prefix = DEFAULT_PREFIX;

	/**
	 * String that separates profile from the application name.
	 */
	@NotNull
	@Pattern(regexp = "[a-zA-Z0-9.\\-_]+")
	private String profileSeparator = DEFAULT_PROFILE_SEPARATOR;

	/**
	 * Prefix which indicates the origin of the property. Defaults to "aws:secrets:".
	 */
	@NotNull
	private String origin = DEFAULT_ORIGIN;

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public String getDefaultLabel() {
		return defaultLabel;
	}

	public void setDefaultLabel(String defaultLabel) {
		this.defaultLabel = defaultLabel;
	}

	public boolean isIgnoreLabel() {
		return this.ignoreLabel;
	}

	public void setIgnoreLabel(boolean ignoreLabel) {
		this.ignoreLabel = ignoreLabel;
	}

	public int getOrder() {
		return order;
	}

	@Override
	public void setOrder(int order) {
		this.order = order;
	}

	public String getPrefix() {
		return prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	public String getProfileSeparator() {
		return profileSeparator;
	}

	public void setProfileSeparator(String profileSeparator) {
		this.profileSeparator = profileSeparator;
	}

	public String getOrigin() {
		return origin;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}

}
