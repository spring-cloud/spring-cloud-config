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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.config.client.ConfigClientProperties;
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
 *
 */
@Configuration
public class ConfigServerBootstrapConfiguration {

	@ConditionalOnProperty("spring.cloud.config.server.bootstrap")
	@Import(ConfigServerConfiguration.class)
	protected static class LocalPropertySourceLocatorConfiguration {

		@Autowired
		private EnvironmentRepository repository;

		@Autowired
		private ConfigClientProperties client;

		@Autowired
		private ConfigServerProperties server;

		@Bean
		public EnvironmentRepositoryPropertySourceLocator environmentRepositoryPropertySourceLocator() {
			String label = StringUtils.hasText(client.getLabel()) ? client.getLabel()
					: server.getDefaultLabel();
			return new EnvironmentRepositoryPropertySourceLocator(repository,
					client.getName(), client.getProfile(), label);
		}

	}

}
