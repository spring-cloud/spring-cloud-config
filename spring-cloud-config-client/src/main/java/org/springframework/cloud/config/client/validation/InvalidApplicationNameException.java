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

package org.springframework.cloud.config.client.validation;

import static org.springframework.cloud.config.client.ConfigClientProperties.NAME_PLACEHOLDER;

/**
 * A {@code InvalidApplicationNameException} is thrown when config client detects an
 * invalid application name.
 *
 * @author Anshul Mehra
 */
public class InvalidApplicationNameException extends RuntimeException {

	private final String property = NAME_PLACEHOLDER;

	private final String value;

	public InvalidApplicationNameException(String currentResolvedValue) {
		super("Application name must not start with 'application-'");
		this.value = currentResolvedValue;
	}

	public String getProperty() {
		return this.property;
	}

	public String getValue() {
		return this.value;
	}

}
