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

package org.springframework.cloud.config.server.environment;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.cloud.config.server.support.RequestContext;

/**
 * Strategy for locating a search path for resource (e.g. in the file system or
 * classpath).
 *
 * @author Dave Syer
 *
 */
public interface SearchPathLocator {

	Locations getLocations(RequestContext ctx);

	/**
	 * Locations POJO.
	 */
	class Locations {

		private final String application;

		private final String profile;

		private final String label;

		private final String[] locations;

		private final String version;

		public Locations(String application, String profile, String label, String version, String[] locations) {
			this.application = application;
			this.profile = profile;
			this.label = label;
			this.locations = locations;
			this.version = version;
		}

		public String[] getLocations() {
			return this.locations;
		}

		public String getVersion() {
			return this.version;
		}

		public String getApplication() {
			return this.application;
		}

		public String getProfile() {
			return this.profile;
		}

		public String getLabel() {
			return this.label;
		}

		@Override
		public String toString() {
			return "Locations [application=" + this.application + ", profile=" + this.profile + ", label=" + this.label
					+ ", locations=" + Arrays.toString(this.locations) + ", version=" + this.version + "]";
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			Locations locations1 = (Locations) o;
			return getApplication().equals(locations1.getApplication()) && getProfile().equals(locations1.getProfile())
					&& Objects.equals(getLabel(), locations1.getLabel())
					&& Arrays.equals(getLocations(), locations1.getLocations())
					&& Objects.equals(getVersion(), locations1.getVersion());
		}

		@Override
		public int hashCode() {
			int result = Objects.hash(getApplication(), getProfile(), getLabel(), getVersion());
			result = 31 * result + Arrays.hashCode(getLocations());
			return result;
		}

	}

}
