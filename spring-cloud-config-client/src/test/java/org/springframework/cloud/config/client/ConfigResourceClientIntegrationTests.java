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
 * Verifies that the config server import exposes its {@link RestTemplate} and a
 * {@link RestClient} as beans, and that a {@link ConfigResourceClient} is built from
 * them.
 *
 * @author Sharang Gupta
 */
public class ConfigResourceClientIntegrationTests {

	@Test
	void configDataImportExposesTheRestClientAndTheResourceClient() {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestConfig.class)
			.web(WebApplicationType.NONE)
			.run("--spring.config.import=optional:configserver:http://localhost:1", "--spring.cloud.config.name=app")) {
			assertThat(context.getBean(ConfigServerConfigDataLocationResolver.REST_TEMPLATE_BEAN_NAME))
				.isInstanceOf(RestTemplate.class);
			assertThat(context.getBean(ConfigServerConfigDataLocationResolver.REST_CLIENT_BEAN_NAME))
				.isInstanceOf(RestClient.class);
			assertThat(context.getBean(ConfigResourceClient.class)).isInstanceOf(DefaultConfigResourceClient.class);
		}
	}

	@Test
	void noResourceClientWithoutTheConfigServerImport() {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestConfig.class)
			.web(WebApplicationType.NONE)
			.run("--spring.cloud.config.import-check.enabled=false")) {
			assertThat(context.containsBean(ConfigServerConfigDataLocationResolver.REST_CLIENT_BEAN_NAME)).isFalse();
			assertThat(context.getBeanProvider(ConfigResourceClient.class).getIfAvailable()).isNull();
		}
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class TestConfig {

	}

}
