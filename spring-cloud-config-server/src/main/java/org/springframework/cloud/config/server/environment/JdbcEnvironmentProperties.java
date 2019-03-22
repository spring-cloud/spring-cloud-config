/*
 * Copyright 2018-2019 the original author or authors.
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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.config.server.support.EnvironmentRepositoryProperties;
import org.springframework.core.Ordered;

/**
 * @author Dylan Roberts
 */
@ConfigurationProperties("spring.cloud.config.server.jdbc")
public class JdbcEnvironmentProperties implements EnvironmentRepositoryProperties {

	private static final String DEFAULT_SQL = "SELECT KEY, VALUE from PROPERTIES"
			+ " where APPLICATION=? and PROFILE=? and LABEL=?";

	private int order = Ordered.LOWEST_PRECEDENCE - 10;

	/** SQL used to query database for keys and values. */
	private String sql = DEFAULT_SQL;

	public int getOrder() {
		return this.order;
	}

	@Override
	public void setOrder(int order) {
		this.order = order;
	}

	public String getSql() {
		return this.sql;
	}

	public void setSql(String sql) {
		this.sql = sql;
	}

}
