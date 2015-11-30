/*
 * Copyright 2013-2015 the original author or authors.
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
import java.util.HashMap;
import java.util.List;

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

	private static final String DEFAULT_PROFILE = "default";

	private MongoTemplate mongoTemplate;
	
	public void setMongoTemplate(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public Environment findOne(String name, String profile, String label) {
		String[] profilesArr = StringUtils.commaDelimitedListToStringArray(profile);
		final List<String> profiles = Arrays.asList(profilesArr.clone());
		for (int i = 0; i < profiles.size(); i ++) {
			String currProfile = profiles.get(i);
			if (DEFAULT_PROFILE.equals(currProfile)) {
				profiles.set(i, null); // 'null' profile value in MongoDB is considered default
				break;
			}
		}
		Query query = new Query().addCriteria(Criteria.where("profile").in(profiles.toArray()));
		if (label != null) {
			query.addCriteria(Criteria.where("label").is(label));
		}
		Environment environment;
		try {
			List<MongoPropertySource> sources = mongoTemplate.find(query, MongoPropertySource.class, name);
			Collections.sort(sources, new Comparator<MongoPropertySource>() {
				@Override
				public int compare(MongoPropertySource s1, MongoPropertySource s2) {
					Object p1 = s1.get("profile");
					Object p2 = s2.get("profile");
					int i1 = profiles.indexOf(p1 != null ? p1 : DEFAULT_PROFILE);
					int i2 = profiles.indexOf(p2 != null ? p2 : DEFAULT_PROFILE);
					return Double.compare(i1, i2);
				}
			});
			environment = new Environment(name, profilesArr, label, null);
			for (MongoPropertySource source : sources) {
				String sourceName = String.format("%s-%s-%s", name, source.get("profile"), source.get("label"));
				PropertySource propSource = new PropertySource(sourceName, source);
				environment.add(propSource);
			}
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot load environment", e);
		}
		return environment;
	}
	
	public static class MongoPropertySource extends HashMap<Object, Object> {

		private static final long serialVersionUID = -902368693790845431L;
		
	}

}
