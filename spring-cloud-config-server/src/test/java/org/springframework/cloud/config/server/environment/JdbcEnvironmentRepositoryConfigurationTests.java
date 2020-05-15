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

import org.junit.After;
import org.junit.Test;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.config.server.ConfigServerApplication;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests to verify JdbcEnvironmentRepository configuration.
 *
 * @author Thomas Vitale
 */
public class JdbcEnvironmentRepositoryConfigurationTests {

	private ConfigurableApplicationContext context;

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void jdbcEnvironmentRepositoryBeansConfiguredWhenDefault() throws IOException {
		this.context = new SpringApplicationBuilder(ConfigServerApplication.class)
				.web(WebApplicationType.NONE).profiles("test", "jdbc")
				.run();
		assertThat(this.context.getBean(JdbcEnvironmentRepositoryFactory.class)).isNotNull();
		assertThat(this.context.getBean(JdbcEnvironmentRepository.class)).isNotNull();
	}

	@Test
	public void jdbcEnvironmentRepositoryBeansConfiguredWhenEnabled() throws IOException {
		this.context = getApplicationContextWithJdbcEnabled(true);
		assertThat(this.context.getBean(JdbcEnvironmentRepositoryFactory.class)).isNotNull();
		assertThat(this.context.getBean(JdbcEnvironmentRepository.class)).isNotNull();
	}

	@Test(expected = NoSuchBeanDefinitionException.class)
	public void jdbcEnvironmentRepositoryFactoryNotConfiguredWhenDisabled()
			throws IOException {
		this.context = getApplicationContextWithJdbcEnabled(false);
		JdbcEnvironmentRepositoryFactory repositoryFactory = this.context
				.getBean(JdbcEnvironmentRepositoryFactory.class);
	}

	@Test(expected = NoSuchBeanDefinitionException.class)
	public void jdbcEnvironmentRepositoryNotConfiguredWhenDisabled() throws IOException {
		this.context = getApplicationContextWithJdbcEnabled(false);
		JdbcEnvironmentRepository repository = this.context
				.getBean(JdbcEnvironmentRepository.class);
	}

	private ConfigurableApplicationContext getApplicationContextWithJdbcEnabled(
			boolean jdbcEnabled) throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo();
		return new SpringApplicationBuilder(ConfigServerApplication.class)
				.web(WebApplicationType.NONE).profiles("test", "jdbc")
				.properties("spring.cloud.config.server.git.uri:" + uri)
				.properties("spring.cloud.config.server.jdbc.enabled:" + jdbcEnabled)
				.run();
	}

}
