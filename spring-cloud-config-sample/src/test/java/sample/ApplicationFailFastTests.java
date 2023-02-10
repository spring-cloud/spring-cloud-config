/*
 * Copyright 2018-2022 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
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
	public void configDataContextFailsFast(CapturedOutput output) {
		assertThatThrownBy(() -> {
			new SpringApplicationBuilder().sources(Application.class, PropertyInjectionConfiguration.class).run(
					"--server.port=0", "--spring.cloud.config.enabled=true", "--spring.cloud.config.fail-fast=true",
					"--spring.config.import=optional:configserver:http://serverhostdoesnotexist:1234",
					"--spring.cloud.config.server.enabled=false", "--logging.level.org.springframework.retry=TRACE",
					"--logging.level.org.springframework.cloud.config=TRACE",
					"--logging.level.org.springframework.boot.context.config=TRACE");
		}).as("Exception not caused by fail fast").hasMessageContaining("fail fast");
		assertThat(output).contains("Retry: count=5").doesNotContain("Could not resolve placeholder");
	}

	@Component
	private static class PropertyInjectionConfiguration {

		@Value("${some.property}")
		private String someProperty;

	}

}
