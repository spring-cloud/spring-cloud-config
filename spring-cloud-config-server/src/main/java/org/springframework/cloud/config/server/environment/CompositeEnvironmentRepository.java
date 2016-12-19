/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.config.server.environment;

import java.util.Collections;
import java.util.List;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.core.OrderComparator;

/**
 * An {@link EnvironmentRepository} composed of multiple ordered {@link OrderedEnvironmentRepository}s.
 * @author Ryan Baxter
 */
public class CompositeEnvironmentRepository implements EnvironmentRepository {

	protected List<OrderedEnvironmentRepository> environmentRepositories;

	/**
	 * Creates a new {@link CompositeEnvironmentRepository}.
	 * @param environmentRepositories The list of {@link OrderedEnvironmentRepository}s to create the composite from.
	 */
	public CompositeEnvironmentRepository(List<OrderedEnvironmentRepository> environmentRepositories) {
		//Sort the environment repositories by the priority
		Collections.sort(environmentRepositories, OrderComparator.INSTANCE);
		this.environmentRepositories = environmentRepositories;
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		Environment env = new Environment(application, new String[]{profile}, label, null, null);
		for(EnvironmentRepository repo : environmentRepositories) {
			env.add(repo.findOne(application, profile, label).getPropertySources());
		}
		return env;
	}
}
