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
import org.junit.rules.ExpectedException;
import org.springframework.boot.test.util.EnvironmentTestUtils;
import org.springframework.cloud.config.client.ConfigClientProperties.Credentials;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.junit.Rule;

/**
 * @author Dave Syer
 *
 */
public class ConfigClientPropertiesTests {

	private ConfigClientProperties locator = new ConfigClientProperties(
			new StandardEnvironment());

	@Rule
	public ExpectedException expected = ExpectedException.none();

	@Test
	public void vanilla() {
		locator.setUri(new String[] { "http://localhost:9999" });
		locator.setPassword("secret");
		Credentials credentials = locator.getCredentials(0);
		assertEquals("http://localhost:9999", credentials.getUri());
		assertEquals("user", credentials.getUsername());
		assertEquals("secret", credentials.getPassword());
	}

	@Test
	public void uriCreds() {
		locator.setUri(new String[] { "http://foo:bar@localhost:9999" });
		Credentials credentials = locator.getCredentials(0);
		assertEquals("http://localhost:9999", credentials.getUri());
		assertEquals("foo", credentials.getUsername());
		assertEquals("bar", credentials.getPassword());
	}

	@Test
	public void explicitPassword() {
		locator.setUri(new String[] { "http://foo:bar@localhost:9999" });
		locator.setPassword("secret");
		Credentials credentials = locator.getCredentials(0);
		assertEquals("http://localhost:9999", credentials.getUri());
		assertEquals("foo", credentials.getUsername());
		assertEquals("secret", credentials.getPassword());
	}

	@Test
	public void testIfNoColonPresentInUriCreds() {
		locator.setUri(new String[] { "http://foobar@localhost:9999" });
		locator.setPassword("secret");
		Credentials credentials = locator.getCredentials(0);
		assertEquals("http://localhost:9999", credentials.getUri());
		assertEquals("foobar", credentials.getUsername());
		assertEquals("secret", credentials.getPassword());
	}

	@Test
	public void testIfColonPresentAtTheEndInUriCreds() {
		locator.setUri(new String[] { "http://foobar:@localhost:9999" });
		locator.setPassword("secret");
		Credentials credentials = locator.getCredentials(0);
		assertEquals("http://localhost:9999", credentials.getUri());
		assertEquals("foobar", credentials.getUsername());
		assertEquals("secret", credentials.getPassword());
	}

	@Test
	public void testIfColonPresentAtTheStartInUriCreds() {
		locator.setUri(new String[] { "http://:foobar@localhost:9999" });
		Credentials credentials = locator.getCredentials(0);
		assertEquals("http://localhost:9999", credentials.getUri());
		assertEquals("", credentials.getUsername());
		assertEquals("foobar", credentials.getPassword());
	}

	@Test
	public void testIfColonPresentAtTheStartAndEndInUriCreds() {
		locator.setUri(new String[] { "http://:foobar:@localhost:9999" });
		Credentials credentials = locator.getCredentials(0);
		assertEquals("http://localhost:9999", credentials.getUri());
		assertEquals("", credentials.getUsername());
		assertEquals("foobar:", credentials.getPassword());
	}

	@Test
	public void testIfSpacePresentAsUriCreds() {
		locator.setUri(new String[] { "http://  @localhost:9999" });
		locator.setPassword("secret");
		Credentials credentials = locator.getCredentials(0);
		assertEquals("http://localhost:9999", credentials.getUri());
		assertEquals("  ", credentials.getUsername());
		assertEquals("secret", credentials.getPassword());
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
		ConfigClientProperties properties = new ConfigClientProperties(
				new MockEnvironment());

		properties.setUri(
				new String[] { "https://userInfoName:userInfoPW@localhost:8888/" });
		properties.setUsername("explicitName");
		properties.setPassword("explicitPW");
		Credentials credentials = properties.getCredentials(0);
		assertThat(credentials.getPassword(), equalTo("explicitPW"));
		assertThat(credentials.getUsername(), equalTo("explicitName"));
	}

	@Test
	public void checkIfExceptionThrownForNegativeIndex() {
		locator.setUri(new String[] { "http://localhost:8888", "http://localhost:8889" });
		expected.expect(IllegalStateException.class);
		expected.expectMessage("Trying to access an invalid array index");
		Credentials credentials = locator.getCredentials(-1);
	}

	@Test
	public void checkIfExceptionThrownForPositiveInvalidIndex() {
		locator.setUri(new String[] { "http://localhost:8888", "http://localhost:8889" });
		expected.expect(IllegalStateException.class);
		expected.expectMessage("Trying to access an invalid array index");
		Credentials credentials = locator.getCredentials(3);
	}

	@Test
	public void checkIfExceptionThrownForIndexEqualToLength() {
		locator.setUri(new String[] { "http://localhost:8888", "http://localhost:8889" });
		expected.expect(IllegalStateException.class);
		expected.expectMessage("Trying to access an invalid array index");
		Credentials credentials = locator.getCredentials(2);
	}

}
