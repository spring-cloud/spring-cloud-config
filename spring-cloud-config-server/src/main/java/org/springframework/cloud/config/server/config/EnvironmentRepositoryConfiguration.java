/*
 * Copyright 2013-2018 the original author or authors.
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

import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.tmatesoft.svn.core.SVNException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.config.server.composite.CompositeEnvironmentBeanFactoryPostProcessor;
import org.springframework.cloud.config.server.composite.ConditionalOnMissingSearchPathLocator;
import org.springframework.cloud.config.server.composite.ConditionalOnSearchPathLocator;
import org.springframework.cloud.config.server.environment.CompositeEnvironmentRepository;
import org.springframework.cloud.config.server.environment.ConsulEnvironmentWatch;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.environment.EnvironmentWatch;
import org.springframework.cloud.config.server.environment.JdbcEnvironmentProperties;
import org.springframework.cloud.config.server.environment.JdbcEnvironmentRepository;
import org.springframework.cloud.config.server.environment.JdbcEnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentProperties;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentRepository;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.NativeEnvironmentProperties;
import org.springframework.cloud.config.server.environment.NativeEnvironmentRepository;
import org.springframework.cloud.config.server.environment.NativeEnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.SearchPathCompositeEnvironmentRepository;
import org.springframework.cloud.config.server.environment.SvnEnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.SvnKitEnvironmentProperties;
import org.springframework.cloud.config.server.environment.SvnKitEnvironmentRepository;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties;
import org.springframework.cloud.config.server.environment.VaultEnvironmentRepository;
import org.springframework.cloud.config.server.environment.VaultEnvironmentRepositoryFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @author Dave Syer
 * @author Ryan Baxter
 * @author Daniel Lavoie
 * @author Dylan Roberts
 *
 */
@Configuration
@EnableConfigurationProperties({ MultipleJGitEnvironmentProperties.class, SvnKitEnvironmentProperties.class,
		JdbcEnvironmentProperties.class, NativeEnvironmentProperties.class, VaultEnvironmentProperties.class })
@Import({ CompositeRepositoryConfiguration.class, JdbcRepositoryConfiguration.class, VaultRepositoryConfiguration.class,
		SvnRepositoryConfiguration.class, NativeRepositoryConfiguration.class, GitRepositoryConfiguration.class,
		DefaultRepositoryConfiguration.class })
