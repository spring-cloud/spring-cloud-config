/*
 * Copyright 2015-2019 the original author or authors.
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.bus.BusProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author Dave Syer
 * @author Will Boyd
 *
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
@Import(FileMonitorConfiguration.class)
public class EnvironmentMonitorAutoConfiguration {

	@Autowired(required = false)
	private List<PropertyPathNotificationExtractor> extractors;

	@Bean
	public PropertyPathEndpoint propertyPathEndpoint(BusProperties busProperties) {
		return new PropertyPathEndpoint(
				new CompositePropertyPathNotificationExtractor(this.extractors),
				busProperties.getId());
	}

	@Configuration(proxyBeanMethods = false)
	protected static class PropertyPathNotificationExtractorConfiguration {

		@Bean
		@ConditionalOnProperty(
				value = "spring.cloud.config.server.monitor.github.enabled",
				havingValue = "true", matchIfMissing = true)
		public GithubPropertyPathNotificationExtractor githubPropertyPathNotificationExtractor() {
			return new GithubPropertyPathNotificationExtractor();
		}

		@Bean
		@ConditionalOnProperty(
				value = "spring.cloud.config.server.monitor.gitlab.enabled",
				havingValue = "true", matchIfMissing = true)
		public GitlabPropertyPathNotificationExtractor gitlabPropertyPathNotificationExtractor() {
			return new GitlabPropertyPathNotificationExtractor();
		}

		@Bean
		@ConditionalOnProperty(
				value = "spring.cloud.config.server.monitor.bitbucket.enabled",
				havingValue = "true", matchIfMissing = true)
		public BitbucketPropertyPathNotificationExtractor bitbucketPropertyPathNotificationExtractor() {
			return new BitbucketPropertyPathNotificationExtractor();
		}

		@Bean
		@ConditionalOnProperty(value = "spring.cloud.config.server.monitor.gitea.enabled",
				havingValue = "true", matchIfMissing = true)
		public GiteaPropertyPathNotificationExtractor giteaPropertyPathNotificationExtractor() {
			return new GiteaPropertyPathNotificationExtractor();
		}

		@Bean
		@ConditionalOnProperty(value = "spring.cloud.config.server.monitor.gitee.enabled",
				havingValue = "true", matchIfMissing = true)
		public GiteePropertyPathNotificationExtractor giteePropertyPathNotificationExtractor() {
			return new GiteePropertyPathNotificationExtractor();
		}

		@Bean
		@ConditionalOnProperty(value = "spring.cloud.config.server.monitor.gogs.enabled",
				havingValue = "true", matchIfMissing = true)
		public GogsPropertyPathNotificationExtractor gogsPropertyPathNotificationExtractor() {
			return new GogsPropertyPathNotificationExtractor();
		}

	}

}
