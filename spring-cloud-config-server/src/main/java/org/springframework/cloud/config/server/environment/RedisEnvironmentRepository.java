package org.springframework.cloud.config.server.environment;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;

import java.util.Map;

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
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		Environment environment = new Environment(application, profiles, label, null, null);
		Map<?, ?> m = redis.opsForHash().entries(application + "-" + profile);
		environment.add(new PropertySource(application + "-" + profile, m));
		return environment;
	}

}
