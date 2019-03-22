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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.util.StringUtils;

/**
 * An {@link EnvironmentRepository} that picks up data from a relational database. The
 * database should have a table called "PROPERTIES" with columns "APPLICATION", "PROFILE",
 * "LABEL" (with the usual {@link Environment} meaning), plus "KEY" and "VALUE" for the
 * key and value pairs in {@link Properties} style. Property values behave in the same way
 * as they would if they came from Spring Boot properties files named
 * <code>{application}-{profile}.properties</code>, including all the encryption and
 * decryption, which will be applied as post-processing steps (i.e. not in this repository
 * directly).
 *
 * @author Dave Syer
 *
 */
public class JdbcEnvironmentRepository implements EnvironmentRepository, Ordered {

	private final JdbcTemplate jdbc;

	private final PropertiesResultSetExtractor extractor = new PropertiesResultSetExtractor();

	private int order;

	private String sql;

	public JdbcEnvironmentRepository(JdbcTemplate jdbc,
			JdbcEnvironmentProperties properties) {
		this.jdbc = jdbc;
		this.order = properties.getOrder();
		this.sql = properties.getSql();
	}

	public String getSql() {
		return this.sql;
	}

	public void setSql(String sql) {
		this.sql = sql;
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		String config = application;
		if (StringUtils.isEmpty(label)) {
			label = "master";
		}
		if (StringUtils.isEmpty(profile)) {
			profile = "default";
		}
		if (!profile.startsWith("default")) {
			profile = "default," + profile;
		}
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		Environment environment = new Environment(application, profiles, label, null,
				null);
		if (!config.startsWith("application")) {
			config = "application," + config;
		}
		List<String> applications = new ArrayList<String>(new LinkedHashSet<>(
				Arrays.asList(StringUtils.commaDelimitedListToStringArray(config))));
		List<String> envs = new ArrayList<String>(
				new LinkedHashSet<>(Arrays.asList(profiles)));
		Collections.reverse(applications);
		Collections.reverse(envs);
		for (String app : applications) {
			for (String env : envs) {
				Map<String, String> next = (Map<String, String>) this.jdbc.query(this.sql,
						new Object[] { app, env, label }, this.extractor);
				if (!next.isEmpty()) {
					environment.add(new PropertySource(app + "-" + env, next));
				}
			}
		}
		return environment;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

}

class PropertiesResultSetExtractor implements ResultSetExtractor<Map<String, String>> {

	@Override
	public Map<String, String> extractData(ResultSet rs)
			throws SQLException, DataAccessException {
		Map<String, String> map = new LinkedHashMap<>();
		while (rs.next()) {
			map.put(rs.getString(1), rs.getString(2));
		}
		return map;
	}

}
