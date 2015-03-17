/*
 * Copyright 2014-2015 the original author or authors.
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

package org.springframework.cloud.config.client;

import static org.junit.Assert.assertEquals;

import java.util.Collections;

import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.boot.actuate.health.Status;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * @author Dave Syer
 *
 */
public class ConfigServerHealthIndicatorTests {

	private ConfigServicePropertySourceLocator locator = Mockito
			.mock(ConfigServicePropertySourceLocator.class);
	private ConfigServerHealthIndicator indicator = new ConfigServerHealthIndicator(
			locator);

	@Test
	public void testDefaultStatus() {
		// UNKNOWN is better than DOWN since it doesn't stop the app from working
		assertEquals(Status.UNKNOWN, indicator.health().getStatus());
	}

	@Test
	public void testExceptionStatus() {
		Mockito.doThrow(new IllegalStateException()).when(locator).locate(Mockito.any(Environment.class));
		assertEquals(Status.DOWN, indicator.health().getStatus());
	}

	@Test
	public void testServerUp() {
		PropertySource<?> source = new MapPropertySource("foo", Collections.<String,Object>emptyMap());
		Mockito.doReturn(source).when(locator).locate(Mockito.any(Environment.class));
		assertEquals(Status.UP, indicator.health().getStatus());
	}

}
