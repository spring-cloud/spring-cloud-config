/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.config.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;

import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.core.env.AbstractEnvironment.ACTIVE_PROFILES_PROPERTY_NAME;

/**
 * @author Ryan Baxter
 */
class RemoteEnvironmentFetcherTests {

	@Test
	void fetch() {
		RemoteEnvironmentFetcher fetcher = new RemoteEnvironmentFetcher();
		Environment environment = mock(Environment.class);
		Map<String, Object> properties = new HashMap<>();
		properties.put("test1", "foo");
		properties.put("test2", "bar");
		List<PropertySource> propertySources = Lists.list(new PropertySource("p1", properties));
		when(environment.getPropertySources()).thenReturn(propertySources);
		when(environment.getProfiles()).thenReturn(new String[] { "default" });
		when(environment.getState()).thenReturn("state");
		when(environment.getVersion()).thenReturn("version");
		List<org.springframework.core.env.PropertySource> addedPropertySources = new ArrayList<>();
		Supplier<Environment> spySupplier = mock(Supplier.class);
		when(spySupplier.get()).thenReturn(environment);
		fetcher.fetch(spySupplier, new ConfigClientProperties(), (p) -> addedPropertySources.add(0, p));
		assertThat(addedPropertySources.size()).isEqualTo(2);
		assertThat(addedPropertySources.get(0).getName()).isEqualTo("configClient");
		assertThat(addedPropertySources.get(0).getProperty("config.client.version")).isEqualTo("version");
		assertThat(addedPropertySources.get(0).getProperty("config.client.state")).isEqualTo("state");
		assertThat(addedPropertySources.get(1).getName()).isEqualTo("configserver:p1");
		assertThat(addedPropertySources.get(1).getProperty("test2")).isEqualTo("bar");
		assertThat(addedPropertySources.get(1).getProperty("test1")).isEqualTo("foo");

	}

	@Test
	void fetchWithActivate() {
		RemoteEnvironmentFetcher fetcher = new RemoteEnvironmentFetcher();
		Environment environment = mock(Environment.class);
		Map<String, Object> defaultProperties = new HashMap<>();
		defaultProperties.put("test1", "foo");
		defaultProperties.put("test2", "bar");
		defaultProperties.put(ACTIVE_PROFILES_PROPERTY_NAME, OriginTrackedValue.of("testprofile"));
		Map<String, Object> testProfileProperties = new HashMap<>();
		testProfileProperties.put("test3", "helloworld");
		testProfileProperties.put(ACTIVE_PROFILES_PROPERTY_NAME, OriginTrackedValue.of("anotherprofile"));
		List<PropertySource> propertySources = Lists.list(new PropertySource("p1", defaultProperties));
		when(environment.getPropertySources()).thenReturn(propertySources);

		ConfigClientProperties configClientProperties = new ConfigClientProperties();
		when(environment.getProfiles()).thenReturn(new String[] { "default" }, new String[] { "default" },
				new String[] { "default" }, new String[] { "testprofile" });
		when(environment.getState()).thenReturn("state");
		when(environment.getVersion()).thenReturn("version");
		List<org.springframework.core.env.PropertySource> addedPropertySources = new ArrayList<>();
		Supplier<Environment> spySupplier = mock(Supplier.class);
		when(spySupplier.get()).then((invocation) -> {
			if (configClientProperties.getProfile().equals("testprofile")) {
				when(environment.getPropertySources())
						.thenReturn(Lists.list(new PropertySource("p2", testProfileProperties)));
			}
			return environment;
		});

		fetcher.fetch(spySupplier, configClientProperties, (p) -> addedPropertySources.add(0, p));
		verify(spySupplier, times(2)).get();
		assertThat(addedPropertySources.size()).isEqualTo(3);
		assertThat(addedPropertySources.get(0).getName()).isEqualTo("configClient");
		assertThat(addedPropertySources.get(0).getProperty("config.client.version")).isEqualTo("version");
		assertThat(addedPropertySources.get(0).getProperty("config.client.state")).isEqualTo("state");
		assertThat(addedPropertySources.get(1).getName()).isEqualTo("configserver:p2");
		assertThat(addedPropertySources.get(1).getProperty("test3")).isEqualTo("helloworld");
		assertThat(addedPropertySources.get(1).getProperty(ACTIVE_PROFILES_PROPERTY_NAME)).isEqualTo("anotherprofile");
		assertThat(addedPropertySources.get(2).getName()).isEqualTo("configserver:p1");
		assertThat(addedPropertySources.get(2).getProperty("test2")).isEqualTo("bar");
		assertThat(addedPropertySources.get(2).getProperty("test1")).isEqualTo("foo");
		assertThat(addedPropertySources.get(2).getProperty(ACTIVE_PROFILES_PROPERTY_NAME)).isEqualTo("testprofile");

	}

}
