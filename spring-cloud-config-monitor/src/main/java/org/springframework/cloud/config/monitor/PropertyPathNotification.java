/*
 * Copyright 2015-2019 the original author or authors.
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

package org.springframework.cloud.config.monitor;

/**
 * Simple abstraction of a list of paths that changed in a repository.
 *
 * @author Dave Syer
 *
 */
public class PropertyPathNotification {

	private String[] paths;

	public PropertyPathNotification(String... paths) {
		this.paths = paths;
	}

	public PropertyPathNotification() {
	}

	public String[] getPaths() {
		return this.paths;
	}

	public void setPaths(String[] paths) {
		this.paths = paths;
	}

	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof PropertyPathNotification)) {
			return false;
		}
		final PropertyPathNotification other = (PropertyPathNotification) o;
		if (!other.canEqual(this)) {
			return false;
		}
		if (!java.util.Arrays.deepEquals(this.getPaths(), other.getPaths())) {
			return false;
		}
		return true;
	}

	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		result = result * PRIME + java.util.Arrays.deepHashCode(this.getPaths());
		return result;
	}

	protected boolean canEqual(Object other) {
		return other instanceof PropertyPathNotification;
	}

	public String toString() {
		return "PropertyPathNotification(paths="
				+ java.util.Arrays.deepToString(this.getPaths()) + ")";
	}

}
