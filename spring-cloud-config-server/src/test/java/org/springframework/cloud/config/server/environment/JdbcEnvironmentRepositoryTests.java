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

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.environment.JdbcEnvironmentRepositoryTests.ApplicationConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Dave Syer
 *
 */
@SpringBootTest(classes = ApplicationConfiguration.class,
		properties = { "logging.level.root=debug", "spring.sql.init.schema-locations=classpath:schema-jdbc.sql",
				"spring.sql.init.data-locations=classpath:data-jdbc.sql" })
@AutoConfigureTestDatabase
@DirtiesContext
public class JdbcEnvironmentRepositoryTests {

	@Autowired
	private DataSource dataSource;

	@Test
	public void basicProperties() {
		JdbcEnvironmentProperties properties = new JdbcEnvironmentProperties();
		Environment env = new JdbcEnvironmentRepository(new JdbcTemplate(this.dataSource), properties,
				new JdbcEnvironmentRepository.PropertiesResultSetExtractor()).findOne("foo", "bar", "");
		assertThat(env.getName()).isEqualTo("foo");
		assertThat(env.getProfiles()).isEqualTo(new String[] { "bar" });
		assertThat(env.getLabel()).isEqualTo("master");
		assertThat(env.getPropertySources()).isNotEmpty();
		assertThat(env.getPropertySources().get(0).getName()).isEqualTo("foo-bar");
		assertThat(env.getPropertySources().get(0).getSource().get("a.b.c")).isEqualTo("foo-bar");
		assertThat(env.getPropertySources().get(1).getName()).isEqualTo("application-bar");
		assertThat(env.getPropertySources().get(1).getSource().get("a.b.c")).isEqualTo("application-bar");
		assertThat(env.getPropertySources().get(2).getName()).isEqualTo("foo");
		assertThat(env.getPropertySources().get(2).getSource().get("a.b.c")).isEqualTo("foo-null");
		assertThat(env.getPropertySources().get(3).getName()).isEqualTo("application");
		assertThat(env.getPropertySources().get(3).getSource().get("a.b.c")).isEqualTo("application-null");
	}

	@Test
	public void testDefaultProfile() {
		JdbcEnvironmentProperties properties = new JdbcEnvironmentProperties();
		Environment env = new JdbcEnvironmentRepository(new JdbcTemplate(this.dataSource), properties,
				new JdbcEnvironmentRepository.PropertiesResultSetExtractor()).findOne("foo", "", "");
		assertThat(env.getName()).isEqualTo("foo");
		assertThat(env.getProfiles()).isEqualTo(new String[] { "default" });
		assertThat(env.getLabel()).isEqualTo("master");
		assertThat(env.getPropertySources()).isNotEmpty();
		assertThat(env.getPropertySources().get(0).getName()).isEqualTo("foo-default");
		assertThat(env.getPropertySources().get(0).getSource().get("a.b.c")).isEqualTo("foo-default");
		assertThat(env.getPropertySources().get(1).getName()).isEqualTo("application-default");
		assertThat(env.getPropertySources().get(1).getSource().get("a.b.c")).isEqualTo("application-default");
		assertThat(env.getPropertySources().get(2).getName()).isEqualTo("foo");
		assertThat(env.getPropertySources().get(2).getSource().get("a.b.c")).isEqualTo("foo-null");
		assertThat(env.getPropertySources().get(3).getName()).isEqualTo("application");
		assertThat(env.getPropertySources().get(3).getSource().get("a.b.c")).isEqualTo("application-null");
	}

	@Test
	public void testProfileNotExist() {
		JdbcEnvironmentProperties properties = new JdbcEnvironmentProperties();
		Environment env = new JdbcEnvironmentRepository(new JdbcTemplate(this.dataSource), properties,
				new JdbcEnvironmentRepository.PropertiesResultSetExtractor()).findOne("foo", "not_exist", "");
		assertThat(env.getName()).isEqualTo("foo");
		assertThat(env.getProfiles()).isEqualTo(new String[] { "not_exist" });
		assertThat(env.getLabel()).isEqualTo("master");
		assertThat(env.getPropertySources()).isNotEmpty();
		assertThat(env.getPropertySources().get(0).getName()).isEqualTo("foo");
		assertThat(env.getPropertySources().get(0).getSource().get("a.b.c")).isEqualTo("foo-null");
		assertThat(env.getPropertySources().get(1).getName()).isEqualTo("application");
		assertThat(env.getPropertySources().get(1).getSource().get("a.b.c")).isEqualTo("application-null");
	}

