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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("redis")
@Testcontainers
@Tag("DockerRequired")
public class RedisEnvironmentRepositoryIntegrationTests {

	@Container
	public static GenericContainer redisContainer = new GenericContainer<>("redis:5.0.9-alpine").withExposedPorts(6379);

	@Autowired
	private StringRedisTemplate redis;

	@DynamicPropertySource
	static void containerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", redisContainer::getHost);
		registry.add("spring.data.redis.port", redisContainer::getFirstMappedPort);
	}

	@Test
	public void test() {
		BoundHashOperations bound = redis.boundHashOps("foo-bar");
		bound.put("name", "foo");
		bound.put("tag", "myapp");

		Environment env = new RedisEnvironmentRepository(redis, new RedisEnvironmentProperties()).findOne("foo", "bar",
				"");
		assertThat(env.getName()).isEqualTo("foo");
		assertThat(env.getPropertySources()).isNotEmpty();
		assertThat(env.getPropertySources().get(0).getSource().get("tag")).isEqualTo("myapp");
	}

}
