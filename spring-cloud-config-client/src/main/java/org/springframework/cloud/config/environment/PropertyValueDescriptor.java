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

package org.springframework.cloud.config.environment;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A description of a property's value, including its origin if available.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PropertyValueDescriptor {

	private final Object value;

	private String origin;

	@JsonCreator
	public PropertyValueDescriptor(@JsonProperty("value") Object value,
			@JsonProperty("origin") String origin) {
		this.value = value;
		this.origin = origin;
	}

	public Object getValue() {
		return this.value;
	}

	public String getOrigin() {
		return this.origin;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}

	/**
	 * Places in config server call to string expecting to get the value.
	 * @return the value toString if not null.
	 */
	@Override
	public String toString() {
		return this.value == null ? null : this.value.toString();
	}

}
