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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import com.mongodb.MongoException;
import com.mongodb.MongoTimeoutException;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import wiremock.com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * @author Alexandros Pappas
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("mongodb")
@Testcontainers
@Tag("DockerRequired")
public class MongoDbEnvironmentRepositoryTests {

	@Container
	@ServiceConnection
	static MongoDBContainer mongoContainer = new MongoDBContainer("mongo:5.0");

	@Autowired
	private MongoTemplate mongoTemplate;

	@BeforeEach
	void setup() throws IOException {
		mongoContainer.start();
		mongoTemplate.dropCollection("properties");
		InputStream inputStream = new ClassPathResource("/data-mongo.json").getInputStream();
		String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
		List<Document> documents = Arrays.asList(new ObjectMapper().readValue(json, Document[].class));
		mongoTemplate.getCollection("properties").insertMany(documents);
	}

	@Test
	public void basicProperties() {
		MongoDbEnvironmentProperties properties = new MongoDbEnvironmentProperties();
		Environment env = new MongoDbEnvironmentRepository(mongoTemplate, properties).findOne("foo", "bar", "");
		assertThat(env.getName()).isEqualTo("foo");
		assertThat(env.getProfiles()).isEqualTo(new String[] { "bar" });
		assertThat(env.getLabel()).isEqualTo("master");
		assertThat(env.getPropertySources()).isNotEmpty();
		assertThat(env.getPropertySources().get(0).getName()).isEqualTo("foo-bar");
		assertThat(env.getPropertySources().get(0).getSource().get("a_b_c")).isEqualTo("foo-bar");
		assertThat(env.getPropertySources().get(1).getName()).isEqualTo("application-bar");
		assertThat(env.getPropertySources().get(1).getSource().get("a_b_c")).isEqualTo("application-bar");
		assertThat(env.getPropertySources().get(2).getName()).isEqualTo("foo");
		assertThat(env.getPropertySources().get(2).getSource().get("a_b_c")).isEqualTo("foo-null");
		assertThat(env.getPropertySources().get(3).getName()).isEqualTo("application");
		assertThat(env.getPropertySources().get(3).getSource().get("a_b_c")).isEqualTo("application-null");
	}

	@Test
	public void testDefaultProfile() {
		MongoDbEnvironmentProperties properties = new MongoDbEnvironmentProperties();
		Environment env = new MongoDbEnvironmentRepository(mongoTemplate, properties).findOne("foo", "", "");
		assertThat(env.getName()).isEqualTo("foo");
		assertThat(env.getProfiles()).isEqualTo(new String[] { "default" });
		assertThat(env.getLabel()).isEqualTo("master");
		assertThat(env.getPropertySources()).isNotEmpty();
		assertThat(env.getPropertySources().get(0).getName()).isEqualTo("foo-default");
		assertThat(env.getPropertySources().get(0).getSource().get("a_b_c")).isEqualTo("foo-default");
		assertThat(env.getPropertySources().get(1).getName()).isEqualTo("application-default");
		assertThat(env.getPropertySources().get(1).getSource().get("a_b_c")).isEqualTo("application-default");
		assertThat(env.getPropertySources().get(2).getName()).isEqualTo("foo");
		assertThat(env.getPropertySources().get(2).getSource().get("a_b_c")).isEqualTo("foo-null");
		assertThat(env.getPropertySources().get(3).getName()).isEqualTo("application");
		assertThat(env.getPropertySources().get(3).getSource().get("a_b_c")).isEqualTo("application-null");
	}

