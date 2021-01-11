/*
 * Copyright 2013-2020 the original author or authors.
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

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.Profiles;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConfigServerConfigDataLocationResolverTests {

	private ConfigServerConfigDataLocationResolver resolver;

	private ConfigDataLocationResolverContext context = mock(ConfigDataLocationResolverContext.class);

	private MockEnvironment environment;

	private Binder environmentBinder;

	@BeforeEach
	void setup() {
		this.environment = new MockEnvironment();
		this.environmentBinder = Binder.get(this.environment);
		this.resolver = new ConfigServerConfigDataLocationResolver(new DeferredLog());
		when(context.getBinder()).thenReturn(environmentBinder);
	}

	@Test
	void isResolvableReturnsFalseWithIncorrectPrefix() {
		assertThat(this.resolver.isResolvable(this.context, ConfigDataLocation.of("test:"))).isFalse();
	}

	@Test
	void isResolvableReturnsTrueWithCorrectPrefix() {
		assertThat(this.resolver.isResolvable(this.context, ConfigDataLocation.of("configserver:"))).isTrue();
	}

	@Test
	void isResolvableReturnsFalseWhenDisabled() {
		this.environment.setProperty(ConfigClientProperties.PREFIX + ".enabled", "false");
		assertThat(this.resolver.isResolvable(this.context, ConfigDataLocation.of("configserver:"))).isFalse();
	}

	@Test
	void defaultSpringProfiles() {
		ConfigServerConfigDataResource resource = testResolveProvileSpecific();
		assertThat(resource.getProfiles()).isEqualTo("default");
	}

	@Test
	void configClientProfilesOverridesSpringProfilesActive() {
		this.environment.setProperty(ConfigClientProperties.PREFIX + ".profile", "myprofile");
		ConfigServerConfigDataResource resource = testResolveProvileSpecific();
		assertThat(resource.getProfiles()).isEqualTo("myprofile");
	}

	@Test
	void configClientSpringProfilesActiveOverridesDefaultClientProfiles() {
		ConfigServerConfigDataResource resource = testResolveProvileSpecific("myactiveprofile");
		assertThat(resource.getProfiles()).isEqualTo("myactiveprofile");
	}

	@Test
	void configNameDefaultsToApplication() {
		ConfigServerConfigDataResource resource = testResolveProvileSpecific();
		assertThat(resource.getProperties().getName()).isEqualTo("application");
	}

	@Test
	void configNameDefaultsToSpringApplicationName() {
		this.environment.setProperty("spring.application.name", "myapp");
		ConfigServerConfigDataResource resource = testResolveProvileSpecific();
		assertThat(resource.getProperties().getName()).isEqualTo("myapp");
	}

	@Test
	void configNameOverridesSpringApplicationName() {
		this.environment.setProperty("spring.application.name", "myapp");
		this.environment.setProperty(ConfigClientProperties.PREFIX + ".name", "myconfigname");
		ConfigServerConfigDataResource resource = testResolveProvileSpecific();
		assertThat(resource.getProperties().getName()).isEqualTo("myconfigname");
	}

	private ConfigServerConfigDataResource testResolveProvileSpecific() {
		return testResolveProvileSpecific("default");
	}

	private ConfigServerConfigDataResource testResolveProvileSpecific(String activeProfile) {
		when(context.getBootstrapContext()).thenReturn(mock(ConfigurableBootstrapContext.class));
		Profiles profiles = mock(Profiles.class);
		if (activeProfile != null) {
			when(profiles.getAccepted()).thenReturn(Collections.singletonList(activeProfile));
		}

		List<ConfigServerConfigDataResource> resources = this.resolver.resolveProfileSpecific(context,
				ConfigDataLocation.of("configserver:"), profiles);
		assertThat(resources).hasSize(1);
		ConfigServerConfigDataResource resource = resources.get(0);
		return resource;
	}

}
