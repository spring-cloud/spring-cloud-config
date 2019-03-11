package org.springframework.cloud.config.server.environment;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * @author Piotr Mińkowski
 */
public class RedisEnvironmentRepository implements EnvironmentRepository {

	private final RedisTemplate redis;
	private final RedisEnvironmentProperties properties;

	public RedisEnvironmentRepository(RedisTemplate redis, RedisEnvironmentProperties properties) {
		this.redis = redis;
		this.properties = properties;
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		return null;
	}
}
