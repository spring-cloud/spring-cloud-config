/*
 * Copyright 2015-present the original author or authors.
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

	/**
	 * URIs of the Git repository that sent the notification, as advertised by the webhook
	 * payload. Empty when the payload does not identify the repository.
	 */
	private String[] repositoryUris = new String[0];

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

	public String[] getRepositoryUris() {
		return this.repositoryUris;
	}

	public void setRepositoryUris(String[] repositoryUris) {
		this.repositoryUris = (repositoryUris != null) ? repositoryUris : new String[0];
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
		if (!java.util.Arrays.deepEquals(this.getRepositoryUris(), other.getRepositoryUris())) {
			return false;
		}
		return true;
	}

	public int hashCode() {
		final int PRIME = 59;
		int result = 1;
		result = result * PRIME + java.util.Arrays.deepHashCode(this.getPaths());
		result = result * PRIME + java.util.Arrays.deepHashCode(this.getRepositoryUris());
		return result;
	}

	protected boolean canEqual(Object other) {
		return other instanceof PropertyPathNotification;
	}

	public String toString() {
		return "PropertyPathNotification(paths=" + java.util.Arrays.deepToString(this.getPaths()) + ", repositoryUris="
				+ java.util.Arrays.deepToString(this.getRepositoryUris()) + ")";
	}

}