public class EnvironmentRepositoryConfiguration {
	@Bean
	@ConditionalOnProperty(value = "spring.cloud.config.server.health.enabled", matchIfMissing = true)
	public ConfigServerHealthIndicator configServerHealthIndicator(
			EnvironmentRepository repository) {
		return new ConfigServerHealthIndicator(repository);
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

@Configuration
@ConditionalOnMissingBean(EnvironmentRepository.class)
class DefaultRepositoryConfiguration {
	@Autowired
	private ConfigurableEnvironment environment;

	@Autowired
	private ConfigServerProperties server;

	@Autowired(required = false)
	private TransportConfigCallback transportConfigCallback;

	@Bean
	public MultipleJGitEnvironmentRepository defaultEnvironmentRepository(
			MultipleJGitEnvironmentProperties environmentProperties) {
		MultipleJGitEnvironmentRepositoryFactory gitEnvironmentRepositoryFactory =
				new MultipleJGitEnvironmentRepositoryFactory(environment, server,
						Optional.ofNullable(transportConfigCallback));
		return gitEnvironmentRepositoryFactory.build(environmentProperties);
	}
}

@Configuration
@ConditionalOnMissingBean(EnvironmentRepository.class)
@Profile("native")
class NativeRepositoryConfiguration {
	@Autowired
	private ConfigurableEnvironment environment;

	@Autowired
	private ConfigServerProperties configServerProperties;

	@Bean
	public NativeEnvironmentRepository nativeEnvironmentRepository(
			NativeEnvironmentProperties environmentProperties) {
		NativeEnvironmentRepository repository = new NativeEnvironmentRepository(this.environment,
				environmentProperties);
		repository.setDefaultLabel(configServerProperties.getDefaultLabel());
		return repository;
	}
}

@Configuration
@Profile("git")
class GitRepositoryConfiguration extends DefaultRepositoryConfiguration {
}

@Configuration
@Profile("subversion")
class SvnRepositoryConfiguration {
	@Autowired
	private ConfigurableEnvironment environment;

	@Autowired
	private ConfigServerProperties server;

	@Bean
	public SvnKitEnvironmentRepository svnKitEnvironmentRepository(SvnKitEnvironmentProperties environmentProperties) {
		return new SvnEnvironmentRepositoryFactory(environment, server).build(environmentProperties);
	}
}

@Configuration
@Profile("vault")
class VaultRepositoryConfiguration {
	@Bean
	public VaultEnvironmentRepository vaultEnvironmentRepository(HttpServletRequest request, EnvironmentWatch watch,
																 VaultEnvironmentProperties environmentProperties) {
		return new VaultEnvironmentRepositoryFactory(request, watch).build(environmentProperties);
	}
}

@Configuration
@Profile("jdbc")
@ConditionalOnClass(JdbcTemplate.class)
class JdbcRepositoryConfiguration {

	@Bean
	@ConditionalOnBean(JdbcTemplate.class)
	public JdbcEnvironmentRepository jdbcEnvironmentRepository(JdbcTemplate jdbc,
															   JdbcEnvironmentProperties environmentProperties) {
		return new JdbcEnvironmentRepositoryFactory(jdbc).build(environmentProperties);
	}
}

@Configuration
@Profile("composite")
class CompositeRepositoryConfiguration {

	@Configuration
	@ConditionalOnClass(TransportConfigCallback.class)
	static class JGitCompositeConfig {
		@Bean
		public MultipleJGitEnvironmentRepositoryFactory gitEnvironmentRepositoryFactory(
				ConfigurableEnvironment environment, ConfigServerProperties server,
				Optional<TransportConfigCallback> transportConfigCallback) {
			return new MultipleJGitEnvironmentRepositoryFactory(environment, server, transportConfigCallback);
		}
	}

	@Configuration
	@ConditionalOnClass(SVNException.class)
	static class SvnCompositeConfig {
		@Bean
		public SvnEnvironmentRepositoryFactory svnEnvironmentRepositoryFactory(ConfigurableEnvironment environment,
																			   ConfigServerProperties server) {
			return new SvnEnvironmentRepositoryFactory(environment, server);
		}
	}

	@Bean
	public VaultEnvironmentRepositoryFactory vaultEnvironmentRepositoryFactory(HttpServletRequest request,
																						EnvironmentWatch watch) {
		return new VaultEnvironmentRepositoryFactory(request, watch);
	}

	@Configuration
	@ConditionalOnClass(JdbcTemplate.class)
	static class JdbcCompositeConfig {
		@Bean
		public JdbcEnvironmentRepositoryFactory jdbcEnvironmentRepositoryFactory(JdbcTemplate jdbc) {
			return new JdbcEnvironmentRepositoryFactory(jdbc);
		}
	}

	@Bean
	public NativeEnvironmentRepositoryFactory nativeEnvironmentRepositoryFactory(ConfigurableEnvironment environment) {
		return new NativeEnvironmentRepositoryFactory(environment);
	}

	@Bean
	public static CompositeEnvironmentBeanFactoryPostProcessor compositeEnvironmentRepositoryBeanFactoryPostProcessor(
			Environment environment) {
		return new CompositeEnvironmentBeanFactoryPostProcessor(environment);
	}

	@Primary
	@Bean
	@ConditionalOnSearchPathLocator
	public SearchPathCompositeEnvironmentRepository searchPathCompositeEnvironmentRepository(
			List<EnvironmentRepository> environmentRepositories) throws Exception {
		return new SearchPathCompositeEnvironmentRepository(environmentRepositories);
	}

	@Primary
	@Bean
	@ConditionalOnMissingSearchPathLocator
	public CompositeEnvironmentRepository compositeEnvironmentRepository(
			List<EnvironmentRepository> environmentRepositories) throws Exception {
		return new CompositeEnvironmentRepository(environmentRepositories);
	}
}