	@Test
	public void testApplicationNotExist() {
		JdbcEnvironmentProperties properties = new JdbcEnvironmentProperties();
		Environment env = new JdbcEnvironmentRepository(new JdbcTemplate(this.dataSource), properties,
				new JdbcEnvironmentRepository.PropertiesResultSetExtractor()).findOne("not_exist", "bar", "");
		assertThat(env.getName()).isEqualTo("not_exist");
		assertThat(env.getProfiles()).isEqualTo(new String[] { "bar" });
		assertThat(env.getLabel()).isEqualTo("master");
		assertThat(env.getPropertySources()).isNotEmpty();
		assertThat(env.getPropertySources().get(0).getName()).isEqualTo("application-bar");
		assertThat(env.getPropertySources().get(0).getSource().get("a.b.c")).isEqualTo("application-bar");
		assertThat(env.getPropertySources().get(1).getName()).isEqualTo("application");
		assertThat(env.getPropertySources().get(1).getSource().get("a.b.c")).isEqualTo("application-null");
	}

	@Test
	public void testApplicationProfileBothNotExist() {
		JdbcEnvironmentProperties properties = new JdbcEnvironmentProperties();
		Environment env = new JdbcEnvironmentRepository(new JdbcTemplate(this.dataSource), properties,
				new JdbcEnvironmentRepository.PropertiesResultSetExtractor()).findOne("not_exist", "not_exist", "");
		assertThat(env.getName()).isEqualTo("not_exist");
		assertThat(env.getProfiles()).isEqualTo(new String[] { "not_exist" });
		assertThat(env.getLabel()).isEqualTo("master");
		assertThat(env.getPropertySources()).isNotEmpty();
		assertThat(env.getPropertySources().get(0).getName()).isEqualTo("application");
		assertThat(env.getPropertySources().get(0).getSource().get("a.b.c")).isEqualTo("application-null");
	}

	@Test
	public void testCustomSql() {
		JdbcEnvironmentProperties properties = new JdbcEnvironmentProperties();
		properties.setSql("SELECT MY_KEY, MY_VALUE from MY_PROPERTIES where APPLICATION=? and PROFILE=? and LABEL=?");
		properties.setSqlWithoutProfile(
				"SELECT MY_KEY, MY_VALUE from MY_PROPERTIES where APPLICATION=? and PROFILE is null and LABEL=?");
		Environment env = new JdbcEnvironmentRepository(new JdbcTemplate(this.dataSource), properties,
				new JdbcEnvironmentRepository.PropertiesResultSetExtractor()).findOne("foo", "bar", "");
		assertThat(env.getName()).isEqualTo("foo");
		assertThat(env.getProfiles()).isEqualTo(new String[] { "bar" });
		assertThat(env.getLabel()).isEqualTo("master");
		assertThat(env.getPropertySources()).isNotEmpty();
		assertThat(env.getPropertySources().get(0).getName()).isEqualTo("foo-bar");
		assertThat(env.getPropertySources().get(0).getSource().get("a.b.c")).isEqualTo("foo-bar");
		assertThat(env.getPropertySources().get(1).getName()).isEqualTo("application-bar");
		assertThat(env.getPropertySources().get(1).getSource().get("a.b.c")).isEqualTo("application-bar");
		assertThat(env.getPropertySources().get(2).getName()).isEqualTo("foo");
		assertThat(env.getPropertySources().get(2).getSource().get("a.b.c")).isEqualTo("foo-null");
		assertThat(env.getPropertySources().get(3).getName()).isEqualTo("application");
		assertThat(env.getPropertySources().get(3).getSource().get("a.b.c")).isEqualTo("application-null");
	}

