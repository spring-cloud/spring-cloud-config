/*
 * Copyright 2015-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.config.YamlProcessor;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.util.StringUtils;

/**
 * Simple implementation of {@link EnvironmentRepository} that is backed by
 * MongoDB. The resulting Environment is composed of property sources located
 * using the application name as the MongoDB collection while MongoDB document's
 * 'profile' and 'label' key values represent the Spring profile and label
 * respectively.
 *
 * @author Venil Noronha
 */
public class MongoEnvironmentRepository implements EnvironmentRepository {

	private static final String ID = "_id";
	private static final String LABEL = "label";
	private static final String PROFILE = "profile";
	private static final String DEFAULT_PROFILE = "default";

	private MongoTemplate mongoTemplate;
	private MapFlattener mapFlattener;

	public void setMongoTemplate(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
		this.mapFlattener = new MapFlattener();
	}

	@Override
	public Environment findOne(String name, String profile, String label) {
		String[] profilesArr = StringUtils.commaDelimitedListToStringArray(profile);
		final List<String> profiles = Arrays.asList(profilesArr.clone());
		for (int i = 0; i < profiles.size(); i++) {
			String currProfile = profiles.get(i);
			if (DEFAULT_PROFILE.equals(currProfile)) {
				profiles.set(i, null); // 'null' profile value in MongoDB is considered default
				break;
			}
		}
		Query query = new Query().addCriteria(Criteria.where(PROFILE).in(profiles.toArray()));
		if (label != null) {
			query.addCriteria(Criteria.where(LABEL).is(label));
		}
		Environment environment;
		try {
			List<MongoPropertySource> sources = mongoTemplate.find(query, MongoPropertySource.class, name);
			sortSourcesByProfileOrder(sources, profiles);
			environment = new Environment(name, profilesArr, label, null);
			for (MongoPropertySource source : sources) {
				String sourceName = generatePropertySourceName(name, source);
				cleanSource(source);
				Map<String, Object> pureSource = mapFlattener.flatten(source);
				PropertySource propSource = new PropertySource(sourceName, pureSource);
				environment.add(propSource);
			}
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot load environment", e);
		}
		return environment;
	}

	private void sortSourcesByProfileOrder(List<MongoPropertySource> sources,
			final List<String> profiles) {
		Collections.sort(sources, new Comparator<MongoPropertySource>() {
			@Override
			public int compare(MongoPropertySource s1, MongoPropertySource s2) {
				Object p1 = s1.get(PROFILE);
				Object p2 = s2.get(PROFILE);
				int i1 = profiles.indexOf(p1 != null ? p1 : DEFAULT_PROFILE);
				int i2 = profiles.indexOf(p2 != null ? p2 : DEFAULT_PROFILE);
				return Double.compare(i1, i2);
			}
		});
	}

	private String generatePropertySourceName(String environmentName, MongoPropertySource source) {
		String sourceName;
		String profile = (String) source.get(PROFILE);
		if (profile == null) {
			profile = DEFAULT_PROFILE;
		}
		String label = (String) source.get(LABEL);
		if (label != null) {
			sourceName = String.format("%s-%s-%s", environmentName, profile, label);
		}
		else {
			sourceName = String.format("%s-%s", environmentName, profile);
		}
		return sourceName;
	}

	private void cleanSource(MongoPropertySource source) {
		source.remove(ID);
		source.remove(LABEL);
		source.remove(PROFILE);
	}

	public static class MongoPropertySource extends LinkedHashMap<String, Object> {

		private static final long serialVersionUID = -902368693790845431L;

	}

	private class MapFlattener extends YamlProcessor {

		public Map<String, Object> flatten(Map<String, Object> source) {
			return getFlattenedMap(source);
		}

	}

}
