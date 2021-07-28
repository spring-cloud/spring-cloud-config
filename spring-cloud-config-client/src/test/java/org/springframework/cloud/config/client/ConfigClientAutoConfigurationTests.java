/*
 * Copyright 2002-2019 the original author or authors.
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

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.config.client.validation.InvalidApplicationNameException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConfigClientAutoConfigurationTests {

	@Test
	public void sunnyDay() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				ConfigClientAutoConfiguration.class);
		assertThat(BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context, ConfigClientProperties.class).length)
				.isEqualTo(1);
		context.close();
	}

	@Test
	public void withParent() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(ConfigClientAutoConfiguration.class)
				.child(Object.class).web(WebApplicationType.NONE).properties("spring.cloud.bootstrap.enabled=true")
				.run();
		assertThat(BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context, ConfigClientProperties.class).length)
				.isEqualTo(1);
		context.close();
	}

	@Test
	public void invalidApplicationNameOverrideWithFailFastEnabledFailsToStartup() {
		SpringApplication application = new SpringApplicationBuilder(ConfigClientAutoConfiguration.class)
				.web(WebApplicationType.NONE).properties("spring.cloud.config.fail-fast=true",
						"spring.cloud.bootstrap.enabled=true", "spring.cloud.config.name=application-service")
				.application();

		assertThatThrownBy(application::run).isInstanceOf(InvalidApplicationNameException.class).extracting("value")
				.isEqualTo("application-service");
	}

	@Test
	public void invalidApplicationNameOverrideWithFailFastDisabledStartsUpButNoConfigServerPropertiesAreLoaded() {
		SpringApplication application = new SpringApplicationBuilder(ConfigClientAutoConfiguration.class)
				.web(WebApplicationType.NONE)
				.properties("spring.cloud.config.name=application-service", "spring.cloud.bootstrap.enabled=true")
				.application();

		ConfigurableApplicationContext context = application.run();

		assertThat(context.getEnvironment().getPropertySources().get("configService")).isNull();

		context.close();
	}

	@Test
	public void invalidApplicationNameWithFailFastEnabledFailsToStartup() {
		SpringApplication application = new SpringApplicationBuilder(ConfigClientAutoConfiguration.class)
				.web(WebApplicationType.NONE).properties("spring.cloud.config.fail-fast=true",
						"spring.cloud.bootstrap.enabled=true", "spring.application.name=application-service")
				.application();

		assertThatThrownBy(application::run).isInstanceOf(InvalidApplicationNameException.class).extracting("value")
				.isEqualTo("application-service");
	}

	@Test
	public void invalidApplicationNameWithFailFastDisabledStartsUpButNoConfigServerPropertiesAreLoaded() {
		SpringApplication application = new SpringApplicationBuilder(ConfigClientAutoConfiguration.class)
				.web(WebApplicationType.NONE)
				.properties("spring.application.name=application-service", "spring.cloud.bootstrap.enabled=true")
				.application();

		ConfigurableApplicationContext context = application.run();

		assertThat(context.getEnvironment().getPropertySources().get("configService")).isNull();

		context.close();
	}

}
