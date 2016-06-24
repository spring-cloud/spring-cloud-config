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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collections;

import org.junit.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

/**
 * @author Dave Syer
 * @author Marcos Barbero
 *
 */
public class ConfigServerHealthIndicatorTests {

	private ConfigServicePropertySourceLocator locator =
			mock(ConfigServicePropertySourceLocator.class);
	private Environment environment = mock(Environment.class);
	private ConfigServerHealthIndicator indicator = new ConfigServerHealthIndicator(
			locator, environment, new ConfigClientHealthProperties());

	@Test
	public void testDefaultStatus() {
		// UNKNOWN is better than DOWN since it doesn't stop the app from working
		assertEquals(Status.UNKNOWN, indicator.health().getStatus());
	}

	@Test
	public void testExceptionStatus() {
		doThrow(new IllegalStateException()).when(locator).locate(any(Environment.class));
		assertEquals(Status.DOWN, indicator.health().getStatus());
		verify(locator, times(1)).locate(any(Environment.class));
	}

	@Test
	public void testServerUp() {
		PropertySource<?> source = new MapPropertySource("foo", Collections.<String,Object>emptyMap());
		doReturn(source).when(locator).locate(any(Environment.class));
		assertEquals(Status.UP, indicator.health().getStatus());
		verify(locator, times(1)).locate(any(Environment.class));
	}

	@Test
	public void healthIsCached() {
		PropertySource<?> source = new MapPropertySource("foo", Collections.<String,Object>emptyMap());
		doReturn(source).when(locator).locate(any(Environment.class));

		// not cached
		assertEquals(Status.UP, indicator.health().getStatus());

		// cached
		assertEquals(Status.UP, indicator.health().getStatus());

		verify(locator, times(1)).locate(any(Environment.class));
	}


}
