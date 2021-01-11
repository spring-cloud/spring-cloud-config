/*
 * Copyright 2018-2019 the original author or authors.
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

package sample;

import org.junit.Test;

import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ApplicationFailFastTests {

	@Test
	public void bootstrapContextFails() {
		assertThatThrownBy(() -> {
			new SpringApplicationBuilder().sources(Application.class).run("--spring.config.use-legacy-processing=true",
					"--server.port=0", "--spring.cloud.config.enabled=true", "--spring.cloud.config.fail-fast=true",
					"--spring.cloud.config.uri=http://serverhostdoesnotexist:1234");
		}).as("Exception not caused by fail fast").hasMessageContaining("fail fast");
	}

	@Test
	public void configDataContextFails() {
		assertThatThrownBy(() -> {
			new SpringApplicationBuilder().sources(Application.class).run("--server.port=0",
					"--spring.cloud.config.enabled=true", "--spring.cloud.config.fail-fast=true",
					"--spring.config.import=optional:configserver:http://serverhostdoesnotexist:1234");
		}).as("Exception not caused by fail fast").hasMessageContaining("fail fast");

	}

}
