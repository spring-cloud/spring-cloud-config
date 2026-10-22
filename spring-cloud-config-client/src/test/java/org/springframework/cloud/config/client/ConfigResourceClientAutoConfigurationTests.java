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

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ConfigResourceClientAutoConfiguration}.
 *
 * @author Sharang Gupta
 */
public class ConfigResourceClientAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(
			AutoConfigurations.of(ConfigClientAutoConfiguration.class, ConfigResourceClientAutoConfiguration.class));

	@Test
	void noClientWithoutTheConfigClientRestClient() {
		this.contextRunner.run(context -> assertThat(context).doesNotHaveBean(ConfigResourceClient.class));
	}

	@Test
	void clientUsesTheConfigClientRestClient() {
		this.contextRunner
			.withBean(ConfigServerConfigDataLocationResolver.REST_CLIENT_BEAN_NAME, RestClient.class,
					RestClient::create)
			.run(context -> assertThat(context).hasSingleBean(ConfigResourceClient.class)
				.getBean(ConfigResourceClient.class)
				.isInstanceOf(DefaultConfigResourceClient.class));
	}

	@Test
	void userDefinedClientIsKept() {
		ConfigResourceClient custom = new DefaultConfigResourceClient(RestClient.create(),
				new ConfigClientProperties(new org.springframework.mock.env.MockEnvironment()));
		this.contextRunner
			.withBean(ConfigServerConfigDataLocationResolver.REST_CLIENT_BEAN_NAME, RestClient.class,
					RestClient::create)
			.withBean(ConfigResourceClient.class, () -> custom)
			.run(context -> assertThat(context).getBean(ConfigResourceClient.class).isSameAs(custom));
	}

	@Test
	void noClientWhenTheConfigClientIsDisabled() {
		this.contextRunner
			.withBean(ConfigServerConfigDataLocationResolver.REST_CLIENT_BEAN_NAME, RestClient.class,
					RestClient::create)
			.withPropertyValues("spring.cloud.config.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(ConfigResourceClient.class));
	}

}
