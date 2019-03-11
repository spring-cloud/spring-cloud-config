package org.springframework.cloud.config.server.environment;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import redis.embedded.RedisServer;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@DataRedisTest
@ActiveProfiles("redis")
public class RedisEnvironmentRepositoryTests {

	private RedisServer redisServer;

	@Before
	public void startRedis() throws IOException {
		redisServer = new RedisServer(6379);
		redisServer.start();
	}

	@After
	public void stopRedis() {
		redisServer.stop();
	}

	@Autowired
	StringRedisTemplate redis;

	@Test
	public void test() {
		BoundHashOperations bound = redis.boundHashOps("foo-bar");
		bound.put("name", "foo");
		bound.put("tag", "myapp");

		Environment env = new RedisEnvironmentRepository(redis,
			new RedisEnvironmentProperties()).findOne("foo", "bar", "");
		assertThat(env.getName()).isEqualTo("foo");
	}
}
