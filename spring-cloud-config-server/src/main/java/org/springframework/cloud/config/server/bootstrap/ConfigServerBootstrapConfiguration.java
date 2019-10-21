/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.config.server.bootstrap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.config.EnvironmentRepositoryConfiguration;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.environment.EnvironmentRepositoryPropertySourceLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.util.StringUtils;

/**
 * Bootstrap configuration to fetch external configuration from a (possibly remote)
 * {@link EnvironmentRepository}. Off by default because it can delay startup, but can be
 * enabled with <code>spring.cloud.config.server.bootstrap=true</code>. This would be
 * useful, for example, if the config server were embedded in another app that wanted to
 * be configured from the same repository as all the other clients.
 *
 * @author Dave Syer
 * @author Roy Clarkson
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.cloud.config.server.bootstrap")
public class ConfigServerBootstrapConfiguration {

	@EnableConfigurationProperties(ConfigServerProperties.class)
	@Import({ EnvironmentRepositoryConfiguration.class })
	protected static class LocalPropertySourceLocatorConfiguration {

		@Autowired
		private EnvironmentRepository repository;

		@Autowired
		private ConfigClientProperties client;

		@Autowired
		private ConfigServerProperties server;

		@Bean
		public EnvironmentRepositoryPropertySourceLocator environmentRepositoryPropertySourceLocator() {
			return new EnvironmentRepositoryPropertySourceLocator(this.repository,
					this.client.getName(), this.client.getProfile(), getDefaultLabel());
		}

		private String getDefaultLabel() {
			if (StringUtils.hasText(this.client.getLabel())) {
				return this.client.getLabel();
			}
			else if (StringUtils.hasText(this.server.getDefaultLabel())) {
				return this.server.getDefaultLabel();
			}
			return null;
		}

	}

}
