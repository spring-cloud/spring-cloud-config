/*
 * Copyright 2016-2019 the original author or authors.
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

import javax.sql.DataSource;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.environment.JdbcEnvironmentRepositoryTests.ApplicationConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ApplicationConfiguration.class,
		properties = { "spring.datasource.schema=classpath:schema-jdbc.sql",
				"spring.datasource.data=classpath:data-jdbc.sql" })
@AutoConfigureTestDatabase
@DirtiesContext
public class JdbcEnvironmentRepositoryTests {

	@Autowired
	private DataSource dataSource;

	@Test
	public void basicProperties() {
		Environment env = new JdbcEnvironmentRepository(new JdbcTemplate(this.dataSource),
				new JdbcEnvironmentProperties()).findOne("foo", "bar", "");
		assertThat(env.getName()).isEqualTo("foo");
		assertThat(env.getProfiles()).isEqualTo(new String[] { "default", "bar" });
		assertThat(env.getLabel()).isEqualTo("master");
		assertThat(env.getPropertySources()).isNotEmpty();
		assertThat(env.getPropertySources().get(0).getName()).isEqualTo("foo-bar");
		assertThat(env.getPropertySources().get(0).getSource().get("a.b.c"))
				.isEqualTo("x");
		assertThat(env.getPropertySources().get(1).getName())
				.isEqualTo("application-default");
		assertThat(env.getPropertySources().get(1).getSource().get("a.b")).isEqualTo("y");
	}

	@Test
	public void defaults() {
		Environment env = new JdbcEnvironmentRepository(new JdbcTemplate(this.dataSource),
				new JdbcEnvironmentProperties()).findOne("application", "", "");
		assertThat(env.getName()).isEqualTo("application");
		assertThat(env.getProfiles()).isEqualTo(new String[] { "default" });
		assertThat(env.getLabel()).isEqualTo("master");
		assertThat(env.getPropertySources()).isNotEmpty();
		assertThat(env.getPropertySources().get(0).getSource().get("a.b")).isEqualTo("y");
	}

	@Configuration(proxyBeanMethods = false)
	protected static class ApplicationConfiguration {

	}

}
