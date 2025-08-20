/*
 * Copyright 2018-2022 the original author or authors.
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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.context.WebServerGracefulShutdownLifecycle;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.environment.EnvironmentController;
import org.springframework.cloud.context.properties.ConfigurationPropertiesRebinder;
import org.springframework.cloud.context.scope.refresh.RefreshScope;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.cloud.config.server.bootstrap")
public class ConfigServerBootstrapOverridesAutoConfiguration {

	@Bean
	ConfigServerBootstrapOverridesLifecycle configServerBootstrapOverridesLifecycle(ApplicationContext context) {
		return new ConfigServerBootstrapOverridesLifecycle(context);
	}

	/**
	 * SmartLifecycle that refreshes and rebinds beans needed for
	 * spring.cloud.config.server.overrides to work.
	 */
	private static class ConfigServerBootstrapOverridesLifecycle implements SmartLifecycle {

		private final ApplicationContext context;

		private volatile boolean running;

		ConfigServerBootstrapOverridesLifecycle(ApplicationContext context) {
			this.context = context;
		}

		@Override
		public void start() {
			this.running = true;
			ConfigurationPropertiesRebinder rebinder = context.getBean(ConfigurationPropertiesRebinder.class);
			rebinder.rebind(ConfigServerProperties.class);
			RefreshScope refreshScope = context.getBean(RefreshScope.class);
			refreshScope.refresh(EnvironmentController.class);
		}

		@Override
		public void stop() {
			this.running = false;
		}

		@Override
		public boolean isRunning() {
			return this.running;
		}

		@Override
		public int getPhase() {
			// Run before WebServerStartStopLifecycle
			return WebServerGracefulShutdownLifecycle.SMART_LIFECYCLE_PHASE - 3072;
		}

	}

}
