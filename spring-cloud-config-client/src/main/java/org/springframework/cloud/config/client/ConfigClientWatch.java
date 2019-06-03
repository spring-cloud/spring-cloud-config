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

package org.springframework.cloud.config.client;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;

import static org.springframework.util.StringUtils.hasText;

/**
 * @author Spencer Gibb
 */
public class ConfigClientWatch implements Closeable, EnvironmentAware {

	private static Log log = LogFactory.getLog(ConfigServicePropertySourceLocator.class);

	private final AtomicBoolean running = new AtomicBoolean(false);

	private final ContextRefresher refresher;

	private Environment environment;

	public ConfigClientWatch(ContextRefresher refresher) {
		this.refresher = refresher;
	}

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	@PostConstruct
	public void start() {
		this.running.compareAndSet(false, true);
	}

	@Scheduled(initialDelayString = "${spring.cloud.config.watch.initialDelay:180000}",
			fixedDelayString = "${spring.cloud.config.watch.delay:500}")
	public void watchConfigServer() {
		if (this.running.get()) {
			String newState = this.environment.getProperty("config.client.state");
			String oldState = ConfigClientStateHolder.getState();

			// only refresh if state has changed
			if (stateChanged(oldState, newState)) {
				ConfigClientStateHolder.setState(newState);
				this.refresher.refresh();
			}
		}
	}

	/* for testing */ boolean stateChanged(String oldState, String newState) {
		return (!hasText(oldState) && hasText(newState))
				|| (hasText(oldState) && !oldState.equals(newState));
	}

	@Override
	public void close() {
		this.running.compareAndSet(true, false);
	}

}
