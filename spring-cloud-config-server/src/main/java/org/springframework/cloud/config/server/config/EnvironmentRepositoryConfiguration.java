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
package org.springframework.cloud.config.server.config;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.config.server.environment.ConsulEnvironmentWatch;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.environment.EnvironmentWatch;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentRepository;
import org.springframework.cloud.config.server.environment.NativeEnvironmentRepository;
import org.springframework.cloud.config.server.environment.SvnKitEnvironmentRepository;
import org.springframework.cloud.config.server.environment.VaultEnvironmentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.web.client.RestTemplate;

/**
 * @author Dave Syer
 * @author Ryan Baxter
 *
 */
@Configuration
public class EnvironmentRepositoryConfiguration {

	@Bean
	@ConditionalOnProperty(value = "spring.cloud.config.server.health.enabled", matchIfMissing = true)
	public ConfigServerHealthIndicator configServerHealthIndicator(EnvironmentRepository repository) {
		return new ConfigServerHealthIndicator(repository);
	}

	@Configuration
	@ConditionalOnMissingBean(EnvironmentRepository.class)
	protected static class DefaultRepositoryConfiguration {

		@Autowired
		private ConfigurableEnvironment environment;

		@Autowired
		private ConfigServerProperties server;

		@Bean
		public MultipleJGitEnvironmentRepository defaultEnvironmentRepository() {
			MultipleJGitEnvironmentRepository repository = new MultipleJGitEnvironmentRepository(this.environment);
			if (this.server.getDefaultLabel()!=null) {
				repository.setDefaultLabel(this.server.getDefaultLabel());
			}
			return repository;
		}
	}

	@Configuration
	@Profile("native")
	protected static class NativeRepositoryConfiguration {

		@Autowired
		private ConfigurableEnvironment environment;

		@Bean
		public NativeEnvironmentRepository nativeEnvironmentRepository() {
			return new NativeEnvironmentRepository(this.environment);
		}
	}

	@Configuration
	@Profile("git")
	protected static class GitRepositoryConfiguration extends DefaultRepositoryConfiguration {}

	@Configuration
	@Profile("subversion")
	protected static class SvnRepositoryConfiguration {
		@Autowired
		private ConfigurableEnvironment environment;

		@Autowired
		private ConfigServerProperties server;

		@Bean
		public SvnKitEnvironmentRepository svnKitEnvironmentRepository() {
			SvnKitEnvironmentRepository repository = new SvnKitEnvironmentRepository(this.environment);
			if (this.server.getDefaultLabel()!=null) {
				repository.setDefaultLabel(this.server.getDefaultLabel());
			}
			return repository;
		}
	}

	@Configuration
	@Profile("vault")
	protected static class VaultConfiguration {
		@Bean
		public VaultEnvironmentRepository vaultEnvironmentRepository(HttpServletRequest request, EnvironmentWatch watch) {
			return new VaultEnvironmentRepository(request, watch, new RestTemplate());
		}
	}

	@Configuration
	@ConditionalOnProperty(value = "spring.cloud.config.server.consul.watch.enabled")
	protected static class ConsulEnvironmentWatchConfiguration {

		@Bean
		public EnvironmentWatch environmentWatch() {
			return new ConsulEnvironmentWatch();
		}
	}

	@Configuration
	@ConditionalOnMissingBean(EnvironmentWatch.class)
	protected static class DefaultEnvironmentWatch {

		@Bean
		public EnvironmentWatch environmentWatch() {
			return new EnvironmentWatch.Default();
		}
	}
}
