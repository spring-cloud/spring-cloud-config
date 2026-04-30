/*
 * Copyright 2026-present the original author or authors.
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
import org.springframework.cloud.test.ClassPathExclusions;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Luccas Asaphe
 *
 */
@ClassPathExclusions({ "spring-boot-starter-actuator-*.jar", "spring-boot-restclient-*.jar" })
public class ConfigServerConfigDataWithoutMicrometerTests {

	@Test
	void contextStartsWithoutMicrometer() {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestConfig.class)
			.web(WebApplicationType.NONE)
			.run("--spring.config.import=optional:configserver:")) {
			assertThat(context).isNotNull();
		}
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class TestConfig {

	}

}
