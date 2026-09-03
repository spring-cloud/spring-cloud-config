/*
 * Copyright 2018-present the original author or authors.
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/**
 * @author Piotr Mińkowski
 * @author KNV Srinivas
 */
public class RedisEnvironmentRepository implements EnvironmentRepository, Ordered {

	private static final String DEFAULT_APPLICATION = "application";

	private final StringRedisTemplate redis;

	private final RedisEnvironmentProperties properties;

	private final int order;

	public RedisEnvironmentRepository(StringRedisTemplate redis, RedisEnvironmentProperties properties) {
		this.redis = redis;
		this.properties = properties;
		this.order = properties.getOrder();
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		Environment environment = new Environment(application, profiles, label, null, null);
		final List<String> keys = addKeys(application, Arrays.asList(profiles));
		keys.forEach(it -> {
			Map<?, ?> m = redis.opsForHash().entries(it);
			environment.add(new PropertySource("redis:" + it, m));
		});
		return environment;
	}

	private List<String> addKeys(String application, List<String> profiles) {
		// Same normalisation as CredhubEnvironmentRepository: `application` may be null
		// or comma-delimited, and the shared hash goes first so that every requested
		// application ends up ahead of it once the keys are reversed below.
		List<String> applications = Stream
			.concat(Stream.of(DEFAULT_APPLICATION),
					Arrays.stream(StringUtils.commaDelimitedListToStringArray(application)))
			.map(String::trim)
			.filter(StringUtils::hasText)
			.distinct()
			.collect(Collectors.toList());
		List<String> keys = new ArrayList<>(applications);
		for (String profile : profiles) {
			for (String app : applications) {
				keys.add(app + "-" + profile);
			}
		}
		Collections.reverse(keys);
		return keys;
	}

	@Override
	public int getOrder() {
		return order;
	}

}
