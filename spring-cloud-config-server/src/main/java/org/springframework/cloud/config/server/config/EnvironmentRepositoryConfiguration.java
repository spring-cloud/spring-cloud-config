/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.config.server.config;

import java.util.List;
import java.util.Optional;

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import io.micrometer.observation.ObservationRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.http.client.HttpClient;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.tmatesoft.svn.core.SVNException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.ssm.SsmClient;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.config.server.composite.CompositeEnvironmentBeanFactoryPostProcessor;
import org.springframework.cloud.config.server.composite.ConditionalOnMissingSearchPathLocator;
import org.springframework.cloud.config.server.composite.ConditionalOnSearchPathLocator;
import org.springframework.cloud.config.server.environment.AwsParameterStoreEnvironmentProperties;
import org.springframework.cloud.config.server.environment.AwsParameterStoreEnvironmentRepository;
import org.springframework.cloud.config.server.environment.AwsParameterStoreEnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.AwsS3EnvironmentProperties;
import org.springframework.cloud.config.server.environment.AwsS3EnvironmentRepository;
import org.springframework.cloud.config.server.environment.AwsS3EnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.AwsSecretsManagerEnvironmentProperties;
import org.springframework.cloud.config.server.environment.AwsSecretsManagerEnvironmentRepository;
import org.springframework.cloud.config.server.environment.AwsSecretsManagerEnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.CompositeEnvironmentRepository;
import org.springframework.cloud.config.server.environment.ConfigTokenProvider;
import org.springframework.cloud.config.server.environment.ConfigurableHttpConnectionFactory;
import org.springframework.cloud.config.server.environment.ConsulEnvironmentWatch;
import org.springframework.cloud.config.server.environment.CredhubEnvironmentProperties;
import org.springframework.cloud.config.server.environment.CredhubEnvironmentRepository;
import org.springframework.cloud.config.server.environment.CredhubEnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.environment.EnvironmentWatch;
import org.springframework.cloud.config.server.environment.GoogleSecretManagerEnvironmentProperties;
import org.springframework.cloud.config.server.environment.GoogleSecretManagerEnvironmentRepository;
import org.springframework.cloud.config.server.environment.GoogleSecretManagerEnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.HttpClient4BuilderCustomizer;
import org.springframework.cloud.config.server.environment.HttpClientConfigurableHttpConnectionFactory;
import org.springframework.cloud.config.server.environment.HttpClientVaultRestTemplateFactory;
import org.springframework.cloud.config.server.environment.HttpRequestConfigTokenProvider;
import org.springframework.cloud.config.server.environment.JdbcEnvironmentProperties;
import org.springframework.cloud.config.server.environment.JdbcEnvironmentRepository;
import org.springframework.cloud.config.server.environment.JdbcEnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentProperties;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentRepository;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.NativeEnvironmentProperties;
import org.springframework.cloud.config.server.environment.NativeEnvironmentRepository;
import org.springframework.cloud.config.server.environment.NativeEnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.RedisEnvironmentProperties;
import org.springframework.cloud.config.server.environment.RedisEnvironmentRepository;
import org.springframework.cloud.config.server.environment.RedisEnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.SearchPathCompositeEnvironmentRepository;
import org.springframework.cloud.config.server.environment.SvnEnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.SvnKitEnvironmentProperties;
import org.springframework.cloud.config.server.environment.SvnKitEnvironmentRepository;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties;
import org.springframework.cloud.config.server.environment.VaultEnvironmentRepository;
import org.springframework.cloud.config.server.environment.VaultEnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.vault.SpringVaultClientConfiguration;
import org.springframework.cloud.config.server.environment.vault.SpringVaultEnvironmentRepository;
import org.springframework.cloud.config.server.environment.vault.SpringVaultEnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.support.GitCredentialsProviderFactory;
import org.springframework.cloud.config.server.support.GoogleCloudSourceSupport;
import org.springframework.cloud.config.server.support.TransportConfigCallbackFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.credhub.core.CredHubOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.vault.core.VaultTemplate;

