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
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests for {@link ConfigResourceClientAutoConfiguration}.
 *
 * @author Sharang Gupta
 */
public class ConfigResourceClientAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(
			AutoConfigurations.of(ConfigClientAutoConfiguration.class, ConfigResourceClientAutoConfiguration.class));

	@Test
	void noClientWithoutTheConfigServerConnection() {
		this.contextRunner.run(context -> assertThat(context).doesNotHaveBean(ConfigResourceClient.class));
	}

	@Test
	void clientUsesTheConfigServerConnection() {
		this.contextRunner
			.withBean(ConfigServerRestClientHolder.class, () -> new ConfigServerRestClientHolder(RestClient.create()))
			.run(context -> assertThat(context).hasSingleBean(ConfigResourceClient.class)
				.getBean(ConfigResourceClient.class)
				.isInstanceOf(DefaultConfigResourceClient.class));
	}

	@Test
	void userDefinedClientIsKept() {
		ConfigResourceClient custom = new DefaultConfigResourceClient(RestClient.create(),
				new ConfigClientProperties(new MockEnvironment()));
		this.contextRunner
			.withBean(ConfigServerRestClientHolder.class, () -> new ConfigServerRestClientHolder(RestClient.create()))
			.withBean(ConfigResourceClient.class, () -> custom)
			.run(context -> assertThat(context).getBean(ConfigResourceClient.class).isSameAs(custom));
	}

	@Test
	void propertiesOfTheConfigServerImportTakePrecedence() {
		RestTemplate restTemplate = new RestTemplate();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("http://imported:8888/application/default/nginx.conf?useDefaultLabel"))
			.andRespond(withSuccess("server { }", MediaType.TEXT_PLAIN));
		this.contextRunner
			.withBean(ConfigServerRestClientHolder.class,
					() -> new ConfigServerRestClientHolder(RestClient.create(restTemplate)))
			.withBean(ConfigServerConfigDataLocationResolver.PROPERTIES_BEAN_NAME, ConfigClientProperties.class,
					() -> properties("http://imported:8888"))
			.withBean("configClientProperties", ConfigClientProperties.class, () -> properties("http://bound:8888"))
			.run(context -> {
				assertThat(context).getBeans(ConfigClientProperties.class).hasSize(2);
				context.getBean(ConfigResourceClient.class).getPlainTextResource("nginx.conf");
				server.verify();
			});
	}

	private static ConfigClientProperties properties(String uri) {
		ConfigClientProperties properties = new ConfigClientProperties(new MockEnvironment());
		properties.setUri(new String[] { uri });
		return properties;
	}

	@Test
	void noClientWhenTheConfigClientIsDisabled() {
		this.contextRunner
			.withBean(ConfigServerRestClientHolder.class, () -> new ConfigServerRestClientHolder(RestClient.create()))
			.withPropertyValues("spring.cloud.config.enabled=false")
			.run(context -> assertThat(context).doesNotHaveBean(ConfigResourceClient.class));
	}

}
