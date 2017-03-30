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
 * An {@link EnvironmentRepository} composed of multiple ordered {@link EnvironmentRepository}s.
 * @author Ryan Baxter
 * @author Mark Bonnekessel
 */
public class CompositeEnvironmentRepository implements EnvironmentRepository {

	protected List<EnvironmentRepository> environmentRepositories;

	/**
	 * Creates a new {@link CompositeEnvironmentRepository}.
	 * @param environmentRepositories The list of {@link EnvironmentRepository}s to create the composite from.
	 */
	public CompositeEnvironmentRepository(List<EnvironmentRepository> environmentRepositories) {
		//Sort the environment repositories by the priority
		Collections.sort(environmentRepositories, OrderComparator.INSTANCE);
		this.environmentRepositories = environmentRepositories;
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		Environment resultEnv = new Environment(application, new String[]{profile}, label, null, null);
		for(EnvironmentRepository repo : environmentRepositories) {
			Environment env = repo.findOne(application, profile, label);
			if(env.getCached()){
				resultEnv.setCached(true);
			}

			resultEnv.addAll(env.getPropertySources());
		}
		return resultEnv;
	}
}
