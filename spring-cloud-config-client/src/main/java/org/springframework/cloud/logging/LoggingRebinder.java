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
package org.springframework.cloud.logging;

import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.bind.RelaxedPropertyResolver;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * Listener that looks for {@link EnvironmentChangeEvent} and rebinds logger levels if any
 * changed.
 * 
 * @author Dave Syer
 *
 */
public class LoggingRebinder implements ApplicationListener<EnvironmentChangeEvent>,
		EnvironmentAware {

	private final Log logger = LogFactory.getLog(getClass());

	private Environment environment;

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void onApplicationEvent(EnvironmentChangeEvent event) {
		if (environment == null) {
			return;
		}
		LoggingSystem system = LoggingSystem.get(LoggingSystem.class.getClassLoader());
		setLogLevels(system, environment);
	}

	protected void setLogLevels(LoggingSystem system, Environment environment) {
		Map<String, Object> levels = new RelaxedPropertyResolver(environment)
				.getSubProperties("logging.level.");
		for (Entry<String, Object> entry : levels.entrySet()) {
			setLogLevel(system, environment, entry.getKey(), entry.getValue().toString());
		}
	}

	private void setLogLevel(LoggingSystem system, Environment environment, String name,
			String level) {
		try {
			if (name.equalsIgnoreCase("root")) {
				name = null;
			}
			level = environment.resolvePlaceholders(level);
			system.setLogLevel(name, LogLevel.valueOf(level));
		}
		catch (RuntimeException ex) {
			this.logger.error("Cannot set level: " + level + " for '" + name + "'");
		}
	}

}
