package org.springframework.cloud.config.server.environment;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class MongoDbEnvironmentRepository implements EnvironmentRepository, Ordered {

	private static final Logger logger = LoggerFactory.getLogger(MongoDbEnvironmentRepository.class);

	private final MongoTemplate mongoTemplate;
	private final MongoEnvironmentProperties properties;

	public MongoDbEnvironmentRepository(MongoTemplate mongoTemplate, MongoEnvironmentProperties properties) {
		this.mongoTemplate = mongoTemplate;
		this.properties = properties;
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		String config = application;
		if (profile == null || profile.isEmpty()) {
			profile = "default";
		}
		if (label == null || label.isEmpty()) {
			label = this.properties.getDefaultLabel();
		}

		Environment environment = new Environment(application, new String[] {profile}, label, null, null);
		addPropertySource(environment, application, profile, label);
		return environment;
	}

	private void addPropertySource(Environment environment, String application, String profile, String label) {
		try {
			Query query = new Query(Criteria.where("application").is(application)
				.and("profile").is(profile)
				.and("label").is(label));
			List<Map> propertyMaps = this.mongoTemplate.find(query, Map.class, this.properties.getCollection());

			for (Map propertyMap : propertyMaps) {
				String propertySourceName = application + "-" + profile;
				@SuppressWarnings("unchecked")
				Map<String, Object> source = (Map<String, Object>) propertyMap.get("properties");
				if (source != null && !source.isEmpty()) {
					environment.add(new PropertySource(propertySourceName, source));
				}
			}
		} catch (DataAccessException e) {
			if (!this.properties.isFailOnError()) {
				if (logger.isDebugEnabled()) {
					logger.debug("Failed to retrieve configuration from MongoDB", e);
				}
			} else {
				throw e;
			}
		}
	}

	@Override
	public int getOrder() {
		return this.properties.getOrder();
	}
}
