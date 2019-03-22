/*
 * Copyright 2014-2019 the original author or authors.
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

import java.util.Collections;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * Normally you don't want the config server to be a config client itself, so this
 * listener disables the config client unless <code>spring.cloud.config.enabled</code> is
 * explicitly "true". It has to be "true" at the time this listener is fired, which means
 * <b>before</b> the <code>bootstrap.yml</code> is parsed, which in turn means to you need
 * to launch the application with an existing primed {@link Environment} (e.g. via System
 * properties or a {@link SpringApplicationBuilder}). This is the same rule of precedence
 * as for anything else affecting the bootstrap process itself, e.g. setting
 * <code>spring.cloud.bootstrap.name</code> to something other than "bootstrap".
 * <p>
 * N.B. a config server can always be an "embedded" config client (using its own config
 * repository as a property source) if you set
 * <code>spring.cloud.config.server.bootstrap=true</code> in <code>bootstrap.yml</code>.
 * This listener is only to prevent it from using HTTP to contact itself.
 *
 * @author Dave Syer
 *
 */
public class ConfigServerBootstrapApplicationListener
		implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

	/**
	 * Default order of the bootstrap application listener.
	 */
	public static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 4;

	private int order = DEFAULT_ORDER;

	private PropertySource<?> propertySource = new MapPropertySource("configServerClient",
			Collections.<String, Object>singletonMap("spring.cloud.config.enabled",
					"false"));

	@Override
	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
		ConfigurableEnvironment environment = event.getEnvironment();
		if (!environment.resolvePlaceholders("${spring.cloud.config.enabled:false}")
				.equalsIgnoreCase("true")) {
			if (!environment.getPropertySources()
					.contains(this.propertySource.getName())) {
				environment.getPropertySources().addLast(this.propertySource);
			}
		}
	}

}
