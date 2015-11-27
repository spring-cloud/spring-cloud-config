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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.config.EnvironmentRepositoryConfiguration;
import org.springframework.cloud.config.server.environment.MongoEnvironmentRepository.MongoPropertySource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;

/**
 * @author Venil Noronha
 */
public class MongoEnvironmentRepositoryIntegrationTests {

	private ConfigurableApplicationContext context;
	private MongoClient mongoClient;
	private MongoTemplate mongoOps;

	@Before
	public void init() throws Exception {
		ServerAddress seed = new ServerAddress("localhost", 27017);
		mongoClient = new MongoClient(Collections.singletonList(seed));
		mongoOps = new MongoTemplate(mongoClient, "testdb");
	}

	@After
	public void close() {
		mongoClient.close();
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void defaultRepo() throws IOException {
		// Setup
		MongoPropertySource ps = new MongoPropertySource();
		ps.put("testkey", "testval");
		mongoOps.save(ps, "testapp");
		try {
			// Test
			Map<String, Object> props = new HashMap<>();
			props.put("spring.profiles.active", "mongodb");
			props.put("spring.cloud.config.server.mongodb.database", "testdb");
			context = new SpringApplicationBuilder(TestConfiguration.class).web(false).properties(props).run();
			EnvironmentRepository repository = this.context.getBean(EnvironmentRepository.class);
			Environment environment = repository.findOne("testapp", "default", null);
			assertEquals(1, environment.getPropertySources().size());
		}
		finally {
			// Cleanup
			mongoOps.dropCollection("testapp");
		}
	}

	
	@Configuration
	@Import({ PropertyPlaceholderAutoConfiguration.class, EnvironmentRepositoryConfiguration.class })
	protected static class TestConfiguration {
		
	}

}
