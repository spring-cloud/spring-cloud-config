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
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.health.Status;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Dave Syer
 * @author Marcos Barbero
 *
 */
@Disabled
public class ConfigServerHealthIndicatorTests {

	private ConfigurableEnvironment environment = mock(ConfigurableEnvironment.class);

	private ConfigServerHealthIndicator indicator = new ConfigServerHealthIndicator(this.environment,
			new ConfigClientHealthProperties());

	@Test
	public void testDefaultStatus() {
		MutablePropertySources sources = new MutablePropertySources();
		doReturn(sources).when(this.environment).getPropertySources();
		// UNKNOWN is better than DOWN since it doesn't stop the app from working
		assertThat(this.indicator.health().getStatus()).isEqualTo(Status.UNKNOWN);
	}

	@Test
	public void testExceptionStatus() {
		// TODO: is this needed any more
	}

	@Test
	public void testServerUp() {
		setupPropertySources();
		Map<String, Object> details = this.indicator.getHealth(true).getDetails();
		List<String> propertySources = (List) details.get("propertySources");
		assertThat(propertySources.contains("bootstrapProperties-test")).isTrue();
		assertThat(propertySources.contains("configserver:test")).isTrue();
		assertThat(propertySources.contains("configClient")).isTrue();
		assertThat(propertySources.size()).isEqualTo(3);
		assertThat(this.indicator.health().getStatus()).isEqualTo(Status.UP);
	}

	protected void setupPropertySources() {
		PropertySource<?> source = new MapPropertySource("configClient", Collections.emptyMap());
		PropertySource<?> configServerSource = new MapPropertySource("configserver:test", Collections.emptyMap());
		PropertySource<?> bootstrapSource = new MapPropertySource("bootstrapProperties-test", Collections.emptyMap());
		MutablePropertySources sources = new MutablePropertySources();
		sources.addFirst(source);
		sources.addFirst(bootstrapSource);
		sources.addFirst(configServerSource);
		doReturn(sources).when(this.environment).getPropertySources();
	}

	@Test
	public void healthIsCached() {
		setupPropertySources();

		// not cached
		assertThat(this.indicator.health().getStatus()).isEqualTo(Status.UP);

		// cached
		assertThat(this.indicator.health().getStatus()).isEqualTo(Status.UP);

		verify(this.environment, times(1)).getPropertySources();
	}

}
