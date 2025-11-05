/*
 * Copyright 2018-present the original author or authors.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.mongodb.MongoException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.util.StringUtils;

/**
 * @author Alexandros Pappas
 */
public class MongoDbEnvironmentRepository implements EnvironmentRepository, Ordered {

	private static final Log logger = LogFactory.getLog(JdbcEnvironmentRepository.class);

	private final MongoTemplate mongoTemplate;

	private final MongoDbEnvironmentProperties properties;

	public MongoDbEnvironmentRepository(MongoTemplate mongoTemplate, MongoDbEnvironmentProperties properties) {
		this.mongoTemplate = mongoTemplate;
		this.properties = properties;
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		label = StringUtils.hasText(label) ? label : this.properties.getDefaultLabel();
		profile = StringUtils.hasText(profile) ? profile : "default";

		// Prepare the environment with applications and profiles
		String[] profilesArray = StringUtils.commaDelimitedListToStringArray(profile);
		Environment environment = new Environment(application, profilesArray, label, null, null);

		// Prepend "application," to config if not already present
		String config = application.startsWith("application") ? application : "application," + application;

		List<String> applications = Arrays.stream(StringUtils.commaDelimitedListToStringArray(config))
			.distinct()
			.collect(Collectors.toList());
		List<String> profiles = Arrays.stream(profilesArray).distinct().collect(Collectors.toList());

		// Reverse for the intended processing order
		Collections.reverse(applications);
		Collections.reverse(profiles);

		// Add property sources for each combination of application and profile
		for (String env : profiles) {
			for (String app : applications) {
				addPropertySource(environment, app, env, label);
			}
		}
		// add properties without profile, equivalent to foo.yml, application.yml
		for (String app : applications) {
			addPropertySource(environment, app, null, label);
		}
		return environment;
	}

	private void addPropertySource(Environment environment, String application, String profile, String label) {
		try {
			Criteria criteria = Criteria.where("application").is(application).and("label").is(label);
			if (profile != null) {
				criteria = criteria.and("profile").is(profile);
			}
			else {
				// Handling properties without profile by explicitly looking for them
				criteria = criteria.andOperator(Criteria.where("profile").is(null));
			}

			Query query = new Query(criteria);
			List<Map> propertyMaps = this.mongoTemplate.find(query, Map.class, this.properties.getCollection());

			for (Map propertyMap : propertyMaps) {
				String propertySourceName = (profile != null) ? application + "-" + profile : application;
				@SuppressWarnings("unchecked")
				Map<String, Object> source = (Map<String, Object>) propertyMap.get("properties");
				if (source != null && !source.isEmpty()) {
					environment.add(new PropertySource(propertySourceName, source));
				}
			}
		}
		catch (DataAccessException | MongoException e) {
			if (!this.properties.isFailOnError()) {
				if (logger.isDebugEnabled()) {
					logger.debug("Failed to retrieve configuration from MongoDB", e);
				}
			}
			else {
				throw e;
			}
		}
	}

	@Override
	public int getOrder() {
		return this.properties.getOrder();
	}

}
