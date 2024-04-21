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

import io.micrometer.observation.ObservationRegistry;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.support.AbstractScmAccessor;
import org.springframework.cloud.config.server.support.AbstractScmAccessorProperties;
import org.springframework.cloud.config.server.support.RequestContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * @author Dave Syer
 *
 */
public abstract class AbstractScmEnvironmentRepository extends AbstractScmAccessor
		implements EnvironmentRepository, SearchPathLocator, Ordered {

	private EnvironmentCleaner cleaner = new EnvironmentCleaner();

	private int order = Ordered.LOWEST_PRECEDENCE;

	private final ObservationRegistry observationRegistry;

	public AbstractScmEnvironmentRepository(ConfigurableEnvironment environment,
			ObservationRegistry observationRegistry) {
		super(environment);
		this.observationRegistry = observationRegistry;
	}

	public AbstractScmEnvironmentRepository(ConfigurableEnvironment environment,
			AbstractScmAccessorProperties properties, ObservationRegistry observationRegistry) {
		super(environment, properties);
		this.order = properties.getOrder();
		this.observationRegistry = observationRegistry;
	}

	@Override
	public synchronized Environment findOne(RequestContext ctx) {
		NativeEnvironmentRepository delegate = new NativeEnvironmentRepository(getEnvironment(),
				new NativeEnvironmentProperties(), this.observationRegistry);
		Locations locations = getLocations(ctx);
		delegate.setSearchLocations(locations.getLocations());
		Environment result = delegate.findOne(ctx.toBuilder().label("").build());
		result.setVersion(locations.getVersion());
		result.setLabel(ctx.getLabel());
		return this.cleaner.clean(result, getWorkingDirectory().toURI().toString(), getUri());
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

}
