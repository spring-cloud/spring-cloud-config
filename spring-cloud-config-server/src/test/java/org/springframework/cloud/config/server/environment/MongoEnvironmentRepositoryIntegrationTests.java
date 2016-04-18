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

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.config.EnvironmentRepositoryConfiguration;
import org.springframework.cloud.config.server.environment.MongoEnvironmentRepository.MongoPropertySource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * @author Venil Noronha
 */
public class MongoEnvironmentRepositoryIntegrationTests {

	private ConfigurableApplicationContext context;

	@Before
	public void init() {

	}

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void defaultRepo() {
		// Prepare context
		Map<String, Object> props = new HashMap<>();
		props.put("spring.profiles.active", "mongodb");
		props.put("spring.data.mongodb.database", "testdb");
		context = new SpringApplicationBuilder(TestConfiguration.class).web(false).properties(props).run();
		// Prepare test
		MongoTemplate mongoTemplate = this.context.getBean(MongoTemplate.class);
		mongoTemplate.dropCollection("testapp");
		MongoPropertySource ps = new MongoPropertySource();
		ps.getSource().put("testkey", "testval");
		mongoTemplate.save(ps, "testapp");
		// Test
		EnvironmentRepository repository = this.context.getBean(EnvironmentRepository.class);
		Environment environment = repository.findOne("testapp", "default", null);
		assertEquals("testapp-default", environment.getPropertySources().get(0).getName());
		assertEquals(1, environment.getPropertySources().size());
		assertEquals(true, environment.getPropertySources().get(0).getSource().containsKey("testkey"));
		assertEquals("testval", environment.getPropertySources().get(0).getSource().get("testkey"));
	}
	
	@Test
	public void nestedPropertySource() {
		// Prepare context
		Map<String, Object> props = new HashMap<>();
		props.put("spring.profiles.active", "mongodb");
		props.put("spring.data.mongodb.database", "testdb");
		context = new SpringApplicationBuilder(TestConfiguration.class).web(false).properties(props).run();
		// Prepare test
		MongoTemplate mongoTemplate = this.context.getBean(MongoTemplate.class);
		mongoTemplate.dropCollection("testapp");
		MongoPropertySource ps = new MongoPropertySource();
		Map<String, String> inner = new HashMap<String, String>();
		inner.put("inner", "value");
		ps.getSource().put("outer", inner);
		mongoTemplate.save(ps, "testapp");
		// Test
		EnvironmentRepository repository = this.context.getBean(EnvironmentRepository.class);
		Environment environment = repository.findOne("testapp", "default", null);
		assertEquals("testapp-default", environment.getPropertySources().get(0).getName());
		assertEquals(1, environment.getPropertySources().size());
		assertEquals(true, environment.getPropertySources().get(0).getSource().containsKey("outer.inner"));
		assertEquals("value", environment.getPropertySources().get(0).getSource().get("outer.inner"));
	}

	@Test
	public void repoWithProfileAndLabelInSource() {
		// Prepare context
		Map<String, Object> props = new HashMap<>();
		props.put("spring.profiles.active", "mongodb");
		props.put("spring.data.mongodb.database", "testdb");
		context = new SpringApplicationBuilder(TestConfiguration.class).web(false).properties(props).run();
		// Prepare test
		MongoTemplate mongoTemplate = this.context.getBean(MongoTemplate.class);
		mongoTemplate.dropCollection("testapp");
		MongoPropertySource ps = new MongoPropertySource();
		ps.setProfile("confprofile");
		ps.setLabel("conflabel");
		ps.getSource().put("profile", "sourceprofile");
		ps.getSource().put("label", "sourcelabel");
		mongoTemplate.save(ps, "testapp");
		// Test
		EnvironmentRepository repository = this.context.getBean(EnvironmentRepository.class);
		Environment environment = repository.findOne("testapp", "confprofile", "conflabel");
		assertEquals(1, environment.getPropertySources().size());
		assertEquals("testapp-confprofile-conflabel", environment.getPropertySources().get(0).getName());
		assertEquals(true, environment.getPropertySources().get(0).getSource().containsKey("profile"));
		assertEquals("sourceprofile", environment.getPropertySources().get(0).getSource().get("profile"));
		assertEquals(true, environment.getPropertySources().get(0).getSource().containsKey("label"));
		assertEquals("sourcelabel", environment.getPropertySources().get(0).getSource().get("label"));
	}

	@Configuration
	@Import({
		PropertyPlaceholderAutoConfiguration.class,
		MongoAutoConfiguration.class,
		MongoDataAutoConfiguration.class,
		EnvironmentRepositoryConfiguration.class
	})
	protected static class TestConfiguration {

	}

}
