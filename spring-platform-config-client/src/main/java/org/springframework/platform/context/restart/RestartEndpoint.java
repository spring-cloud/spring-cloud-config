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

package org.springframework.platform.context.restart;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.endpoint.AbstractEndpoint;
import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.util.ClassUtils;

/**
 * An endpoint that restarts the application context. Install as a bean and also register
 * a {@link RestartListener} with the {@link SpringApplication} that starts the context.
 * Those two components communicate via an {@link ApplicationEvent} and set up teh state
 * needed to restart the context.
 * 
 * @author Dave Syer
 *
 */
@ConfigurationProperties("endpoints.restart")
@ManagedResource
public class RestartEndpoint extends AbstractEndpoint<Boolean> implements
		ApplicationListener<ApplicationPreparedEvent> {

	private static Log logger = LogFactory.getLog(RestartEndpoint.class);

	public RestartEndpoint() {
		super("restart", true, false);
	}

	private ConfigurableApplicationContext context;

	private SpringApplication application;

	private String[] args;

	private ApplicationPreparedEvent event;

	@Override
	public void onApplicationEvent(ApplicationPreparedEvent input) {
		event = (ApplicationPreparedEvent) input;
		if (context == null) {
			context = event.getApplicationContext();
			args = event.getArgs();
			application = event.getSpringApplication();
		}
	}

	@Override
	public Boolean invoke() {
		try {
			restart();
			logger.info("Restarted");
			return true;
		} catch (Exception e) {
			if (logger.isDebugEnabled()) {
				logger.info("Could not restart", e);
			} else {
				logger.info("Could not restart: " + e.getMessage());
			}
			return false;
		}
	}
	
	public Endpoint<Boolean> getPauseEndpoint() {
		return new PauseEndpoint();
	}
	
	public Endpoint<Boolean> getResumeEndpoint() {
		return new ResumeEndpoint();
	}
	
	private class PauseEndpoint extends AbstractEndpoint<Boolean> {
		
		public PauseEndpoint() {
			super("pause", true, true);
		}

		@Override
		public Boolean invoke() {
			if (isRunning()) {
				pause();
				return true;
			}
			return false;
		}
	}

	private class ResumeEndpoint extends AbstractEndpoint<Boolean> {
		
		public ResumeEndpoint() {
			super("resume", true, true);
		}

		@Override
		public Boolean invoke() {
			if (!isRunning()) {
				resume();
				return true;
			}
			return false;
		}
	}

	@ManagedOperation
	public synchronized ConfigurableApplicationContext restart() {
		if (context != null) {
			context.close();
			// If running in a webapp then the context classloader is probably going to
			// die so we need to revert to a safe place before starting again
			overrideClassLoaderForRestart();
			context = application.run(args);
		}
		return context;
	}
	
	@ManagedAttribute
	public boolean isRunning() {
		if (context != null) {
			return context.isRunning();
		}
		return false;
	}

	@ManagedOperation
	public synchronized void pause() {
		if (context != null) {
			context.stop();
		}
	}

	@ManagedOperation
	public synchronized void resume() {
		if (context != null) {
			context.start();
		}
	}

	private void overrideClassLoaderForRestart() {
		ClassUtils.overrideThreadContextClassLoader(application.getClass().getClassLoader());
	}

}
