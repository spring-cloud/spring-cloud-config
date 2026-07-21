/*
 * Copyright 2015-present the original author or authors.
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

package org.springframework.cloud.config.monitor;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.bus.BusProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.util.StringUtils;

/**
 * @author Dave Syer
 * @author Will Boyd
 *
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
@Import(FileMonitorConfiguration.class)
@EnableConfigurationProperties(MonitorConfigurationProperties.class)
public class EnvironmentMonitorAutoConfiguration {

	private static FilterRegistrationBean<WebhookValidatorFilter> createRegistrationBean(
			MonitorConfigurationProperties properties, List<WebhookRequestValidator> validators,
			String monitorEndpointPath) {
		FilterRegistrationBean<WebhookValidatorFilter> registrationBean = new FilterRegistrationBean<>();
		registrationBean.setFilter(new WebhookValidatorFilter(validators));
		// There was a bug in the PropertyPathEndpoint where the property used to set
		// the path was missing "server" in the prefix
		// To avoid breaking existing installations we will use the old property if it
		// is set
		// That property is deprecated and will be removed in a future release, at
		// which point we can remove this
		String path = StringUtils.hasText(monitorEndpointPath) ? monitorEndpointPath
				: properties.getEndpoint().getPath();
		registrationBean.addUrlPatterns(path);
		return registrationBean;
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(BusProperties.class)
	protected static class BusPropertyPathConfiguration {

		@Autowired(required = false)
		private List<PropertyPathNotificationExtractor> extractors;

		@Bean
		@ConditionalOnBean(BusProperties.class)
		public PropertyPathEndpoint propertyPathEndpoint(BusProperties busProperties,
				MonitorConfigurationProperties monitorProperties) {
			return new PropertyPathEndpoint(new CompositePropertyPathNotificationExtractor(this.extractors),
					busProperties.getId(), monitorProperties.getMaxDashes());
		}

		// TODO: With the current implementation bus can't be disabled
		@Bean
		@ConditionalOnMissingBean(BusProperties.class)
		public PropertyPathEndpoint noBusBeanPropertyPathEndpoint(
				@Value("${spring.cloud.bus.id:application}") String id,
				MonitorConfigurationProperties monitorProperties) {
			return new PropertyPathEndpoint(new CompositePropertyPathNotificationExtractor(this.extractors), id,
					monitorProperties.getMaxDashes());
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingClass("org.springframework.cloud.bus.BusProperties")
	protected static class NoBusPropertyPathConfiguration {

		@Bean
		public PropertyPathEndpoint noBusPropertyPathEndpoint(@Value("${spring.cloud.bus.id:application}") String id,
				@Autowired(required = false) List<PropertyPathNotificationExtractor> extractors,
				MonitorConfigurationProperties monitorProperties) {
			return new PropertyPathEndpoint(new CompositePropertyPathNotificationExtractor(extractors), id,
					monitorProperties.getMaxDashes());
		}

	}

	@Configuration(proxyBeanMethods = false)
	protected static class PropertyPathNotificationExtractorConfiguration {

		@Bean
		@ConditionalOnProperty(value = "spring.cloud.config.server.monitor.github.enabled", havingValue = "true",
				matchIfMissing = true)
		public GithubPropertyPathNotificationExtractor githubPropertyPathNotificationExtractor() {
			return new GithubPropertyPathNotificationExtractor();
		}

		@Bean
		@ConditionalOnProperty(value = "spring.cloud.config.server.monitor.github.enabled", havingValue = "true",
				matchIfMissing = true)
		@ConditionalOnProperty(value = "spring.cloud.config.server.monitor.github.validationEnabled",
				havingValue = "true", matchIfMissing = true)
		@ConditionalOnProperty("spring.cloud.config.server.monitor.github.webhookSecret")
		public GithubWebhookRequestValidator githubWebhookRequestValidator(MonitorConfigurationProperties properties) {
			return new GithubWebhookRequestValidator(properties.getGithub().getWebhookSecret());
		}

		@Bean
		@ConditionalOnProperty(value = "spring.cloud.config.server.monitor.gogs.enabled", havingValue = "true",
				matchIfMissing = true)
		@ConditionalOnProperty(value = "spring.cloud.config.server.monitor.gogs.validationEnabled",
				havingValue = "true", matchIfMissing = true)
		@ConditionalOnProperty("spring.cloud.config.server.monitor.gogs.webhookSecret")
		public GogsWebhookRequestValidator gogsWebhookRequestValidator(MonitorConfigurationProperties properties) {
			return new GogsWebhookRequestValidator(properties.getGogs().getWebhookSecret());
		}

		@Bean
		@ConditionalOnProperty(value = "spring.cloud.config.server.monitor.gitlab.enabled", havingValue = "true",
				matchIfMissing = true)
		public GitlabPropertyPathNotificationExtractor gitlabPropertyPathNotificationExtractor() {
			return new GitlabPropertyPathNotificationExtractor();
		}

		@Bean
		@ConditionalOnProperty(value = "spring.cloud.config.server.monitor.gitlab.enabled", havingValue = "true",
				matchIfMissing = true)
		@ConditionalOnProperty(value = "spring.cloud.config.server.monitor.gitlab.validationEnabled",
				havingValue = "true", matchIfMissing = true)
		@ConditionalOnProperty("spring.cloud.config.server.monitor.gitlab.webhookSecret")
		public GitlabWebhookRequestValidator gitlabWebhookRequestValidator(MonitorConfigurationProperties properties) {
			return new GitlabWebhookRequestValidator(properties.getGitlab().getWebhookSecret());
		}

		@Bean
		@ConditionalOnProperty(value = "spring.cloud.config.server.monitor.bitbucket.enabled", havingValue = "true",
				matchIfMissing = true)
		public BitbucketPropertyPathNotificationExtractor bitbucketPropertyPathNotificationExtractor() {
			return new BitbucketPropertyPathNotificationExtractor();
		}

		@Bean
		@ConditionalOnProperty(value = "spring.cloud.config.server.monitor.gitea.enabled", havingValue = "true",
				matchIfMissing = true)
		public GiteaPropertyPathNotificationExtractor giteaPropertyPathNotificationExtractor() {
			return new GiteaPropertyPathNotificationExtractor();
		}

		@Bean
		@ConditionalOnProperty(value = "spring.cloud.config.server.monitor.gitea.enabled", havingValue = "true",
				matchIfMissing = true)
		@ConditionalOnProperty(value = "spring.cloud.config.server.monitor.gitea.validationEnabled",
				havingValue = "true", matchIfMissing = true)
		@ConditionalOnProperty("spring.cloud.config.server.monitor.gitea.webhookSecret")
		public GiteaWebhookRequestValidator giteaWebhookRequestValidator(MonitorConfigurationProperties properties) {
			return new GiteaWebhookRequestValidator(properties.getGitea().getWebhookSecret());
		}

		@Bean
		@ConditionalOnProperty(value = "spring.cloud.config.server.monitor.gitee.enabled", havingValue = "true",
				matchIfMissing = true)
		public GiteePropertyPathNotificationExtractor giteePropertyPathNotificationExtractor() {
			return new GiteePropertyPathNotificationExtractor();
		}

		@Bean
		@ConditionalOnProperty(value = "spring.cloud.config.server.monitor.gitee.enabled", havingValue = "true",
				matchIfMissing = true)
		@ConditionalOnProperty(value = "spring.cloud.config.server.monitor.gitee.validationEnabled",
				havingValue = "true", matchIfMissing = true)
		@ConditionalOnProperty("spring.cloud.config.server.monitor.gitee.webhookSecret")
		public GiteeWebhookRequestValidator giteeWebhookRequestValidator(MonitorConfigurationProperties properties) {
			return new GiteeWebhookRequestValidator(properties.getGitee().getWebhookSecret());
		}

		@Bean
		@ConditionalOnProperty(value = "spring.cloud.config.server.monitor.gogs.enabled", havingValue = "true",
				matchIfMissing = true)
		public GogsPropertyPathNotificationExtractor gogsPropertyPathNotificationExtractor() {
			return new GogsPropertyPathNotificationExtractor();
		}

		@Bean
		@ConditionalOnProperty(value = "spring.cloud.config.server.monitor.bitbucket.enabled", havingValue = "true",
				matchIfMissing = true)
		@ConditionalOnProperty(value = "spring.cloud.config.server.monitor.bitbucket.validationEnabled",
				havingValue = "true", matchIfMissing = true)
		@ConditionalOnProperty("spring.cloud.config.server.monitor.bitbucket.webhookSecret")
		public BitbucketWebhookRequestValidator bitbucketWebhookRequestValidator(
				MonitorConfigurationProperties properties) {
			return new BitbucketWebhookRequestValidator(properties.getBitbucket().getWebhookSecret());
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(name = "org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties")
	protected static class WithSecurityWebhookValidatorFilterConfiguration {

		@Bean
		@ConditionalOnProperty(value = "spring.cloud.config.server.monitor.validation-filter-enabled",
				havingValue = "true", matchIfMissing = true)
		public FilterRegistrationBean<WebhookValidatorFilter> webhookValidatorFilter(
				MonitorConfigurationProperties properties, List<WebhookRequestValidator> validators,
				@Value("${spring.cloud.config.monitor.endpoint.path:}") String monitorEndpointPath) {
			FilterRegistrationBean<WebhookValidatorFilter> registrationBean = createRegistrationBean(properties,
					validators, monitorEndpointPath);
			// Run before Spring Security so invalid requests are rejected before the
			// security filter chain processes them, and so the body is buffered before
			// any filter can consume it.
			registrationBean.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1);
			return registrationBean;
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingClass("org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties")
	protected static class WithoutSecurityWebhookValidatorFilterConfiguration {

		@Bean
		@ConditionalOnProperty(value = "spring.cloud.config.server.monitor.validation-filter-enabled",
				havingValue = "true", matchIfMissing = true)
		public FilterRegistrationBean<WebhookValidatorFilter> webhookValidatorFilter(
				MonitorConfigurationProperties properties, List<WebhookRequestValidator> validators,
				@Value("${spring.cloud.config.monitor.endpoint.path:}") String monitorEndpointPath) {
			return createRegistrationBean(properties, validators, monitorEndpointPath);
		}

	}

}