/**
 * @author Dave Syer
 * @author Ryan Baxter
 * @author Daniel Lavoie
 * @author Dylan Roberts
 * @author Alberto C. Ríos
 * @author Scott Frederick
 * @author Tejas Pandilwar
 * @author Iulian Antohe
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ SvnKitEnvironmentProperties.class, CredhubEnvironmentProperties.class,
		JdbcEnvironmentProperties.class, NativeEnvironmentProperties.class, VaultEnvironmentProperties.class,
		RedisEnvironmentProperties.class, AwsS3EnvironmentProperties.class,
		AwsSecretsManagerEnvironmentProperties.class, AwsParameterStoreEnvironmentProperties.class,
		GoogleSecretManagerEnvironmentProperties.class })
@Import({ CompositeRepositoryConfiguration.class, JdbcRepositoryConfiguration.class, VaultConfiguration.class,
		VaultRepositoryConfiguration.class, SpringVaultRepositoryConfiguration.class, CredhubConfiguration.class,
		CredhubRepositoryConfiguration.class, SvnRepositoryConfiguration.class, NativeRepositoryConfiguration.class,
		GitRepositoryConfiguration.class, RedisRepositoryConfiguration.class, GoogleCloudSourceConfiguration.class,
		AwsS3RepositoryConfiguration.class, AwsSecretsManagerRepositoryConfiguration.class,
		AwsParameterStoreRepositoryConfiguration.class, GoogleSecretManagerRepositoryConfiguration.class,
		// DefaultRepositoryConfiguration must be last
		DefaultRepositoryConfiguration.class })
public class EnvironmentRepositoryConfiguration {

	@Bean
	@ConditionalOnMissingBean(search = SearchStrategy.CURRENT)
	public MultipleJGitEnvironmentProperties multipleJGitEnvironmentProperties() {
		return new MultipleJGitEnvironmentProperties();
	}

	@Bean
	@ConditionalOnMissingBean(ConfigTokenProvider.class)
	public ConfigTokenProvider defaultConfigTokenProvider(ObjectProvider<HttpServletRequest> httpRequest) {
		return new HttpRequestConfigTokenProvider(httpRequest);
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(AbstractHealthIndicator.class)
	@ConditionalOnProperty(value = "spring.cloud.config.server.health.enabled", matchIfMissing = true)
	protected static class ConfigServerActuatorConfiguration {

		@Bean
		public ConfigServerHealthIndicator configServerHealthIndicator(EnvironmentRepository repository) {
			return new ConfigServerHealthIndicator(repository);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty("spring.cloud.config.server.consul.watch.enabled")
	protected static class ConsulEnvironmentWatchConfiguration {

		@Bean
		public EnvironmentWatch environmentWatch() {
			return new ConsulEnvironmentWatch();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingBean(EnvironmentWatch.class)
	protected static class DefaultEnvironmentWatch {

		@Bean
		public EnvironmentWatch environmentWatch() {
			return new EnvironmentWatch.Default();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(TransportConfigCallback.class)
	static class JGitFactoryConfig {

		@Bean
		public MultipleJGitEnvironmentRepositoryFactory gitEnvironmentRepositoryFactory(
				ConfigurableEnvironment environment, ConfigServerProperties server,
				Optional<ConfigurableHttpConnectionFactory> jgitHttpConnectionFactory,
				Optional<TransportConfigCallback> customTransportConfigCallback,
				Optional<GoogleCloudSourceSupport> googleCloudSourceSupport,
				GitCredentialsProviderFactory gitCredentialsProviderFactory,
				List<HttpClient4BuilderCustomizer> customizers) {
			final TransportConfigCallbackFactory transportConfigCallbackFactory = new TransportConfigCallbackFactory(
					customTransportConfigCallback.orElse(null), googleCloudSourceSupport.orElse(null));
			return new MultipleJGitEnvironmentRepositoryFactory(environment, server, jgitHttpConnectionFactory,
					transportConfigCallbackFactory, gitCredentialsProviderFactory, customizers);
		}

		@Bean
		@ConditionalOnMissingBean
		public GitCredentialsProviderFactory gitCredentialsProviderFactory() {
			return new GitCredentialsProviderFactory();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass({ HttpClient.class, TransportConfigCallback.class })
	static class JGitHttpClientConfig {

		@Bean
		public ConfigurableHttpConnectionFactory httpClientConnectionFactory() {
			return new HttpClientConfigurableHttpConnectionFactory();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(S3Client.class)
	static class AwsS3FactoryConfig {

		@Bean
		public AwsS3EnvironmentRepositoryFactory awsS3EnvironmentRepositoryFactory(ConfigServerProperties server) {
			return new AwsS3EnvironmentRepositoryFactory(server);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(SecretsManagerClient.class)
	static class AwsSecretsManagerFactoryConfig {

		@Bean
		public AwsSecretsManagerEnvironmentRepositoryFactory awsSecretsManagerEnvironmentRepositoryFactory(
				ConfigServerProperties configServerProperties) {
			return new AwsSecretsManagerEnvironmentRepositoryFactory(configServerProperties);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(SsmClient.class)
	static class AwsParameterStoreFactoryConfig {

		// set the bean name explicitly since we assume the bean name will start with the
		// profile
		// name in the case of a composite configuration. The profile name is
		// awsparamstore
		// but the method name starts with awsParameterStore and the logic in
		// CompositeUtils.getFactoryName
		// will not find a match
		@Bean(name = "awsparamstoreenvironmentrepositoryfactory")
		public AwsParameterStoreEnvironmentRepositoryFactory awsParameterStoreEnvironmentRepositoryFactory(
				ConfigServerProperties server) {
			return new AwsParameterStoreEnvironmentRepositoryFactory(server);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(SVNException.class)
	static class SvnFactoryConfig {

		@Bean
		public SvnEnvironmentRepositoryFactory svnEnvironmentRepositoryFactory(ConfigurableEnvironment environment,
				ConfigServerProperties server, ObjectProvider<ObservationRegistry> observationRegistry) {
			return new SvnEnvironmentRepositoryFactory(environment, server,
					observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP));
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingClass("org.springframework.vault.core.VaultTemplate")
	@SuppressWarnings("deprecation")
	static class VaultFactoryConfig {

		@Bean
		public VaultEnvironmentRepositoryFactory vaultEnvironmentRepositoryFactory(
				ObjectProvider<HttpServletRequest> request, EnvironmentWatch watch,
				Optional<VaultEnvironmentRepositoryFactory.VaultRestTemplateFactory> vaultRestTemplateFactory,
				ConfigTokenProvider tokenProvider) {
			return new VaultEnvironmentRepositoryFactory(request, watch, vaultRestTemplateFactory, tokenProvider);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(SecretManagerServiceClient.class)
	static class GoogleSecretManagerFactoryConfig {

		@Bean
		public GoogleSecretManagerEnvironmentRepositoryFactory googleSecretManagerEnvironmentRepositoryFactory(
				ObjectProvider<HttpServletRequest> request) {
			return new GoogleSecretManagerEnvironmentRepositoryFactory(request);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(HttpClient.class)
	@ConditionalOnMissingClass("org.springframework.vault.core.VaultTemplate")
	@SuppressWarnings("deprecation")
	static class VaultHttpClientConfig {

		@Bean
		public VaultEnvironmentRepositoryFactory.VaultRestTemplateFactory vaultRestTemplateFactory() {
			return new HttpClientVaultRestTemplateFactory();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(VaultTemplate.class)
	@Import(SpringVaultClientConfiguration.class)
	static class SpringVaultFactoryConfig {

		@Bean
		public SpringVaultEnvironmentRepositoryFactory vaultEnvironmentRepositoryFactory(
				ObjectProvider<HttpServletRequest> request, EnvironmentWatch watch,
				SpringVaultClientConfiguration vaultClientConfiguration) {
			return new SpringVaultEnvironmentRepositoryFactory(request, watch, vaultClientConfiguration);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(JdbcTemplate.class)
	@ConditionalOnProperty(value = "spring.cloud.config.server.jdbc.enabled", matchIfMissing = true)
	static class JdbcFactoryConfig {

		@Bean
		@ConditionalOnBean(JdbcTemplate.class)
		public JdbcEnvironmentRepositoryFactory jdbcEnvironmentRepositoryFactory(JdbcTemplate jdbc,
				JdbcEnvironmentRepository.PropertiesResultSetExtractor propertiesResultSetExtractor) {
			return new JdbcEnvironmentRepositoryFactory(jdbc, propertiesResultSetExtractor);
		}

		@Bean
		@ConditionalOnMissingBean(JdbcEnvironmentRepository.PropertiesResultSetExtractor.class)
		public JdbcEnvironmentRepository.PropertiesResultSetExtractor propertiesResultSetExtractor() {
			return new JdbcEnvironmentRepository.PropertiesResultSetExtractor();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(StringRedisTemplate.class)
	static class RedisFactoryConfig {

		@Bean
		@ConditionalOnBean(StringRedisTemplate.class)
		public RedisEnvironmentRepositoryFactory redisEnvironmentRepositoryFactory(StringRedisTemplate redis) {
			return new RedisEnvironmentRepositoryFactory(redis);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(CredHubOperations.class)
	static class CredhubFactoryConfig {

		@Bean
		public CredhubEnvironmentRepositoryFactory credhubEnvironmentRepositoryFactory(
				Optional<CredHubOperations> credHubOperations) {
			return new CredhubEnvironmentRepositoryFactory(credHubOperations.orElse(null));
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class NativeFactoryConfig {

		@Bean
		public NativeEnvironmentRepositoryFactory nativeEnvironmentRepositoryFactory(
				ConfigurableEnvironment environment, ConfigServerProperties properties,
				ObjectProvider<ObservationRegistry> observationRegistry) {
			return new NativeEnvironmentRepositoryFactory(environment, properties,
					observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP));
		}

	}

}

@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingBean(value = EnvironmentRepository.class, search = SearchStrategy.CURRENT)
class DefaultRepositoryConfiguration {

	@Bean
	public MultipleJGitEnvironmentRepository defaultEnvironmentRepository(
			MultipleJGitEnvironmentRepositoryFactory gitEnvironmentRepositoryFactory,
			MultipleJGitEnvironmentProperties environmentProperties) throws Exception {
		return gitEnvironmentRepositoryFactory.build(environmentProperties);
	}

}

@Configuration(proxyBeanMethods = false)
@Profile("native")
class NativeRepositoryConfiguration {

	@Bean
	public NativeEnvironmentRepository nativeEnvironmentRepository(NativeEnvironmentRepositoryFactory factory,
			NativeEnvironmentProperties environmentProperties) {
		return factory.build(environmentProperties);
	}

}

@Configuration(proxyBeanMethods = false)
@Profile("git")
class GitRepositoryConfiguration extends DefaultRepositoryConfiguration {

}

@Configuration(proxyBeanMethods = false)
@Profile("awss3")
class AwsS3RepositoryConfiguration {

	@Bean
	@ConditionalOnMissingBean(AwsS3EnvironmentRepository.class)
	public AwsS3EnvironmentRepository awsS3EnvironmentRepository(AwsS3EnvironmentRepositoryFactory factory,
			AwsS3EnvironmentProperties environmentProperties) {
		return factory.build(environmentProperties);
	}

}

@Configuration(proxyBeanMethods = false)
@Profile("awsparamstore")
class AwsParameterStoreRepositoryConfiguration {

	@Bean
	@ConditionalOnMissingBean(AwsParameterStoreEnvironmentRepository.class)
	public AwsParameterStoreEnvironmentRepository awsParameterStoreEnvironmentRepository(
			AwsParameterStoreEnvironmentRepositoryFactory factory,
			AwsParameterStoreEnvironmentProperties environmentProperties) {
		return factory.build(environmentProperties);
	}

}

@Configuration(proxyBeanMethods = false)
@Profile("awssecretsmanager")
class AwsSecretsManagerRepositoryConfiguration {

	@Bean
	@ConditionalOnMissingBean(AwsSecretsManagerEnvironmentRepository.class)
	public AwsSecretsManagerEnvironmentRepository awsSecretsManagerEnvironmentRepository(
			AwsSecretsManagerEnvironmentRepositoryFactory factory,
			AwsSecretsManagerEnvironmentProperties environmentProperties) {
		return factory.build(environmentProperties);
	}

}

@Configuration(proxyBeanMethods = false)
@Profile("subversion")
class SvnRepositoryConfiguration {

	@Bean
	public SvnKitEnvironmentRepository svnKitEnvironmentRepository(SvnEnvironmentRepositoryFactory factory,
			SvnKitEnvironmentProperties environmentProperties) {
		return factory.build(environmentProperties);
	}

}

@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingClass("org.springframework.vault.core.VaultTemplate")
@Profile("vault")
@SuppressWarnings("deprecation")
class VaultRepositoryConfiguration {

	@Bean
	public VaultEnvironmentRepository vaultEnvironmentRepository(VaultEnvironmentRepositoryFactory factory,
			VaultEnvironmentProperties environmentProperties) throws Exception {
		return factory.build(environmentProperties);
	}

}

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(VaultTemplate.class)
@Profile("vault")
class SpringVaultRepositoryConfiguration {

	@Bean
	public SpringVaultEnvironmentRepository vaultEnvironmentRepository(SpringVaultEnvironmentRepositoryFactory factory,
			VaultEnvironmentProperties environmentProperties) {
		return factory.build(environmentProperties);
	}

}

@Configuration(proxyBeanMethods = false)
@Profile("credhub")
class CredhubRepositoryConfiguration {

	@Bean
	public CredhubEnvironmentRepository credhubEnvironmentRepository(CredhubEnvironmentRepositoryFactory factory,
			CredhubEnvironmentProperties environmentProperties) {
		return factory.build(environmentProperties);
	}

}

@Configuration(proxyBeanMethods = false)
@Profile("jdbc")
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnProperty(value = "spring.cloud.config.server.jdbc.enabled", matchIfMissing = true)
class JdbcRepositoryConfiguration {

	@Bean
	@ConditionalOnBean(JdbcTemplate.class)
	public JdbcEnvironmentRepository jdbcEnvironmentRepository(JdbcEnvironmentRepositoryFactory factory,
			JdbcEnvironmentProperties environmentProperties) {
		return factory.build(environmentProperties);
	}

}

@Configuration(proxyBeanMethods = false)
@Profile("redis")
@ConditionalOnClass(StringRedisTemplate.class)
class RedisRepositoryConfiguration {

	@Bean
	@ConditionalOnBean(StringRedisTemplate.class)
	public RedisEnvironmentRepository redisEnvironmentRepository(RedisEnvironmentRepositoryFactory factory,
			RedisEnvironmentProperties environmentProperties) {
		return factory.build(environmentProperties);
	}

}

@Configuration(proxyBeanMethods = false)
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
			List<EnvironmentRepository> environmentRepositories, ConfigServerProperties properties,
			ObjectProvider<ObservationRegistry> observationRegistry) {
		return new SearchPathCompositeEnvironmentRepository(environmentRepositories,
				observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP),
				properties.isFailOnCompositeError());
	}

	@Primary
	@Bean
	@ConditionalOnMissingSearchPathLocator
	public CompositeEnvironmentRepository compositeEnvironmentRepository(
			List<EnvironmentRepository> environmentRepositories, ConfigServerProperties properties,
			ObjectProvider<ObservationRegistry> observationRegistry) {
		return new CompositeEnvironmentRepository(environmentRepositories,
				observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP),
				properties.isFailOnCompositeError());
	}

}

@Configuration(proxyBeanMethods = false)
@Profile("secret-manager")
@ConditionalOnClass(SecretManagerServiceClient.class)
class GoogleSecretManagerRepositoryConfiguration {

	@Bean
	public GoogleSecretManagerEnvironmentRepository googleSecretManagerEnvironmentRepository(
			GoogleSecretManagerEnvironmentRepositoryFactory factory,
			GoogleSecretManagerEnvironmentProperties environmentProperties) throws Exception {
		return factory.build(environmentProperties);
	}

}
