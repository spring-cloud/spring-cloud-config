/*
 * Copyright 2018-present the original author or authors.
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
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.test.TestConfigServerApplication;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(classes = TestConfigServerApplication.class, properties = { "spring.config.name:configserver" },
		webEnvironment = RANDOM_PORT)
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

	@Test
	public void defaultApplicationIsIncluded() {
		BoundHashOperations application = redis.boundHashOps("application");
		application.put("name", "from-application");
		application.put("shared", "from-application");
		BoundHashOperations applicationProfile = redis.boundHashOps("application-prod");
		applicationProfile.put("name", "from-application-prod");
		BoundHashOperations app = redis.boundHashOps("myapp-prod");
		app.put("name", "from-myapp-prod");

		Environment env = new RedisEnvironmentRepository(redis, new RedisEnvironmentProperties()).findOne("myapp",
				"prod", "");

		assertThat(env.getPropertySources().stream().map(PropertySource::getName)).containsExactly("redis:myapp-prod",
				"redis:application-prod", "redis:myapp", "redis:application");
		assertThat(env.getPropertySources().get(0).getSource().get("name")).isEqualTo("from-myapp-prod");
		assertThat(env.getPropertySources().get(3).getSource().get("shared")).isEqualTo("from-application");
	}

	@Test
	public void nullApplicationFallsBackToTheDefaultApplication() {
		redis.boundHashOps("application").put("name", "from-application");

		Environment env = new RedisEnvironmentRepository(redis, new RedisEnvironmentProperties()).findOne(null, "prod",
				"");

		assertThat(env.getPropertySources().stream().map(PropertySource::getName))
			.containsExactly("redis:application-prod", "redis:application");
		assertThat(env.getPropertySources().get(1).getSource().get("name")).isEqualTo("from-application");
	}

	@Test
	public void commaDelimitedApplicationsAreAllIncludedLastOneFirst() {
		redis.boundHashOps("app1").put("name", "from-app1");
		redis.boundHashOps("app2").put("name", "from-app2");

		Environment env = new RedisEnvironmentRepository(redis, new RedisEnvironmentProperties())
			.findOne("app1, app2,app1", "prod", "");

		assertThat(env.getPropertySources().stream().map(PropertySource::getName)).containsExactly("redis:app2-prod",
				"redis:app1-prod", "redis:application-prod", "redis:app2", "redis:app1", "redis:application");
		assertThat(env.getPropertySources().get(3).getSource().get("name")).isEqualTo("from-app2");
	}

}
