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
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * @author Dave Syer
 *
 */
@Configuration
@ComponentScan
public class ConfigServerConfiguration {

	@Configuration
	@Profile("native")
	protected static class NativeRepositoryConfiguration {
		@Autowired
		private ConfigurableEnvironment environment;

		@Bean
		public NativeEnvironmentRepository repository() {
			return new NativeEnvironmentRepository(environment);
		}
	}

	@Configuration
	@Profile("!native")
	protected static class GitRepositoryConfiguration {
		@Autowired
		private ConfigurableEnvironment environment;

		@Bean
		@ConfigurationProperties("spring.cloud.config.server")
		public JGitEnvironmentRepository repository() {
			return new JGitEnvironmentRepository(environment);
		}
	}
}