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

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * @author Dave Syer
 *
 */
public class ConfigServicePropertySourceLocatorTests {
	
	private ConfigServicePropertySourceLocator locator = new ConfigServicePropertySourceLocator();

	@Test
	public void vanilla() {
		locator.setUri("http://localhost:9999");
		locator.setPassword("secret");
		assertEquals("http://localhost:9999", locator.getUri());
		assertEquals("user", locator.getUsername());
		assertEquals("secret", locator.getPassword());
	}

	@Test
	public void uriCreds() {
		locator.setUri("http://foo:bar@localhost:9999");
		assertEquals("http://localhost:9999", locator.getUri());
		assertEquals("foo", locator.getUsername());
		assertEquals("bar", locator.getPassword());
	}

	@Test
	public void overridePassword() {
		locator.setUri("http://foo:bar@localhost:9999");
		locator.setPassword("secret");
		assertEquals("http://localhost:9999", locator.getUri());
		assertEquals("foo", locator.getUsername());
		assertEquals("secret", locator.getPassword());
	}

}
