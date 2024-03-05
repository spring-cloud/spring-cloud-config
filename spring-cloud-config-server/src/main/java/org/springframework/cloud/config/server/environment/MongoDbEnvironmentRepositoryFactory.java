package org.springframework.cloud.config.server.environment;

import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Factory for creating instances of MongoDbEnvironmentRepository.
 */
public class MongoDbEnvironmentRepositoryFactory
	implements EnvironmentRepositoryFactory<MongoDbEnvironmentRepository, MongoEnvironmentProperties> {

	private final MongoTemplate mongoTemplate;

	public MongoDbEnvironmentRepositoryFactory(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public MongoDbEnvironmentRepository build(MongoEnvironmentProperties environmentProperties) {
		return new MongoDbEnvironmentRepository(this.mongoTemplate, environmentProperties);
	}
}

