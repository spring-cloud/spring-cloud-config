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
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.support.InstanceSupplier;
import org.springframework.boot.BootstrapRegistry;
import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.Profiles;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.cloud.bootstrap.TextEncryptorBindHandler;
import org.springframework.cloud.bootstrap.encrypt.KeyProperties;
import org.springframework.cloud.bootstrap.encrypt.TextEncryptorUtils;
import org.springframework.cloud.context.encrypt.EncryptorFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.encrypt.TextEncryptor;

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
		ConfigServerConfigDataResource resource = testResolveProfileSpecific();
		assertThat(resource.getProfiles()).isEqualTo("default");
	}

	@Test
	void configClientProfilesOverridesSpringProfilesActive() {
		this.environment.setProperty(ConfigClientProperties.PREFIX + ".profile", "myprofile");
		ConfigServerConfigDataResource resource = testResolveProfileSpecific();
		assertThat(resource.getProfiles()).isEqualTo("myprofile");
	}

	@Test
	void configClientProfilesAcceptedProfiles() {
		this.environment.setProperty(ConfigClientProperties.PREFIX + ".profile", "myprofile");
		ConfigServerConfigDataResource resource = testResolve();
		assertThat(resource.getAcceptedProfiles()).contains("myprofile");
	}

	@Test
	void configClientProfilesDefaultAcceptedProfiles() {
		ConfigServerConfigDataResource resource = testResolve();
		assertThat(resource.getAcceptedProfiles()).contains("default");
	}

	@Test
	void configClientSpringProfilesActiveOverridesDefaultClientProfiles() {
		ConfigServerConfigDataResource resource = testResolveProfileSpecific("myactiveprofile");
		assertThat(resource.getProfiles()).isEqualTo("myactiveprofile");
	}

	@Test
	void assertConfigDataResourceHasNullProfiles() {
		ConfigServerConfigDataResource resource = testResolve();
		assertThat(resource.isProfileSpecific()).isFalse();
	}

	@Test
	void configNameDefaultsToApplication() {
		ConfigServerConfigDataResource resource = testResolveProfileSpecific();
		assertThat(resource.getProperties().getName()).isEqualTo("application");
	}

	@Test
	void configNameDefaultsToSpringApplicationName() {
		this.environment.setProperty("spring.application.name", "myapp");
		ConfigServerConfigDataResource resource = testResolveProfileSpecific();
		assertThat(resource.getProperties().getName()).isEqualTo("myapp");
	}

	@Test
	void configNameOverridesSpringApplicationName() {
		this.environment.setProperty("spring.application.name", "myapp");
		this.environment.setProperty(ConfigClientProperties.PREFIX + ".name", "myconfigname");
		ConfigServerConfigDataResource resource = testResolveProfileSpecific();
		assertThat(resource.getProperties().getName()).isEqualTo("myconfigname");
	}

	@Test
	void retryPropertiesShouldBeDefaultByDefault() {
		ConfigServerConfigDataResource resource = testResolveProfileSpecific();
		RetryProperties defaultRetry = new RetryProperties();
		assertThat(resource.getRetryProperties().getMaxAttempts()).isEqualTo(defaultRetry.getMaxAttempts());
		assertThat(resource.getRetryProperties().getMaxInterval()).isEqualTo(defaultRetry.getMaxInterval());
		assertThat(resource.getRetryProperties().getInitialInterval()).isEqualTo(defaultRetry.getInitialInterval());
		assertThat(resource.getRetryProperties().getMultiplier()).isEqualTo(defaultRetry.getMultiplier());
		assertThat(resource.getRetryProperties().isUseRandomPolicy()).isEqualTo(defaultRetry.isUseRandomPolicy());
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
		assertThat(resource.getRetryProperties().isUseRandomPolicy()).isEqualTo(false);
	}

	@Test
	void urisInLocationOverridesProperty() {
		String locationUri = "http://actualuri1,http://actualuri2";
		ConfigServerConfigDataResource resource = testUri("http://shouldbeoverridden", locationUri);
		assertThat(resource.getProperties().getUri()).containsExactly(locationUri.split(","));
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
		ConfigServerConfigDataResource resource1 = resources1.get(0);
		assertThat(resource1.getProperties().getUri()).isEqualTo(new String[] { "http://urlNo1" });
		ConfigServerConfigDataResource resource2 = resources2.get(0);
		assertThat(resource2.getProperties().getUri()).isEqualTo(new String[] { "http://urlNo2" });
	}

	@Test
	void setFailsafeDelegateKeysNotConfigured() {
		ConfigurableBootstrapContext bootstrapContext = mock(ConfigurableBootstrapContext.class);
		when(bootstrapContext.isRegistered(eq(ConfigClientProperties.class))).thenReturn(true);
		KeyProperties keyProperties = new KeyProperties();
		ConfigClientProperties configClientProperties = new ConfigClientProperties();
		configClientProperties.setUri(new String[] { "http://myuri" });
		when(bootstrapContext.isRegistered(TextEncryptor.class)).thenReturn(true);
		when(bootstrapContext.get(TextEncryptor.class)).thenReturn(new TextEncryptorUtils.FailsafeTextEncryptor());
		when(bootstrapContext.get(eq(ConfigClientProperties.class))).thenReturn(configClientProperties);
		when(context.getBootstrapContext()).thenReturn(bootstrapContext);
		this.resolver.resolve(context, ConfigDataLocation.of("configserver:http://urlNo1"));
		TextEncryptor textEncryptor = bootstrapContext.get(TextEncryptor.class);
		assertThat(textEncryptor).isInstanceOf(TextEncryptorUtils.FailsafeTextEncryptor.class);
		assertThat(((TextEncryptorUtils.FailsafeTextEncryptor) textEncryptor).getDelegate()).isNull();
	}

	@Test
	void setFailsafeDelegateKeysConfigured() {
		ConfigurableBootstrapContext bootstrapContext = mock(ConfigurableBootstrapContext.class);
		when(bootstrapContext.isRegistered(eq(ConfigClientProperties.class))).thenReturn(true);
		environment.setProperty("encrypt.key", "mykey");

		// The value is "password" encrypted with the key "mykey"
		environment.setProperty("spring.cloud.config.password",
				"{cipher}6defc102cd76752fcf4c78231ed82ead85133a09741d9a1442595b4800e2b3d1");
		ConfigClientProperties configClientProperties = new ConfigClientProperties();
		configClientProperties.setUri(new String[] { "http://myuri" });
		when(bootstrapContext.isRegistered(TextEncryptor.class)).thenReturn(true);
		when(bootstrapContext.get(TextEncryptor.class)).thenReturn(new TextEncryptorUtils.FailsafeTextEncryptor());
		when(bootstrapContext.get(eq(ConfigClientProperties.class))).thenReturn(configClientProperties);
		when(context.getBootstrapContext()).thenReturn(bootstrapContext);
		KeyProperties keyProperties = new KeyProperties();
		keyProperties.setKey("mykey");

		// Use this TextEncryptor in the BindHandler we return so it will decrypt the
		// password when we bind ConfigClientProperties
		TextEncryptor bindHandlerTextEncryptor = new EncryptorFactory(keyProperties.getSalt())
				.create(keyProperties.getKey());
		when(context.getBootstrapContext().getOrElse(eq(BindHandler.class), eq(null)))
				.thenReturn(new TextEncryptorBindHandler(bindHandlerTextEncryptor, keyProperties));

		// Call resolve so we can test that the delegate is added to the
		// FailsafeTextEncryptor
		this.resolver.resolve(context, ConfigDataLocation.of("configserver:http://urlNo1"));
		TextEncryptor textEncryptor = bootstrapContext.get(TextEncryptor.class);

		// Capture the ConfigClientProperties we create and register in
		// ConfigServerConfigDataLocationResolver.resolveProfileSpecific
		// it should have the decrypted passord in it
		ArgumentCaptor<BootstrapRegistry.InstanceSupplier<ConfigClientProperties>> captor = ArgumentCaptor
				.forClass(BootstrapRegistry.InstanceSupplier.class);
		verify(bootstrapContext).register(eq(ConfigClientProperties.class), captor.capture());
		assertThat(captor.getValue().get(bootstrapContext).getPassword()).isEqualTo("password");
		assertThat(textEncryptor).isInstanceOf(TextEncryptorUtils.FailsafeTextEncryptor.class);
		assertThat(((TextEncryptorUtils.FailsafeTextEncryptor) textEncryptor).getDelegate())
				.isInstanceOf(TextEncryptor.class);
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

	private ConfigServerConfigDataResource testResolveProfileSpecific() {
		return testResolveProfileSpecific("default");
	}

	private ConfigServerConfigDataResource testResolveProfileSpecific(String activeProfile) {
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

	private ConfigServerConfigDataResource testResolve() {
		when(context.getBootstrapContext()).thenReturn(mock(ConfigurableBootstrapContext.class));

		List<ConfigServerConfigDataResource> resources = this.resolver.resolve(context,
				ConfigDataLocation.of("configserver:"));
		assertThat(resources).hasSize(1);
		return resources.get(0);
	}

}
