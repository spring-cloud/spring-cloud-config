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
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
		this.resolver = new ConfigServerConfigDataLocationResolver(destination -> new DeferredLog());
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

	@Test
	void retryPropertiesShouldBeDefaultByDefault() {
		ConfigServerConfigDataResource resource = testResolveProvileSpecific();
		RetryProperties defaultRetry = new RetryProperties();
		assertThat(resource.getRetryProperties().getMaxAttempts()).isEqualTo(defaultRetry.getMaxAttempts());
		assertThat(resource.getRetryProperties().getMaxInterval()).isEqualTo(defaultRetry.getMaxInterval());
		assertThat(resource.getRetryProperties().getInitialInterval()).isEqualTo(defaultRetry.getInitialInterval());
		assertThat(resource.getRetryProperties().getMultiplier()).isEqualTo(defaultRetry.getMultiplier());
	}

	@Test
	void uriInLocationOverridesProperty() {
		String locationUri = "http://actualuri";
		ConfigServerConfigDataResource resource = testUri("http://shouldbeoverridden", locationUri);
		assertThat(resource.getProperties().getUri()).containsExactly(locationUri);
	}

	@Test
	void uriWithParamsParsesProperties() {
		String locationUri = "http://actualuri";
		ConfigServerConfigDataResource resource = testUri("http://shouldbeoverridden",
				locationUri + "?fail-fast=true&max-attempts=10&max-interval=1500&multiplier=1.2&initial-interval=1100");
		assertThat(resource.getProperties().getUri()).containsExactly(locationUri);
		assertThat(resource.getProperties().isFailFast()).isTrue();
		assertThat(resource.getRetryProperties().getMaxAttempts()).isEqualTo(10);
		assertThat(resource.getRetryProperties().getMaxInterval()).isEqualTo(1500);
		assertThat(resource.getRetryProperties().getInitialInterval()).isEqualTo(1100);
		assertThat(resource.getRetryProperties().getMultiplier()).isEqualTo(1.2);
	}

	@Test
	void urisInLocationOverridesProperty() {
		String locationUri = "http://actualuri1,http://actualuri2";
		ConfigServerConfigDataResource resource = testUri("http://shouldbeoverridden", locationUri);
		assertThat(resource.getProperties().getUri()).containsExactly(locationUri.split(","));
	}

	@Test
	void useExistingConfigClientPropertiesInBootstrapContext() {
		ConfigurableBootstrapContext bootstrapContext = mock(ConfigurableBootstrapContext.class);
		when(bootstrapContext.isRegistered(eq(ConfigClientProperties.class))).thenReturn(true);
		ConfigClientProperties configClientProperties = new ConfigClientProperties();
		configClientProperties.setUri(new String[] { "http://myuri" });
		when(bootstrapContext.get(eq(ConfigClientProperties.class))).thenReturn(configClientProperties);
		when(context.getBootstrapContext()).thenReturn(bootstrapContext);
		List<ConfigServerConfigDataResource> resources = this.resolver.resolveProfileSpecific(context,
				ConfigDataLocation.of("configserver:"), mock(Profiles.class));
		assertThat(resources).hasSize(1);
		verify(bootstrapContext, times(1)).get(eq(ConfigClientProperties.class));
		ConfigServerConfigDataResource resource = resources.get(0);
		assertThat(resource.getProperties().getUri()).isEqualTo(new String[] { "http://myuri" });
	}

	@Test
	void createNewConfigClientPropertiesInBootstrapContext() {
		ConfigurableBootstrapContext bootstrapContext = mock(ConfigurableBootstrapContext.class);
		when(bootstrapContext.isRegistered(eq(ConfigClientProperties.class))).thenReturn(false);
		ConfigClientProperties configClientProperties = new ConfigClientProperties();
		configClientProperties.setUri(new String[] { "http://myuri" });
		when(bootstrapContext.get(eq(ConfigClientProperties.class))).thenReturn(configClientProperties);
		when(context.getBootstrapContext()).thenReturn(bootstrapContext);
		List<ConfigServerConfigDataResource> resources = this.resolver.resolveProfileSpecific(context,
				ConfigDataLocation.of("configserver:http://locationuri"), mock(Profiles.class));
		assertThat(resources).hasSize(1);
		verify(bootstrapContext, times(0)).get(eq(ConfigClientProperties.class));
		ConfigServerConfigDataResource resource = resources.get(0);
		assertThat(resource.getProperties().getUri()).isEqualTo(new String[] { "http://locationuri" });
	}

	@Test
	void multipleImportEntriesDoesNotShareSameURIs() {
		ConfigurableBootstrapContext bootstrapContext = mock(ConfigurableBootstrapContext.class);
		when(bootstrapContext.isRegistered(eq(ConfigClientProperties.class))).thenReturn(true);
		ConfigClientProperties configClientProperties = new ConfigClientProperties();
		configClientProperties.setUri(new String[] { "http://myuri" });
		when(bootstrapContext.get(eq(ConfigClientProperties.class))).thenReturn(configClientProperties);
		when(context.getBootstrapContext()).thenReturn(bootstrapContext);
		List<ConfigServerConfigDataResource> resources1 = this.resolver.resolveProfileSpecific(context,
				ConfigDataLocation.of("configserver:http://urlNo1"), mock(Profiles.class));
		List<ConfigServerConfigDataResource> resources2 = this.resolver.resolveProfileSpecific(context,
				ConfigDataLocation.of("configserver:http://urlNo2"), mock(Profiles.class));
		assertThat(resources1).hasSize(1);
		assertThat(resources2).hasSize(1);
		verify(bootstrapContext, times(2)).get(eq(ConfigClientProperties.class));
		ConfigServerConfigDataResource resource1 = resources1.get(0);
		assertThat(resource1.getProperties().getUri()).isEqualTo(new String[] { "http://urlNo1" });
		ConfigServerConfigDataResource resource2 = resources2.get(0);
		assertThat(resource2.getProperties().getUri()).isEqualTo(new String[] { "http://urlNo2" });
	}

	private ConfigServerConfigDataResource testUri(String propertyUri, String locationUri) {
		this.environment.setProperty(ConfigClientProperties.PREFIX + ".uri", propertyUri);
		when(context.getBootstrapContext()).thenReturn(mock(ConfigurableBootstrapContext.class));
		Profiles profiles = mock(Profiles.class);
		List<ConfigServerConfigDataResource> resources = this.resolver.resolveProfileSpecific(context,
				ConfigDataLocation.of("configserver:" + locationUri), profiles);
		assertThat(resources).hasSize(1);
		return resources.get(0);
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
		return resources.get(0);
	}

}
