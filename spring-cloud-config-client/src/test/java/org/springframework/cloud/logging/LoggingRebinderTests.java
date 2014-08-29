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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.EnvironmentTestUtils;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;

/**
 * @author Dave Syer
 *
 */
public class LoggingRebinderTests {

	private LoggingRebinder rebinder = new LoggingRebinder();
	private Logger logger = LoggerFactory.getLogger("org.springframework.web");
	
	@After
	public void reset() {
		LoggingSystem.get(getClass().getClassLoader()).setLogLevel("org.springframework.web", LogLevel.INFO);
	}

	@Test
	public void logLevelsChanged() {
		assertFalse(logger.isTraceEnabled());
		StandardEnvironment environment = new StandardEnvironment();
		EnvironmentTestUtils.addEnvironment(environment, "logging.level.org.springframework.web=TRACE");
		rebinder.setEnvironment(environment);
		rebinder.onApplicationEvent(new EnvironmentChangeEvent(Collections.singleton("logging.level.org.springframework.web")));
		assertTrue(logger.isTraceEnabled());
	}

}