	@Test
	public void testIncompleteConfig() {
		JdbcEnvironmentProperties properties = new JdbcEnvironmentProperties();
		properties.setSql("SELECT MY_KEY, MY_VALUE from MY_PROPERTIES where APPLICATION=? and PROFILE=? and LABEL=?");
		Environment env = new JdbcEnvironmentRepository(new JdbcTemplate(this.dataSource), properties,
				new JdbcEnvironmentRepository.PropertiesResultSetExtractor()).findOne("foo", "bar", "");
		assertThat(env.getName()).isEqualTo("foo");
		assertThat(env.getProfiles()).isEqualTo(new String[] { "default", "bar" });
		assertThat(env.getLabel()).isEqualTo("master");
		assertThat(env.getPropertySources()).isNotEmpty();
		assertThat(env.getPropertySources().get(0).getName()).isEqualTo("foo-bar");
		assertThat(env.getPropertySources().get(0).getSource().get("a.b.c")).isEqualTo("foo-bar");
		assertThat(env.getPropertySources().get(1).getName()).isEqualTo("application-bar");
		assertThat(env.getPropertySources().get(1).getSource().get("a.b.c")).isEqualTo("application-bar");
		assertThat(env.getPropertySources().get(2).getName()).isEqualTo("foo-default");
		assertThat(env.getPropertySources().get(2).getSource().get("a.b.c")).isEqualTo("foo-default");
		assertThat(env.getPropertySources().get(3).getName()).isEqualTo("application-default");
		assertThat(env.getPropertySources().get(3).getSource().get("a.b.c")).isEqualTo("application-default");
	}

	@Test
	public void testNotFailOnError() {
		JdbcEnvironmentProperties properties = new JdbcEnvironmentProperties();
		properties.setFailOnError(false);
		// when sql is customized but forgot to customize sqlWithoutProfile then
		// sqlWithoutProfile should fail but sql with profile should still working when
		// failOnError is off
		properties.setSql("SELECT MY_KEY, MY_VALUE from MY_PROPERTIES where APPLICATION=? and PROFILE=? and LABEL=?");
		properties.setSqlWithoutProfile(
				"SELECT SHOULD_FAIL from TABLE_NOTEXIST where APPLICATION=? and PROFILE is null and LABEL=?");
		Environment env = new JdbcEnvironmentRepository(new JdbcTemplate(this.dataSource), properties,
				new JdbcEnvironmentRepository.PropertiesResultSetExtractor()).findOne("foo", "bar", "");
		assertThat(env.getName()).isEqualTo("foo");
		assertThat(env.getProfiles()).isEqualTo(new String[] { "bar" });
		assertThat(env.getLabel()).isEqualTo("master");
		assertThat(env.getPropertySources()).isNotEmpty();
		assertThat(env.getPropertySources().get(0).getName()).isEqualTo("foo-bar");
		assertThat(env.getPropertySources().get(0).getSource().get("a.b.c")).isEqualTo("foo-bar");
		assertThat(env.getPropertySources().get(1).getName()).isEqualTo("application-bar");
		assertThat(env.getPropertySources().get(1).getSource().get("a.b.c")).isEqualTo("application-bar");
	}

	@Test
	public void testFailOnError() {
		JdbcEnvironmentProperties properties = new JdbcEnvironmentProperties();
		properties.setSqlWithoutProfile(
				"SELECT SHOULD_FAIL from TABLE_NOTEXIST where APPLICATION=? and PROFILE is null and LABEL=?");
		JdbcEnvironmentRepository repository = new JdbcEnvironmentRepository(new JdbcTemplate(this.dataSource),
				properties);
		assertThatThrownBy(() -> repository.findOne("foo", "bar", "")).isInstanceOf(DataAccessException.class);
	}

	@Test
	public void testCustomLabel() {
		JdbcEnvironmentProperties properties = new JdbcEnvironmentProperties();
		properties.setDefaultLabel("main");
		Environment env = new JdbcEnvironmentRepository(new JdbcTemplate(this.dataSource), properties,
				new JdbcEnvironmentRepository.PropertiesResultSetExtractor()).findOne("foo", "bar", "");
		assertThat(env.getName()).isEqualTo("foo");
		assertThat(env.getProfiles()).isEqualTo(new String[] { "bar" });
		assertThat(env.getLabel()).isEqualTo("main");
		assertThat(env.getPropertySources()).isNotEmpty();
		assertThat(env.getPropertySources().get(0).getName()).isEqualTo("foo-bar");
		assertThat(env.getPropertySources().get(0).getSource().get("a.b.c")).isEqualTo("foo-bar");
		assertThat(env.getPropertySources().get(1).getName()).isEqualTo("application-bar");
		assertThat(env.getPropertySources().get(1).getSource().get("a.b.c")).isEqualTo("application-bar");
	}

	@ImportAutoConfiguration(SqlInitializationAutoConfiguration.class)
	@Configuration(proxyBeanMethods = false)
	protected static class ApplicationConfiguration {

	}

}
