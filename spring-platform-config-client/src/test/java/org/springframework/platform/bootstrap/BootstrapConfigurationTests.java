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

package org.springframework.platform.bootstrap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.After;
import org.junit.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.platform.config.client.PropertySourceLocator;

/**
 * @author Dave Syer
 *
 */
public class BootstrapConfigurationTests {

	private ConfigurableApplicationContext context;

	@After
	public void close() {
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void picksUpAdditionalPropertySource() {
		context = new SpringApplicationBuilder().web(false).sources(
				BareConfiguration.class).run();
		assertEquals("bar", context.getEnvironment().getProperty("bootstrap.foo"));
		assertTrue(context.getEnvironment().getPropertySources().contains("bootstrap"));
	}

	@Test
	public void environmentEnrichedOnce() {
		context = new SpringApplicationBuilder().sources(BareConfiguration.class).environment(
				new StandardEnvironment()).child(BareConfiguration.class).web(false).run();
		assertEquals("bar", context.getEnvironment().getProperty("bootstrap.foo"));
		assertEquals(context.getEnvironment(), context.getParent().getEnvironment());
		MutablePropertySources sources = context.getEnvironment().getPropertySources();
		PropertySource<?> bootstrap = sources.get("bootstrap");
		assertNotNull(bootstrap);
		assertEquals(0, sources.precedenceOf(bootstrap));
	}

	@Test
	public void environmentEnrichedInParent() {
		context = new SpringApplicationBuilder().sources(BareConfiguration.class).child(
				BareConfiguration.class).web(false).run();
		assertEquals("bar", context.getEnvironment().getProperty("bootstrap.foo"));
		assertNotSame(context.getEnvironment(), context.getParent().getEnvironment());
		assertTrue(context.getEnvironment().getPropertySources().contains("bootstrap"));
		assertTrue(((ConfigurableEnvironment) context.getParent().getEnvironment()).getPropertySources().contains(
				"bootstrap"));
	}

	@Configuration
	protected static class BareConfiguration {
	}

	@Configuration
	protected static class PropertySourceConfiguration implements PropertySourceLocator {

		@Override
		public PropertySource<?> locate() {
			return new MapPropertySource("testBootstrap",
					Collections.<String, Object> singletonMap("bootstrap.foo", "bar"));
		}
	}

}
