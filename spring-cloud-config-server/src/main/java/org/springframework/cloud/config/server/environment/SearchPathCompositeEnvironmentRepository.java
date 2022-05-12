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

package org.springframework.cloud.config.server.environment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.micrometer.observation.ObservationRegistry;

/**
 * A {@link CompositeEnvironmentRepository} which implements {@link SearchPathLocator}.
 *
 * @author Ryan Baxter
 */
public class SearchPathCompositeEnvironmentRepository extends CompositeEnvironmentRepository
		implements SearchPathLocator {

	/**
	 * Creates a new {@link SearchPathCompositeEnvironmentRepository}.
	 * @param environmentRepositories The {@link EnvironmentRepository}s to create this
	 * composite from.
	 * @param observationRegistry observation registry
	 * @param failOnError whether to throw an exception if there is an error.
	 */
	public SearchPathCompositeEnvironmentRepository(List<EnvironmentRepository> environmentRepositories,
			ObservationRegistry observationRegistry, boolean failOnError) {
		super(environmentRepositories, observationRegistry, failOnError);
	}

	@Override
	public Locations getLocations(String application, String profile, String label) {
		List<String> locations = new ArrayList<>();
		for (EnvironmentRepository repo : this.environmentRepositories) {
			if (repo instanceof SearchPathLocator searchPathLocator) {
				addForSearchPathLocators(application, profile, label, locations, searchPathLocator);
			}
			else if (repo instanceof ObservationEnvironmentRepositoryWrapper wrapper
					&& wrapper.getDelegate() instanceof SearchPathLocator searchPathLocator) {
				addForSearchPathLocators(application, profile, label, locations, searchPathLocator);
			}
		}
		return new Locations(application, profile, label, null, locations.toArray(new String[locations.size()]));
	}

	private void addForSearchPathLocators(String application, String profile, String label, List<String> locations,
			SearchPathLocator searchPathLocator) {
		locations.addAll(Arrays.asList(searchPathLocator.getLocations(application, profile, label).getLocations()));
	}

}
