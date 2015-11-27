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

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;

/**
 * Simple implementation of {@link EnvironmentRepository} that is backed by
 * MongoDB. The resulting Environment is composed of property sources located
 * using the application name as the MongoDB collection while MongoDB document's
 * `profile` and `label` key values represent the Spring profile and label
 * respectively.
 *
 * @author Venil Noronha
 */
@ConfigurationProperties("spring.cloud.config.server.mongodb")
public class MongoEnvironmentRepository implements EnvironmentRepository, InitializingBean, DisposableBean {

	private static final String DEFAULT_HOST = "127.0.0.1";
	private static final int DEFAULT_PORT = 27017;
	private static final String DEFAULT_PROFILE = "default";

	/**
	 * The host.
	 */
	private String host = DEFAULT_HOST;
	
	/**
	 * The port.
	 */
	private int port = DEFAULT_PORT;
	
	/**
	 * The database name.
	 */
	private String database;
	
	/**
	 * The username.
	 */
	private String username;
	
	/**
	 * The password.
	 */
	private String password;

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getDatabase() {
		return database;
	}

	public void setDatabase(String database) {
		this.database = database;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	private MongoClient mongoClient;
	private MongoOperations mongoOps;
	
	@Override
	public void afterPropertiesSet() throws Exception {
		Assert.state(getDatabase() != null, "You need to configure mongodb database name");
		ServerAddress seed = new ServerAddress(host, port);
		if (username != null && password != null) {
			MongoCredential cred = MongoCredential.createCredential(username, database, password.toCharArray());
			mongoClient = new MongoClient(Collections.singletonList(seed), Collections.singletonList(cred));
		}
		else {
			mongoClient = new MongoClient(Collections.singletonList(seed));
		}
		mongoOps = new MongoTemplate(mongoClient, database);
	}

	@Override
	public void destroy() throws Exception {
		mongoClient.close();
	}

	@Override
	public Environment findOne(String name, String profile, String label) {
		String[] profilesArr = StringUtils.commaDelimitedListToStringArray(profile);
		Environment environment = new Environment(name, profilesArr, label, null);
		final List<String> profiles = Arrays.asList(profilesArr.clone());
		for (int i = 0; i < profiles.size(); i ++) {
			String currProfile = profiles.get(i);
			if (DEFAULT_PROFILE.equals(currProfile)) {
				profiles.set(i, null); // `null` profile value in MongoDB is considered default
				break;
			}
		}
		Query query = new Query().addCriteria(Criteria.where("profile").in(profiles.toArray()));
		if (label != null) {
			query.addCriteria(Criteria.where("label").is(label));
		}
		List<MongoPropertySource> sources = mongoOps.find(query, MongoPropertySource.class, name);
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
		for (MongoPropertySource source : sources) {
			String sourceName = String.format("%s-%s-%s", name, source.get("profile"), source.get("label"));
			PropertySource propSource = new PropertySource(sourceName, source);
			environment.add(propSource);
		}
		return environment;
	}
	
	public static class MongoPropertySource extends HashMap<Object, Object> {

		private static final long serialVersionUID = -902368693790845431L;
		
	}

}
