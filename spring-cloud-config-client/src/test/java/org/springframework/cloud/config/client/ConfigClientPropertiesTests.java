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
package org.springframework.cloud.config.client;

import org.junit.Test;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * @author Dave Syer
 *
 */
public class ConfigClientPropertiesTests {

	private ConfigClientProperties locator = new ConfigClientProperties(
			new StandardEnvironment());

	@Test
	public void vanilla() {
		locator.setUri("http://localhost:9999");
		locator.setPassword("secret");
		assertEquals("http://localhost:9999", locator.getRawUri());
		assertEquals("user", locator.getUsername());
		assertEquals("secret", locator.getPassword());
	}

	@Test
	public void uriCreds() {
		locator.setUri("http://foo:bar@localhost:9999");
		assertEquals("http://localhost:9999", locator.getRawUri());
		assertEquals("foo", locator.getUsername());
		assertEquals("bar", locator.getPassword());
	}

	@Test
	public void explicitPassword() {
		locator.setUri("http://foo:bar@localhost:9999");
		locator.setPassword("secret");
		assertEquals("http://localhost:9999", locator.getRawUri());
		assertEquals("foo", locator.getUsername());
		assertEquals("secret", locator.getPassword());
	}

	@Test
	public void changeNameInOverride() {
		locator.setName("one");
		ConfigurableEnvironment environment = new StandardEnvironment();
		EnvironmentTestUtils.addEnvironment(environment, "spring.application.name:two");
		ConfigClientProperties override = locator.override(environment);
		assertEquals("two", override.getName());
	}

	@Test
	public void testThatExplicitUsernamePasswordTakePrecedence() {
		ConfigClientProperties properties =
				new ConfigClientProperties(new MockEnvironment());

		properties.setUri("https://userInfoName:userInfoPW@localhost:8888/");
		properties.setUsername("explicitName");
		properties.setPassword("explicitPW");

		assertThat(properties.getPassword(), equalTo("explicitPW"));
		assertThat(properties.getUsername(), equalTo("explicitName"));
	}
}
