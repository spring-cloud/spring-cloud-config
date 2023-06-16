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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * This test verifies that we are recreating ConfigClientProperties every time we receive
 * a request to load configuration data from the config server. Since
 * optional:configserver is last in the list of spring.config.import it will be processed
 * before applicationname.yaml. applicationname.yaml contains the value for
 * spring.application.name but since Boot has not processed that import yet the initial
 * request to the config server will use the default application name. After the config
 * server import is processed Boot will then load applicationname.yaml and
 * spring.application.name will be set.
 *
 * At this point Boot has collected all active profiles so it will load all
 * spring.config.import statements again with active profiles. The subsequent call then
 * will not have spring.application.name set in the context so the config server config
 * data loader will make a request to the config server with the correct application name.
 *
 * @author Ryan Baxter
 */
public class ConfigClientConfigDataLoaderTest {

	ConfigurableApplicationContext context;

	@Test
	void context() {
		RestTemplate rest = mock(RestTemplate.class);
		Environment environment = new Environment("test", "default");
		ResponseEntity<Environment> responseEntity = mock(ResponseEntity.class);
		when(responseEntity.getStatusCode()).thenReturn(HttpStatus.OK);
		when(responseEntity.getBody()).thenReturn(environment);
		when(rest.exchange(eq("http://localhost:8888/{name}/{profile}"), eq(HttpMethod.GET),
				ArgumentMatchers.any(HttpEntity.class), eq(Environment.class), eq("application"),
				ArgumentMatchers.<String>any())).thenReturn(responseEntity);
		when(rest.exchange(eq("http://localhost:8888/{name}/{profile}"), eq(HttpMethod.GET),
				ArgumentMatchers.any(HttpEntity.class), eq(Environment.class), eq("foo"),
				ArgumentMatchers.<String>any())).thenReturn(responseEntity);
		context = setup(rest).run();
		verify(rest).exchange(eq("http://localhost:8888/{name}/{profile}"), eq(HttpMethod.GET),
				ArgumentMatchers.any(HttpEntity.class), eq(Environment.class), eq("application"),
				ArgumentMatchers.<String>any());
		verify(rest, times(1)).exchange(eq("http://localhost:8888/{name}/{profile}"), eq(HttpMethod.GET),
				ArgumentMatchers.any(HttpEntity.class), eq(Environment.class), eq("foo"),
				ArgumentMatchers.<String>any());
	}

	@AfterEach
	void after() {
		context.close();
	}

	SpringApplicationBuilder setup(RestTemplate restTemplate, String... env) {
		SpringApplicationBuilder builder = new SpringApplicationBuilder(
				DiscoveryClientConfigDataConfigurationTests.TestConfig.class)
						.properties("spring.config.import=classpath:applicationname.yaml, optional:configserver:");
		builder.addBootstrapRegistryInitializer(
				registry -> registry.register(RestTemplate.class, context -> restTemplate));

		return builder;
	}

}
