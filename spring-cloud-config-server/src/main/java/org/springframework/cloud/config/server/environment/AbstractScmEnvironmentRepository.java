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

package org.springframework.cloud.config.server.environment;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.support.AbstractScmAccessor;
import org.springframework.cloud.config.server.support.AbstractScmAccessorProperties;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

/**
 * @author Dave Syer
 *
 */
public abstract class AbstractScmEnvironmentRepository extends AbstractScmAccessor
		implements EnvironmentRepository, SearchPathLocator, Ordered {

	private final EnvironmentCleaner cleaner = new EnvironmentCleaner();

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
	public synchronized Environment findOne(String application, String profile, String label) {
		return findOne(application, profile, label, false);
	}

	@Override
	public synchronized Environment findOne(String application, String profile, String label, boolean includeOrigin) {
		var environment = new Environment(application, StringUtils.commaDelimitedListToStringArray(profile), label, "",
				"");

		for (String l : splitAndReorder(label)) {
			var e = findOneInternal(application, profile, l, includeOrigin);
			environment.addAll(e.getPropertySources());
			environment.setVersion(concat(e.getVersion(), environment.getVersion()));
		}

		return this.cleaner.clean(environment, getWorkingDirectory().toURI().toString(), getUri());
	}

	private Environment findOneInternal(String application, String profile, String label, boolean includeOrigin) {
		var delegate = new NativeEnvironmentRepository(getEnvironment(), new NativeEnvironmentProperties(),
				this.observationRegistry);
		var locations = getLocations(application, profile, label);
		delegate.setSearchLocations(locations.getLocations());
		var environment = delegate.findOne(application, profile, "", includeOrigin);
		environment.setVersion(locations.getVersion());
		return environment;
	}

	private List<String> splitAndReorder(String label) {
		var labels = Arrays.stream(StringUtils.commaDelimitedListToStringArray(label))
			.filter(StringUtils::hasText)
			.collect(Collectors.toList());

		// just return the given label (null, empty or invalid...)
		if (labels.isEmpty()) {
			labels.add(label);
			return labels;
		}

		Collections.reverse(labels);
		return labels;
	}

	private String concat(String first, String second) {
		if (StringUtils.hasText(first) && StringUtils.hasText(second)) {
			return first + "," + second;
		}
		if (StringUtils.hasText(first)) {
			return first;
		}
		if (StringUtils.hasText(second)) {
			return second;
		}
		return "";
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

}
