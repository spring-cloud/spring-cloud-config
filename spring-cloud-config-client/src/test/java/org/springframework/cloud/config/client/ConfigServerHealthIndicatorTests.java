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

package org.springframework.cloud.config.client;

import java.util.Collections;

import org.junit.Test;

import org.springframework.boot.actuate.health.Status;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Dave Syer
 * @author Marcos Barbero
 *
 */
public class ConfigServerHealthIndicatorTests {

	private ConfigServicePropertySourceLocator locator = mock(
			ConfigServicePropertySourceLocator.class);

	private Environment environment = mock(Environment.class);

	private ConfigServerHealthIndicator indicator = new ConfigServerHealthIndicator(
			this.locator, this.environment, new ConfigClientHealthProperties());

	@Test
	public void testDefaultStatus() {
		// UNKNOWN is better than DOWN since it doesn't stop the app from working
		assertThat(this.indicator.health().getStatus()).isEqualTo(Status.UNKNOWN);
	}

	@Test
	public void testExceptionStatus() {
		doThrow(new IllegalStateException()).when(this.locator)
				.locate(any(Environment.class));
		assertThat(this.indicator.health().getStatus()).isEqualTo(Status.DOWN);
		verify(this.locator, times(1)).locate(any(Environment.class));
	}

	@Test
	public void testServerUp() {
		PropertySource<?> source = new MapPropertySource("foo",
				Collections.<String, Object>emptyMap());
		doReturn(source).when(this.locator).locate(any(Environment.class));
		assertThat(this.indicator.health().getStatus()).isEqualTo(Status.UP);
		verify(this.locator, times(1)).locate(any(Environment.class));
	}

	@Test
	public void healthIsCached() {
		PropertySource<?> source = new MapPropertySource("foo",
				Collections.<String, Object>emptyMap());
		doReturn(source).when(this.locator).locate(any(Environment.class));

		// not cached
		assertThat(this.indicator.health().getStatus()).isEqualTo(Status.UP);

		// cached
		assertThat(this.indicator.health().getStatus()).isEqualTo(Status.UP);

		verify(this.locator, times(1)).locate(any(Environment.class));
	}

}
