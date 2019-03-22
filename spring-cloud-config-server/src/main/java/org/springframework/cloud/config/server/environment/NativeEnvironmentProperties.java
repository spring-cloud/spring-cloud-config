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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.config.server.support.EnvironmentRepositoryProperties;
import org.springframework.core.Ordered;

/**
 * @author Dylan Roberts
 */
@ConfigurationProperties("spring.cloud.config.server.native")
public class NativeEnvironmentProperties implements EnvironmentRepositoryProperties {

	/**
	 * Flag to determine how to handle exceptions during decryption (default false).
	 */
	private Boolean failOnError = false;

	/**
	 * Flag to determine whether label locations should be added.
	 */
	private Boolean addLabelLocations = true;

	private String defaultLabel = "master";

	/**
	 * Locations to search for configuration files. Defaults to the same as a Spring Boot
	 * app so [classpath:/,classpath:/config/,file:./,file:./config/].
	 */
	private String[] searchLocations = new String[0];

	/**
	 * Version string to be reported for native repository.
	 */
	private String version;

	private int order = Ordered.LOWEST_PRECEDENCE;

	public Boolean getFailOnError() {
		return this.failOnError;
	}

	public void setFailOnError(Boolean failOnError) {
		this.failOnError = failOnError;
	}

	public Boolean getAddLabelLocations() {
		return this.addLabelLocations;
	}

	public void setAddLabelLocations(Boolean addLabelLocations) {
		this.addLabelLocations = addLabelLocations;
	}

	public String getDefaultLabel() {
		return this.defaultLabel;
	}

	public void setDefaultLabel(String defaultLabel) {
		this.defaultLabel = defaultLabel;
	}

	public String[] getSearchLocations() {
		return this.searchLocations;
	}

	public void setSearchLocations(String[] searchLocations) {
		this.searchLocations = searchLocations;
	}

	public String getVersion() {
		return this.version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public int getOrder() {
		return this.order;
	}

	@Override
	public void setOrder(int order) {
		this.order = order;
	}

}
