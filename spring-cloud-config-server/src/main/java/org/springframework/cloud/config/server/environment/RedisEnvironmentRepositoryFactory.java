package org.springframework.cloud.config.server.environment;

import org.springframework.data.redis.core.RedisTemplate;

/**
 * @author Piotr Mińkowski
 */
public class RedisEnvironmentRepositoryFactory implements EnvironmentRepositoryFactory<RedisEnvironmentRepository, RedisEnvironmentProperties> {

	private RedisTemplate redis;

	public RedisEnvironmentRepositoryFactory(RedisTemplate redis) {
		this.redis = redis;
	}

	@Override
	public RedisEnvironmentRepository build(RedisEnvironmentProperties environmentProperties) {
		return new RedisEnvironmentRepository(this.redis, environmentProperties);
	}
}
