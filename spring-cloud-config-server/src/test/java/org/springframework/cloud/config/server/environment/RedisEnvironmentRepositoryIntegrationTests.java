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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import redis.embedded.RedisServer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@DataRedisTest(properties = "spring.redis.port=6378")
@ActiveProfiles("redis")
public class RedisEnvironmentRepositoryIntegrationTests {

	private RedisServer redisServer;

	@Before
	public void startRedis() throws IOException {
		redisServer = new RedisServer(6378);
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
		assertThat(env.getPropertySources()).isNotEmpty();
		assertThat(env.getPropertySources().get(0).getSource().get("tag"))
				.isEqualTo("myapp");
	}

}
