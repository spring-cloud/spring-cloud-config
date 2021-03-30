/*
 * Copyright 2013-2019 the original author or authors.
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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.commons.ConfigDataMissingEnvironmentPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.config.client.ConfigServerConfigDataLocationResolver.PREFIX;

/**
 * @author Spencer Gibb
 */
@ExtendWith(OutputCaptureExtension.class)
public class ConfigServerConfigDataNoImportIntegrationTests {

	private static final String APP_NAME = "testConfigDataNoImport";

	@Test
	public void exceptionThrownIfNoImport(CapturedOutput output) {
		Assertions
				.assertThatThrownBy(() -> new SpringApplicationBuilder(Config.class).web(WebApplicationType.NONE)
						.run("--spring.application.name=" + APP_NAME))
				.isInstanceOf(ConfigDataMissingEnvironmentPostProcessor.ImportException.class);

		assertThat(output).contains("No spring.config.import property has been defined")
				.contains("Add a spring.config.import=configserver: property to your configuration");
	}

	@Test
	public void exceptionThrownIfImportMissing(CapturedOutput output) {
		Assertions
				.assertThatThrownBy(() -> new SpringApplicationBuilder(Config.class).web(WebApplicationType.NONE).run(
						"--spring.config.import=optional:file:somefile.properties",
						"--spring.application.name=" + APP_NAME))
				.isInstanceOf(ConfigDataMissingEnvironmentPostProcessor.ImportException.class);

		assertThat(output).contains("spring.config.import property is missing a " + PREFIX)
				.contains("Add a spring.config.import=configserver: property to your configuration");
	}

	@Test
	public void noExceptionThrownIfConfigDisabled() {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(Config.class)
				.web(WebApplicationType.NONE)
				.run("--spring.cloud.config.enabled=false", "--spring.application.name=" + APP_NAME)) {
			// nothing to do
		}
	}

	@Test
	public void noExceptionThrownIfImportCheckDisabled() {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(Config.class)
				.web(WebApplicationType.NONE)
				.run("--spring.cloud.config.import-check.enabled=false", "--spring.application.name=" + APP_NAME)) {
			// nothing to do
		}
	}

	@Configuration
	@EnableAutoConfiguration
	static class Config {

	}

}
