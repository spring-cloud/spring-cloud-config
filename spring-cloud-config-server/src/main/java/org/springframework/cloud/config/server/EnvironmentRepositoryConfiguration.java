/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.config.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * @author Dave Syer
 *
 */
@Configuration
@ConditionalOnMissingBean(EnvironmentRepository.class)
@EnableConfigurationProperties(ConfigServerProperties.class)
public class EnvironmentRepositoryConfiguration {

	@Bean
	@ConditionalOnProperty(value = "spring.cloud.config.server.health.enabled", matchIfMissing = true)
	public ConfigServerHealthIndicator configServerHealthIndicator(EnvironmentRepository repository) {
		return new ConfigServerHealthIndicator(repository);
	}

	@Configuration
	@Profile("native")
	protected static class NativeRepositoryConfiguration {

		@Autowired
		private ConfigurableEnvironment environment;		
		
		@Bean
		public EnvironmentRepository environmentRepository() {
			return new NativeEnvironmentRepository(environment);
		}

	}

	@Configuration
	@ConditionalOnMissingBean(EnvironmentRepository.class)
	protected static class GitRepositoryConfiguration {
		
		@Autowired
		private ConfigurableEnvironment environment;		
		
		@Bean
		public EnvironmentRepository environmentRepository() {
			return new MultipleJGitEnvironmentRepository(environment);
		}
	}
	
	@Configuration
	@Profile("subversion")
	protected static class SvnRepositoryConfiguration {
		@Autowired
		private ConfigurableEnvironment environment;

		@Bean
		public EnvironmentRepository environmentRepository() {
			return new SvnKitEnvironmentRepository(environment);
		}
	}

}