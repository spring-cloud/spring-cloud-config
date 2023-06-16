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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigDataLoaderContext;
import org.springframework.boot.context.config.Profiles;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Ryan Baxter
 */
class ConfigServerConfigDataLoaderTests {

	@Test
	void nonProfileSpecific() {
		PropertySource p1 = new PropertySource("p1", new HashMap<>());
		PropertySource p2 = new PropertySource("p2", new HashMap<>());
		ConfigData configData = setupConfigServerConfigDataLoader(Arrays.asList(p1, p2), "application-slash", null);
		assertThat(configData.getPropertySources().size()).isEqualTo(3);
		assertThat(configData.getOptions(configData.getPropertySources().get(0))
				.contains(ConfigData.Option.IGNORE_IMPORTS)).isTrue();
		assertThat(configData.getOptions(configData.getPropertySources().get(1))
				.contains(ConfigData.Option.IGNORE_IMPORTS)).isTrue();
		assertThat(configData.getOptions(configData.getPropertySources().get(2))
				.contains(ConfigData.Option.IGNORE_IMPORTS)).isTrue();

	}

	@Disabled
	@Test
	// TODO Enable once we have https://github.com/spring-cloud/spring-cloud-config/issues/2291
	void filterPropertySourcesThatAreNotProfileSpecific() {
		PropertySource p1 = new PropertySource("p1", Collections.singletonMap("foo", "bar"));
		PropertySource p2 = new PropertySource("p2", Collections.singletonMap("hello", "world"));
		ConfigData configData = setupConfigServerConfigDataLoader(Arrays.asList(p1, p2), "application-slash", "dev");
		assertThat(configData.getPropertySources().size()).isEqualTo(0);

	}

	@Disabled
	@Test
	// TODO Enable once we have https://github.com/spring-cloud/spring-cloud-config/issues/2291
	void returnPropertySourcesThatAreProfileSpecific() {
		PropertySource p1 = new PropertySource("p1-dev", Collections.singletonMap("foo", "bar"));
		PropertySource p2 = new PropertySource("p2-dev", Collections.singletonMap("hello", "world"));
		List<PropertySource> propertySources = Arrays.asList(p1, p2);
		ConfigData configData = setupConfigServerConfigDataLoader(propertySources, "application-slash", "dev");
		assertThat(configData.getPropertySources().size()).isEqualTo(2);
		assertThat(configData.getOptions(configData.getPropertySources().get(0))
				.contains(ConfigData.Option.IGNORE_IMPORTS)).isTrue();
		assertThat(configData.getOptions(configData.getPropertySources().get(1))
				.contains(ConfigData.Option.IGNORE_IMPORTS)).isTrue();

	}

