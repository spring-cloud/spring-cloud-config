/*
 * Copyright 2016-2020 the original author or authors.
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

package org.springframework.cloud.config.server.environment;

import java.io.IOException;

import org.junit.Test;

import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.ContextConsumer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.cloud.config.server.ConfigServerApplication;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests to verify JdbcEnvironmentRepository configuration.
 *
 * @author Thomas Vitale
 */
public class JdbcEnvironmentRepositoryConfigurationTests {

	@Test
	public void jdbcEnvironmentRepositoryBeansConfiguredWhenDefault() throws IOException {
		new WebApplicationContextRunner()
				.withUserConfiguration(ConfigServerApplication.class)
				.withPropertyValues("spring.profiles.active=test,jdbc",
						"spring.main.web-application-type=none")
				.run(context -> {
					assertThat(context)
							.hasSingleBean(JdbcEnvironmentRepositoryFactory.class);
					assertThat(context).hasSingleBean(JdbcEnvironmentRepository.class);
				});
	}

	@Test
	public void jdbcEnvironmentRepositoryBeansConfiguredWhenEnabled() throws IOException {
		getApplicationContextWithJdbcEnabled(true, context -> {
			assertThat(context).hasSingleBean(JdbcEnvironmentRepositoryFactory.class);
			assertThat(context).hasSingleBean(JdbcEnvironmentRepository.class);
		});
	}

	@Test
	public void jdbcEnvironmentRepositoryFactoryNotConfiguredWhenDisabled()
			throws IOException {
		getApplicationContextWithJdbcEnabled(false, context -> assertThat(context)
				.doesNotHaveBean(JdbcEnvironmentRepositoryFactory.class));
	}

	@Test
	public void jdbcEnvironmentRepositoryNotConfiguredWhenDisabled() throws IOException {
		getApplicationContextWithJdbcEnabled(false, context -> assertThat(context)
				.doesNotHaveBean(JdbcEnvironmentRepository.class));
	}

	private void getApplicationContextWithJdbcEnabled(boolean jdbcEnabled,
			ContextConsumer<? super AssertableWebApplicationContext> consumer)
			throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo();
		new WebApplicationContextRunner()
				.withUserConfiguration(ConfigServerApplication.class)
				.withPropertyValues("spring.profiles.active=test,jdbc",
						"spring.main.web-application-type=none",
						"spring.cloud.config.server.git.uri:" + uri,
						"spring.cloud.config.server.jdbc.enabled:" + jdbcEnabled)
				.run(consumer);
	}

}
