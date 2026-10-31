/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.config.server.environment.compliance;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.environment.RedisEnvironmentProperties;
import org.springframework.cloud.config.server.environment.RedisEnvironmentRepository;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Compliance tests for {@link RedisEnvironmentRepository}. Each file is a hash named
 * after the application and profile.
 *
 * @author Ryan Baxter
 */
@Testcontainers
@Tag("DockerRequired")
class RedisEnvironmentRepositoryComplianceTests extends EnvironmentRepositoryComplianceTests {

	@Container
	static GenericContainer<?> redisContainer = new GenericContainer<>("redis:5.0.9-alpine").withExposedPorts(6379);

	private static LettuceConnectionFactory connectionFactory;

	private static StringRedisTemplate redis;

	private final Fixture fixture = new Fixture();

	@BeforeAll
	static void connect() {
		connectionFactory = new LettuceConnectionFactory(
				new RedisStandaloneConfiguration(redisContainer.getHost(), redisContainer.getFirstMappedPort()));
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();
		redis = new StringRedisTemplate(connectionFactory);
	}

	@AfterAll
	static void disconnect() {
		connectionFactory.destroy();
	}

	@Override
	protected EnvironmentRepositoryFixture fixture() {
		return this.fixture;
	}

	static class Fixture implements EnvironmentRepositoryFixture {

		@Override
		public void reset() {
			redis.execute((RedisCallback<Void>) Fixture::flush);
		}

		private static Void flush(RedisConnection connection) {
			connection.serverCommands().flushAll();
			return null;
		}

		@Override
		public void store(ConfigFile file) {
			file.properties().forEach((key, value) -> redis.opsForHash().put(file.baseName(), key, value.toString()));
		}

		@Override
		public EnvironmentRepository repository() {
			return new RedisEnvironmentRepository(redis, new RedisEnvironmentProperties());
		}

		@Override
		public Set<Capability> capabilities() {
			return EnumSet.of(Capability.MULTIPLE_APPLICATIONS, Capability.DISTINCT_DEFAULT_PROFILE);
		}

	}

}