	@Disabled
	@Test
	// TODO Enable once we have https://github.com/spring-cloud/spring-cloud-config/issues/2291
	void filterPropertySourcesWithDocuments() {
		PropertySource p1 = new PropertySource(
			"configserver:git@github.com:demo/support-configuration-repo.git/application.yml",
			Collections.singletonMap("foo", "bar"));
		PropertySource p2 = new PropertySource(
			"configserver:git@github.com:demo/support-configuration-repo.git/application-foo.yml",
			Collections.singletonMap("foo", "bar"));
		PropertySource p3 = new PropertySource(
			"configserver:git@github.com:demo/support-configuration-repo.git/Config resource 'file [/var/folders/k3/zv8hzdm17vv69j485fv3cf9r0000gp/T/config-repo-14772121892716396795/commons/application.properties' via location 'commons/' (document #0)",
			Collections.singletonMap("hello", "world"));
		PropertySource p4 = new PropertySource(
			"configserver:git@github.com:demo/support-configuration-repo.git/Config resource 'file [/var/folders/k3/zv8hzdm17vv69j485fv3cf9r0000gp/T/config-repo-14772121892716396795/commons/application-foo.properties' via location 'commons/' (document #0)",
			Collections.singletonMap("hello", "world"));
		Map<String, Object> activatesOnProfileCamelCase = new HashMap<>();
		activatesOnProfileCamelCase.put("spring.config.activate.onProfile", "foo");
		PropertySource p5 = new PropertySource(
			"configserver:git@github.com:demo/support-configuration-repo.git/Config resource 'file [/var/folders/k3/zv8hzdm17vv69j485fv3cf9r0000gp/T/config-repo-14772121892716396795/commons/application.properties' via location 'commons/' (document #1)",
			activatesOnProfileCamelCase);
		Map<String, Object> activatesOnProfile = new HashMap<>();
		activatesOnProfile.put("spring.config.activate.on-profile", "foo");
		PropertySource p6 = new PropertySource(
			"configserver:git@github.com:demo/support-configuration-repo.git/Config resource 'file [/var/folders/k3/zv8hzdm17vv69j485fv3cf9r0000gp/T/config-repo-14772121892716396795/commons/application.properties' via location 'commons/' (document #2)",
			activatesOnProfile);
		PropertySource p7 = new PropertySource(
			"configserver:git@github.com:demo/support-configuration-repo.git/application-foo.yaml",
			Collections.singletonMap("hello", "world"));
		PropertySource p8 = new PropertySource(
			"configserver:git@github.com:demo/support-configuration-repo.git/Config resource 'file [/var/folders/k3/zv8hzdm17vv69j485fv3cf9r0000gp/T/config-repo-14772121892716396795/commons/application-foo.yaml' via location 'commons/' (document #0)",
			Collections.singletonMap("hello", "world"));
		PropertySource p9 = new PropertySource(
			"configserver:git@github.com:demo/support-configuration-repo.git/Config resource 'file [/var/folders/k3/zv8hzdm17vv69j485fv3cf9r0000gp/T/config-repo-14772121892716396795/commons/application-foo.yaml' via location 'commons/' (document #1)",
			Collections.singletonMap("hello", "world"));

		ConfigData configData = setupConfigServerConfigDataLoader(Arrays.asList(p1, p2, p3, p4, p5, p6, p7, p8, p9),
			"application-slash", "foo");
		assertThat(configData.getPropertySources().size()).isEqualTo(7);

	}

	private ConfigData setupConfigServerConfigDataLoader(List<PropertySource> propertySources, String applicationName,
			String... profileList) {
		RestTemplate rest = mock(RestTemplate.class);
		Environment environment = new Environment("test", profileList);
		environment.addAll(propertySources);

		ResponseEntity<Environment> responseEntity = mock(ResponseEntity.class);
		when(responseEntity.getStatusCode()).thenReturn(HttpStatus.OK);
		when(responseEntity.getBody()).thenReturn(environment);
		when(rest.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Environment.class),
				eq(applicationName), ArgumentMatchers.<String>any())).thenReturn(responseEntity);

		ConfigurableBootstrapContext bootstrapContext = mock(ConfigurableBootstrapContext.class);
		when(bootstrapContext.get(eq(ConfigClientRequestTemplateFactory.class)))
				.thenReturn(mock(ConfigClientRequestTemplateFactory.class));
		when(bootstrapContext.get(eq(RestTemplate.class))).thenReturn(rest);

		ConfigServerConfigDataLoader loader = new ConfigServerConfigDataLoader(destination -> mock(Log.class));
		ConfigDataLoaderContext context = mock(ConfigDataLoaderContext.class);
		when(context.getBootstrapContext()).thenReturn(bootstrapContext);

		ConfigClientProperties properties = new ConfigClientProperties();
		properties.setName(applicationName);
		Profiles profiles = mock(Profiles.class);
		when(profiles.getAccepted())
				.thenReturn(profileList == null ? Collections.singletonList("default") : Arrays.asList(profileList));
		ConfigServerConfigDataResource resource = new ConfigServerConfigDataResource(properties, false, profiles);
		resource.setProfileSpecific(!ObjectUtils.isEmpty(profileList));

		return loader.doLoad(context, resource);

	}

}