	@Test
	public void testProfileNotExist() {
		MongoDbEnvironmentProperties properties = new MongoDbEnvironmentProperties();
		Environment env = new MongoDbEnvironmentRepository(mongoTemplate, properties).findOne("foo", "not_exist", "");
		assertThat(env.getName()).isEqualTo("foo");
		assertThat(env.getProfiles()).isEqualTo(new String[] { "not_exist" });
		assertThat(env.getLabel()).isEqualTo("master");
		assertThat(env.getPropertySources()).isNotEmpty();
		assertThat(env.getPropertySources().get(0).getName()).isEqualTo("foo");
		assertThat(env.getPropertySources().get(0).getSource().get("a_b_c")).isEqualTo("foo-null");
		assertThat(env.getPropertySources().get(1).getName()).isEqualTo("application");
		assertThat(env.getPropertySources().get(1).getSource().get("a_b_c")).isEqualTo("application-null");
	}

	@Test
	public void testApplicationNotExist() {
		MongoDbEnvironmentProperties properties = new MongoDbEnvironmentProperties();
		Environment env = new MongoDbEnvironmentRepository(mongoTemplate, properties).findOne("not_exist", "bar", "");
		assertThat(env.getName()).isEqualTo("not_exist");
		assertThat(env.getProfiles()).isEqualTo(new String[] { "bar" });
		assertThat(env.getLabel()).isEqualTo("master");
		assertThat(env.getPropertySources()).isNotEmpty();
		assertThat(env.getPropertySources().get(0).getName()).isEqualTo("application-bar");
		assertThat(env.getPropertySources().get(0).getSource().get("a_b_c")).isEqualTo("application-bar");
		assertThat(env.getPropertySources().get(1).getName()).isEqualTo("application");
		assertThat(env.getPropertySources().get(1).getSource().get("a_b_c")).isEqualTo("application-null");
	}

	@Test
	public void testApplicationProfileBothNotExist() {
		MongoDbEnvironmentProperties properties = new MongoDbEnvironmentProperties();
		Environment env = new MongoDbEnvironmentRepository(mongoTemplate, properties).findOne("not_exist", "not_exist",
				"");
		assertThat(env.getName()).isEqualTo("not_exist");
		assertThat(env.getProfiles()).isEqualTo(new String[] { "not_exist" });
		assertThat(env.getLabel()).isEqualTo("master");
		assertThat(env.getPropertySources()).isNotEmpty();
		assertThat(env.getPropertySources().get(0).getName()).isEqualTo("application");
		assertThat(env.getPropertySources().get(0).getSource().get("a_b_c")).isEqualTo("application-null");
	}

	@Test
	public void testCustomLabel() {
		MongoDbEnvironmentProperties properties = new MongoDbEnvironmentProperties();
		properties.setDefaultLabel("main");
		Environment env = new MongoDbEnvironmentRepository(mongoTemplate, properties).findOne("foo", "bar", "");
		assertThat(env.getName()).isEqualTo("foo");
		assertThat(env.getProfiles()).isEqualTo(new String[] { "bar" });
		assertThat(env.getLabel()).isEqualTo("main");
		assertThat(env.getPropertySources()).isNotEmpty();
		assertThat(env.getPropertySources().get(0).getName()).isEqualTo("foo-bar");
		assertThat(env.getPropertySources().get(0).getSource().get("a_b_c")).isEqualTo("foo-bar");
		assertThat(env.getPropertySources().get(1).getName()).isEqualTo("application-bar");
		assertThat(env.getPropertySources().get(1).getSource().get("a_b_c")).isEqualTo("application-bar");
	}

	@Test
	public void testFailOnError() {
		MongoTemplate failingMongoTemplate = Mockito.spy(mongoTemplate);
		Mockito.doThrow(new MongoTimeoutException("Timed out after 30000 ms while waiting for a server."))
			.when(failingMongoTemplate)
			.find(any(Query.class), any(), anyString());
		MongoDbEnvironmentRepository repository = new MongoDbEnvironmentRepository(failingMongoTemplate,
				new MongoDbEnvironmentProperties());
		assertThatThrownBy(() -> repository.findOne("foo", "bar", "")).isInstanceOf(MongoException.class)
			.hasMessageContaining("Timed out after 30000 ms while waiting for a server.");
	}

}
