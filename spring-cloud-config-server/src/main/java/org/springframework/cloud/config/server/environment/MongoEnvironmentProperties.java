package org.springframework.cloud.config.server.environment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.config.server.support.EnvironmentRepositoryProperties;
import org.springframework.core.Ordered;

/**
 * Properties related to MongoDB environment repository.
 * @author Alexandros Pappas
 */
@ConfigurationProperties("spring.cloud.config.server.mongodb")
public class MongoEnvironmentProperties implements EnvironmentRepositoryProperties {

	/**
	 * Flag to indicate that MongoDB environment repository configuration is enabled.
	 */
	private boolean enabled = true;

	/**
	 * Order of the MongoDB environment repository.
	 */
	private int order = Ordered.LOWEST_PRECEDENCE - 10;

	/**
	 * Name of the MongoDB collection to query for configuration properties.
	 */
	private String collection = "properties";

	/**
	 * Flag to determine how to handle query exceptions.
	 */
	private boolean failOnError = true;

	/**
	 * Default label to use if none is specified.
	 */
	private String defaultLabel = "master";

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public int getOrder() {
		return order;
	}

	@Override
	public void setOrder(int order) {
		this.order = order;
	}

	public String getCollection() {
		return collection;
	}

	public void setCollection(String collection) {
		this.collection = collection;
	}

	public boolean isFailOnError() {
		return failOnError;
	}

	public void setFailOnError(boolean failOnError) {
		this.failOnError = failOnError;
	}

	public String getDefaultLabel() {
		return defaultLabel;
	}

	public void setDefaultLabel(String defaultLabel) {
		this.defaultLabel = defaultLabel;
	}
}

