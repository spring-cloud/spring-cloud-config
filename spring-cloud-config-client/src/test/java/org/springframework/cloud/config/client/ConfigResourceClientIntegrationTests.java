/*
 * Copyright 2013-present the original author or authors.
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

import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the config server import keeps its connection for a
 * {@link ConfigResourceClient} without exposing a {@link RestTemplate} or a
 * {@link RestClient} bean.
 *
 * @author Sharang Gupta
 */
public class ConfigResourceClientIntegrationTests {

	@Test
	void configDataImportProvidesTheResourceClient() {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestConfig.class)
			.web(WebApplicationType.NONE)
			.run("--spring.config.import=optional:configserver:http://localhost:1", "--spring.cloud.config.name=app")) {
			assertThat(context.getBean(ConfigServerRestClientHolder.class).getRestClient()).isNotNull();
			assertThat(context.getBean(ConfigResourceClient.class)).isInstanceOf(DefaultConfigResourceClient.class);
			// The connection is not exposed as a RestClient or RestTemplate bean
			assertThat(context.getBeanNamesForType(RestClient.class)).isEmpty();
			assertThat(context.getBeanNamesForType(RestTemplate.class)).isEmpty();
		}
	}

	@Test
	void noResourceClientWithoutTheConfigServerImport() {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestConfig.class)
			.web(WebApplicationType.NONE)
			.run("--spring.cloud.config.import-check.enabled=false")) {
			assertThat(context.getBeanProvider(ConfigServerRestClientHolder.class).getIfAvailable()).isNull();
			assertThat(context.getBeanProvider(ConfigResourceClient.class).getIfAvailable()).isNull();
		}
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class TestConfig {

	}

}
