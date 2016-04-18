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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

	private static final String LABEL = "label";
	private static final String PROFILE = "profile";
	private static final String DEFAULT = "default";
	private static final String DEFAULT_PROFILE = null;
	private static final String DEFAULT_LABEL = null;

	private MongoTemplate mongoTemplate;
	private MapFlattener mapFlattener;

	public void setMongoTemplate(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
		this.mapFlattener = new MapFlattener();
	}

	@Override
	public Environment findOne(String name, String profile, String label) {
		String[] profilesArr = StringUtils.commaDelimitedListToStringArray(profile);
		List<String> profiles = new ArrayList<String>(Arrays.asList(profilesArr.clone()));
		for (int i = 0; i < profiles.size(); i++) {
			if (DEFAULT.equals(profiles.get(i))) {
				profiles.set(i, DEFAULT_PROFILE);
			}
		}
		profiles.add(DEFAULT_PROFILE); // Default configuration will have 'null' profile
		profiles = sortedUnique(profiles);

		List<String> labels = Arrays.asList(label, DEFAULT_LABEL); // Default configuration will have 'null' label
		labels = sortedUnique(labels);

		Query query = new Query();
		query.addCriteria(Criteria.where(PROFILE).in(profiles.toArray()));
		query.addCriteria(Criteria.where(LABEL).in(labels.toArray()));

		Environment environment;
		try {
			List<MongoPropertySource> sources = mongoTemplate.find(query, MongoPropertySource.class, name);
			sortSourcesByLabel(sources, labels);
			sortSourcesByProfile(sources, profiles);
			environment = new Environment(name, profilesArr, label, null);
			for (MongoPropertySource propertySource : sources) {
				String sourceName = generateSourceName(name, propertySource);
				Map<String, Object> flatSource = mapFlattener.flatten(propertySource.getSource());
				PropertySource propSource = new PropertySource(sourceName, flatSource);
				environment.add(propSource);
			}
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot load environment", e);
		}

		return environment;
	}

	private ArrayList<String> sortedUnique(List<String> values) {
		return new ArrayList<String>(new LinkedHashSet<String>(values));
	}

	private void sortSourcesByLabel(List<MongoPropertySource> sources,
			final List<String> labels) {
		Collections.sort(sources, new Comparator<MongoPropertySource>() {

			@Override
			public int compare(MongoPropertySource s1, MongoPropertySource s2) {
				String l1 = s1.getLabel();
				String l2 = s2.getLabel();
				int i1 = labels.indexOf(l1 != null ? l1 : DEFAULT_LABEL);
				int i2 = labels.indexOf(l2 != null ? l2 : DEFAULT_LABEL);
				return Integer.compare(i1, i2);
			}

		});
	}
	
	private void sortSourcesByProfile(List<MongoPropertySource> sources,
			final List<String> profiles) {
		Collections.sort(sources, new Comparator<MongoPropertySource>() {

			@Override
			public int compare(MongoPropertySource s1, MongoPropertySource s2) {
				String p1 = s1.getProfile();
				String p2 = s2.getProfile();
				int i1 = profiles.indexOf(p1 != null ? p1 : DEFAULT_PROFILE);
				int i2 = profiles.indexOf(p2 != null ? p2 : DEFAULT_PROFILE);
				return Integer.compare(i1, i2);
			}

		});
	}

	private String generateSourceName(String environmentName, MongoPropertySource source) {
		String sourceName;
		String profile = source.getProfile() != null ? source.getProfile() : DEFAULT;
		String label = source.getLabel();
		if (label != null) {
			sourceName = String.format("%s-%s-%s", environmentName, profile, label);
		}
		else {
			sourceName = String.format("%s-%s", environmentName, profile);
		}
		return sourceName;
	}

	public static class MongoPropertySource {

		private String profile;
		private String label;
		private LinkedHashMap<String, Object> source = new LinkedHashMap<String, Object>();
		
		public String getProfile() {
			return profile;
		}

		public void setProfile(String profile) {
			this.profile = profile;
		}

		public String getLabel() {
			return label;
		}

		public void setLabel(String label) {
			this.label = label;
		}

		public LinkedHashMap<String, Object> getSource() {
			return source;
		}

		public void setSource(LinkedHashMap<String, Object> source) {
			this.source = source;
		}

	}

	private static class MapFlattener extends YamlProcessor {

		public Map<String, Object> flatten(Map<String, Object> source) {
			return getFlattenedMap(source);
		}

	}

}
