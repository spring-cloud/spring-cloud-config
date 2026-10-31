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

package org.springframework.cloud.config.client.validation;

import org.apache.commons.logging.Log;

import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.util.StringUtils;

/**
 * Utility for validating application names used by the Config Client.
 */
public final class ApplicationNameValidator {

	private ApplicationNameValidator() {
	}

	public static boolean validate(ConfigClientProperties properties, Log log) {
		if (!StringUtils.startsWithIgnoreCase(properties.getName(), "application-")) {
			return true;
		}
		InvalidApplicationNameException exception = new InvalidApplicationNameException(properties.getName());

		if (properties.isFailFast()) {
			throw exception;
		}
		log.warn(ConfigClientProperties.NAME_PLACEHOLDER + " resolved to " + properties.getName()
				+ ", not going to load remote properties. Ensure application name doesn't start with 'application-'");

		return false;
	}

}
