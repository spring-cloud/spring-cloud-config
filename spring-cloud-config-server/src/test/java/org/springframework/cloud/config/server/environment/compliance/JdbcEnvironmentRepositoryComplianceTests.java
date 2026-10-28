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

package org.springframework.cloud.config.server.environment.compliance;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.environment.JdbcEnvironmentProperties;
import org.springframework.cloud.config.server.environment.JdbcEnvironmentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/**
 * Compliance tests for {@link JdbcEnvironmentRepository} using an embedded H2 database.
 *
 * @author Ryan Baxter
 */
class JdbcEnvironmentRepositoryComplianceTests extends EnvironmentRepositoryComplianceTests {

	private static EmbeddedDatabase database;

	private final Fixture fixture = new Fixture();

	@BeforeAll
	static void startDatabase() {
		database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
			.generateUniqueName(true)
			.addScript("schema-jdbc.sql")
			.build();
	}

	@AfterAll
	static void stopDatabase() {
		database.shutdown();
	}

	@Override
	protected EnvironmentRepositoryFixture fixture() {
		return this.fixture;
	}

	@Override
	@Test
	@Disabled("TODO-issue: Shared application properties are skipped when the application name starts with application")
	public void applicationNameStartingWithApplicationIncludesSharedFiles() throws Exception {
		super.applicationNameStartingWithApplicationIncludesSharedFiles();
	}

	static class Fixture implements EnvironmentRepositoryFixture {

		private final JdbcTemplate jdbc = new JdbcTemplate(database);

		private final JdbcEnvironmentProperties properties = new JdbcEnvironmentProperties();

		@Override
		public void reset() {
			this.jdbc.update("DELETE FROM PROPERTIES");
		}

		@Override
		public void store(ConfigFile file) {
			String label = file.label() != null ? file.label() : this.properties.getDefaultLabel();
			file.properties()
				.forEach((key, value) -> this.jdbc.update(
						"INSERT INTO PROPERTIES (\"KEY\", \"VALUE\", APPLICATION, PROFILE, LABEL) VALUES (?, ?, ?, ?, ?)",
						key, value, file.application(), file.profile(), label));
		}

		@Override
		public EnvironmentRepository repository() {
			return new JdbcEnvironmentRepository(this.jdbc, this.properties,
					new JdbcEnvironmentRepository.PropertiesResultSetExtractor());
		}

		@Override
		public Set<Capability> capabilities() {
			return EnumSet.of(Capability.LABELS, Capability.MULTIPLE_LABELS, Capability.MULTIPLE_APPLICATIONS,
					Capability.DISTINCT_DEFAULT_PROFILE);
		}

	}

}
