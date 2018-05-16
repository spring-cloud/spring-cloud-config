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

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

import javax.net.ssl.SSLContext;
import javax.servlet.http.HttpServletRequest;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.tmatesoft.svn.core.SVNException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
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
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

/**
 * @author Dave Syer
 * @author Ryan Baxter
 * @author Daniel Lavoie
 * @author Dylan Roberts
 *
 */
@Configuration
@EnableConfigurationProperties({ SvnKitEnvironmentProperties.class,
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

	@Bean
	@ConditionalOnMissingBean(search = SearchStrategy.CURRENT)
	public MultipleJGitEnvironmentProperties multipleJGitEnvironmentProperties() {
		return new MultipleJGitEnvironmentProperties();
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

    @Configuration
    @ConditionalOnClass(TransportConfigCallback.class)
    static class JGitFactoryConfig {
        @Bean
        public MultipleJGitEnvironmentRepositoryFactory gitEnvironmentRepositoryFactory(
                ConfigurableEnvironment environment, ConfigServerProperties server,
                Optional<TransportConfigCallback> customTransportConfigCallback) {
            return new MultipleJGitEnvironmentRepositoryFactory(environment, server, customTransportConfigCallback);
        }
    }

    @Configuration
    @ConditionalOnClass(SVNException.class)
    static class SvnFactoryConfig {
        @Bean
        public SvnEnvironmentRepositoryFactory svnEnvironmentRepositoryFactory(ConfigurableEnvironment environment,
                                                                               ConfigServerProperties server) {
            return new SvnEnvironmentRepositoryFactory(environment, server);
        }
    }

    @Configuration
    static class VaultFactoryConfig {
        @Bean
        public VaultEnvironmentRepositoryFactory vaultEnvironmentRepositoryFactory(
                ObjectProvider<HttpServletRequest> request, EnvironmentWatch watch,
                Optional<RestTemplate> skipSslValidationRestTemplate) {
            return new VaultEnvironmentRepositoryFactory(request, watch, skipSslValidationRestTemplate);
        }
    }

    @Configuration
    @ConditionalOnClass(JdbcTemplate.class)
    static class JdbcCompositeConfig {
	    @Bean
        @ConditionalOnBean(JdbcTemplate.class)
        public JdbcEnvironmentRepositoryFactory jdbcEnvironmentRepositoryFactory(JdbcTemplate jdbc) {
            return new JdbcEnvironmentRepositoryFactory(jdbc);
        }
    }

    @Configuration
    static class NativeFactoryConfig {
        @Bean
        public NativeEnvironmentRepositoryFactory nativeEnvironmentRepositoryFactory(ConfigurableEnvironment environment,
                                                                                     ConfigServerProperties properties) {
            return new NativeEnvironmentRepositoryFactory(environment, properties);
        }
    }

	@Bean
	@ConditionalOnClass(HttpClient.class)
	public RestTemplate skipSslValidationRestTemplate() {
		try {
			SSLContext sslContext = new SSLContextBuilder()
					.loadTrustMaterial(null, (certificate, authType) -> true)
					.build();
			CloseableHttpClient httpClient = HttpClients.custom()
					.setSSLContext(sslContext)
					.setSSLHostnameVerifier(new NoopHostnameVerifier())
					.build();
			HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
			requestFactory.setHttpClient(httpClient);
			return new RestTemplate(requestFactory);
		} catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
			throw new RuntimeException(e);
		}
	}
}

@Configuration
@ConditionalOnMissingBean(value = EnvironmentRepository.class, search = SearchStrategy.CURRENT)
class DefaultRepositoryConfiguration {
	@Autowired
	private ConfigurableEnvironment environment;

	@Autowired
	private ConfigServerProperties server;

	@Autowired(required = false)
	private TransportConfigCallback customTransportConfigCallback;

	@Bean
	public MultipleJGitEnvironmentRepository defaultEnvironmentRepository(
	        MultipleJGitEnvironmentRepositoryFactory gitEnvironmentRepositoryFactory,
			MultipleJGitEnvironmentProperties environmentProperties) {
		return gitEnvironmentRepositoryFactory.build(environmentProperties);
	}
}

@Configuration
@ConditionalOnMissingBean(EnvironmentRepository.class)
@Profile("native")
class NativeRepositoryConfiguration {

	@Bean
	public NativeEnvironmentRepository nativeEnvironmentRepository(NativeEnvironmentRepositoryFactory factory,
			NativeEnvironmentProperties environmentProperties) {
        return factory.build(environmentProperties);
	}
}

@Configuration
@Profile("git")
class GitRepositoryConfiguration extends DefaultRepositoryConfiguration {
}

@Configuration
@Profile("subversion")
class SvnRepositoryConfiguration {

	@Bean
	public SvnKitEnvironmentRepository svnKitEnvironmentRepository(SvnKitEnvironmentProperties environmentProperties,
                                                                   SvnEnvironmentRepositoryFactory factory) {
		return factory.build(environmentProperties);
	}
}

@Configuration
@Profile("vault")
class VaultRepositoryConfiguration {

	@Bean
	public VaultEnvironmentRepository vaultEnvironmentRepository(VaultEnvironmentRepositoryFactory factory,
                                                                VaultEnvironmentProperties environmentProperties) {
		return factory.build(environmentProperties);
	}
}

@Configuration
@Profile("jdbc")
@ConditionalOnClass(JdbcTemplate.class)
class JdbcRepositoryConfiguration {

	@Bean
	@ConditionalOnBean(JdbcTemplate.class)
	public JdbcEnvironmentRepository jdbcEnvironmentRepository(JdbcEnvironmentRepositoryFactory factory,
															   JdbcEnvironmentProperties environmentProperties) {
		return factory.build(environmentProperties);
	}
}

@Configuration
@Profile("composite")
class CompositeRepositoryConfiguration {

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
