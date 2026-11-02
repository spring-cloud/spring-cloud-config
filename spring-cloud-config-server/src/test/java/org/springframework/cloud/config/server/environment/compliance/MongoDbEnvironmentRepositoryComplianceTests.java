/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.config.server.environment.compliance;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Set;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.environment.MongoDbEnvironmentProperties;
import org.springframework.cloud.config.server.environment.MongoDbEnvironmentRepository;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Compliance tests for {@link MongoDbEnvironmentRepository}. Each file is a document in
 * the {@code properties} collection.
 *
 * @author Ryan Baxter
 */
@Testcontainers
@Tag("DockerRequired")
class MongoDbEnvironmentRepositoryComplianceTests extends EnvironmentRepositoryComplianceTests {

	@Container
	static MongoDBContainer mongoContainer = new MongoDBContainer("mongo:5.0");

	private static MongoClient client;

	private static MongoTemplate mongoTemplate;

	private final Fixture fixture = new Fixture();

	@BeforeAll
	static void connect() {
		client = MongoClients.create(mongoContainer.getConnectionString());
		mongoTemplate = new MongoTemplate(client, "compliance");
	}

	@AfterAll
	static void disconnect() {
		client.close();
	}

	@Override
	protected EnvironmentRepositoryFixture fixture() {
		return this.fixture;
	}

	static class Fixture implements EnvironmentRepositoryFixture {

		private final MongoDbEnvironmentProperties properties = new MongoDbEnvironmentProperties();

		@Override
		public void reset() {
			mongoTemplate.dropCollection(this.properties.getCollection());
		}

		@Override
		public void store(ConfigFile file) {
			Document document = new Document();
			document.put("application", file.application());
			document.put("profile", file.profile());
			document.put("label", file.label() != null ? file.label() : this.properties.getDefaultLabel());
			document.put("properties", new Document(new LinkedHashMap<>(file.properties())));
			mongoTemplate.getCollection(this.properties.getCollection()).insertOne(document);
		}

		@Override
		public EnvironmentRepository repository() {
			return new MongoDbEnvironmentRepository(mongoTemplate, this.properties);
		}

		@Override
		public Set<Capability> capabilities() {
			return EnumSet.of(Capability.LABELS, Capability.MULTIPLE_APPLICATIONS, Capability.DISTINCT_DEFAULT_PROFILE);
		}

	}

}
