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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;


import org.junit.Test;
import org.springframework.boot.test.EnvironmentTestUtils;
import org.springframework.cloud.config.client.ConfigClientProperties.ConfigSelectionProperties;
import org.springframework.cloud.config.client.ConfigClientProperties.ConfigServerEndpoint;
import org.springframework.cloud.config.client.ConfigClientProperties.Credentials;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;


/**
 * @author Dave Syer
 * @author Felix Kissel
 *
 */
public class ConfigClientPropertiesTests {

	private ConfigClientProperties locator = new ConfigClientProperties(
			new StandardEnvironment());

	@Test
	public void vanilla() {
		locator.setUri("http://localhost:9999");
		locator.setPassword("secret");
		List<ConfigServerEndpoint> configServerEndpoints = locator.getConfigServerEndpoints();
		assertEquals(1, configServerEndpoints.size());
		ConfigServerEndpoint configServerEndpoint = configServerEndpoints.get(0);
		assertEquals("http://localhost:9999", configServerEndpoint.getRawUri());
		Credentials credentials = configServerEndpoint.getCredentials();
		assertNotNull(credentials);
		assertEquals(ConfigServerEndpoint.DEFAULT_USERNAME_user,
				credentials.getUsername());
		assertEquals("secret", credentials.getPassword());
	}

	@Test
	public void uriCreds() {
		locator.setUri("http://foo:bar@localhost:9999");
		
		List<ConfigServerEndpoint> configServerEndpoints = locator.getConfigServerEndpoints();
		assertEquals(1, configServerEndpoints.size());
		ConfigServerEndpoint configServerEndpoint = configServerEndpoints.get(0);
		assertEquals("http://localhost:9999", configServerEndpoint.getRawUri());
		Credentials credentials = configServerEndpoint.getCredentials();
		assertNotNull(credentials);
		assertEquals("foo", credentials.getUsername());
		assertEquals("bar", credentials.getPassword());
	}

	@Test
	public void overrideUriPassword() {
		locator.setUri("http://foo:bar@localhost:9999");
		locator.setPassword("secret");
		
		List<ConfigServerEndpoint> configServerEndpoints = locator.getConfigServerEndpoints();
		assertEquals(1, configServerEndpoints.size());
		ConfigServerEndpoint configServerEndpoint = configServerEndpoints.get(0);
		assertEquals("http://localhost:9999", configServerEndpoint.getRawUri());
		Credentials credentials = configServerEndpoint.getCredentials();
		assertNotNull(credentials);
		assertEquals("foo", credentials.getUsername());
		assertEquals("secret", credentials.getPassword());
	}
	
	@Test
	public void explicitCreds() {
		locator.setUri("http://foo:bar@localhost:9999");
		locator.setUsername("username");
		locator.setPassword("secret");
		
		List<ConfigServerEndpoint> configServerEndpoints = locator.getConfigServerEndpoints();
		assertEquals(1, configServerEndpoints.size());
		ConfigServerEndpoint configServerEndpoint = configServerEndpoints.get(0);
		assertEquals("http://localhost:9999", configServerEndpoint.getRawUri());
		Credentials credentials = configServerEndpoint.getCredentials();
		assertNotNull(credentials);
		assertEquals("username", credentials.getUsername());
		assertEquals("secret", credentials.getPassword());
	}
	
	@Test
	public void ignoreExplicitUsernameWithoutExplicitPassword() {
		locator.setUri("http://foo:bar@localhost:9999");
		locator.setUsername("username");

		List<ConfigServerEndpoint> configServerEndpoints = locator.getConfigServerEndpoints();
		assertEquals(1, configServerEndpoints.size());
		ConfigServerEndpoint configServerEndpoint = configServerEndpoints.get(0);
		assertEquals("http://localhost:9999", configServerEndpoint.getRawUri());
		Credentials credentials = configServerEndpoint.getCredentials();
		assertNotNull(credentials);
		assertEquals("foo", credentials.getUsername());
		assertEquals("bar", credentials.getPassword());
	}
	
	@Test
	public void explicitPassword() {
		locator.setUri("http://foo@localhost:9999");
		locator.setPassword("secret");
		
		List<ConfigServerEndpoint> configServerEndpoints = locator.getConfigServerEndpoints();
		assertEquals(1, configServerEndpoints.size());
		ConfigServerEndpoint configServerEndpoint = configServerEndpoints.get(0);
		assertEquals("http://localhost:9999", configServerEndpoint.getRawUri());
		Credentials credentials = configServerEndpoint.getCredentials();
		assertNotNull(credentials);
		assertEquals("foo", credentials.getUsername());
		assertEquals("secret", credentials.getPassword());
	}

	@Test
	public void changeNameInOverride() {
		locator.setName("one");
		ConfigurableEnvironment environment = new StandardEnvironment();
		EnvironmentTestUtils.addEnvironment(environment, "spring.application.name:two");
		ConfigSelectionProperties configSelectionProperties = locator
				.getConfigSelectionProperties(environment);
		assertEquals("two", configSelectionProperties.getName());
	}
	
	@Test
	public void ignoreUriUsernameWithoutPassword() {
		locator.setUri("http://foo@localhost:9999");
		
		List<ConfigServerEndpoint> configServerEndpoints = locator.getConfigServerEndpoints();
		assertEquals(1, configServerEndpoints.size());
		ConfigServerEndpoint configServerEndpoint = configServerEndpoints.get(0);
		assertEquals("http://localhost:9999", configServerEndpoint.getRawUri());
		assertNull(configServerEndpoint.getCredentials());
	}
	
	@Test
	public void kickoutEmptyUriCreds() {
		locator.setUri("http://:@localhost:9999");
		
		List<ConfigServerEndpoint> configServerEndpoints = locator.getConfigServerEndpoints();
		assertEquals(1, configServerEndpoints.size());
		ConfigServerEndpoint configServerEndpoint = configServerEndpoints.get(0);
		assertEquals("http://localhost:9999", configServerEndpoint.getRawUri());
		assertNull(configServerEndpoint.getCredentials());
	}
	
	@Test
	public void ignoreUriCredsWithMissingPassword() {
		locator.setUri("http://foo:@localhost:9999");

		List<ConfigServerEndpoint> configServerEndpoints = locator.getConfigServerEndpoints();
		assertEquals(1, configServerEndpoints.size());
		ConfigServerEndpoint configServerEndpoint = configServerEndpoints.get(0);
		assertEquals("http://localhost:9999", configServerEndpoint.getRawUri());
		assertNull(configServerEndpoint.getCredentials());
	}
	
	@Test
	public void useFallbackUsernameForUriCredsWithMissingUsername() {
		locator.setUri("http://:bar@localhost:9999");
		
		List<ConfigServerEndpoint> configServerEndpoints = locator.getConfigServerEndpoints();
		assertEquals(1, configServerEndpoints.size());
		ConfigServerEndpoint configServerEndpoint = configServerEndpoints.get(0);
		assertEquals("http://localhost:9999", configServerEndpoint.getRawUri());
		Credentials credentials = configServerEndpoint.getCredentials();
		assertNotNull(credentials);
		assertEquals(ConfigServerEndpoint.DEFAULT_USERNAME_user,
				credentials.getUsername());
		assertEquals("bar", credentials.getPassword());
	}
	
	public void missingUriTriggerIllegalStateException() {
		locator.setUri(null);
		
		assertTrue( locator.getConfigServerEndpoints().isEmpty());
	}
	
	@Test
	public void multipleUrisVanilla() {
		locator.setUri("http://localhost1:9999,http://localhost2:9999");
		locator.setPassword("secret");
		List<ConfigServerEndpoint> configServerEndpoints = locator.getConfigServerEndpoints();
		assertEquals(2, configServerEndpoints.size());
		
		assertEquals("http://localhost1:9999", configServerEndpoints.get(0).getRawUri());
		assertEquals(ConfigServerEndpoint.DEFAULT_USERNAME_user,
				configServerEndpoints.get(0).getCredentials().getUsername());
		assertEquals("secret", configServerEndpoints.get(0).getCredentials().getPassword());

		assertEquals("http://localhost2:9999", configServerEndpoints.get(1).getRawUri());
		assertEquals(ConfigServerEndpoint.DEFAULT_USERNAME_user,
				configServerEndpoints.get(1).getCredentials().getUsername());
		assertEquals("secret", configServerEndpoints.get(1).getCredentials().getPassword());
	}

	@Test
	public void multipleUrisExplicitPassword() {
		locator.setUri("http://localhost1:9999,http://foo@localhost2:9999");
		locator.setPassword("secret");
		List<ConfigServerEndpoint> configServerEndpoints = locator.getConfigServerEndpoints();
		assertEquals(2, configServerEndpoints.size());
		
		assertEquals("http://localhost1:9999", configServerEndpoints.get(0).getRawUri());
		assertEquals(ConfigServerEndpoint.DEFAULT_USERNAME_user,
				configServerEndpoints.get(0).getCredentials().getUsername());
		assertEquals("secret", configServerEndpoints.get(0).getCredentials().getPassword());
		
		assertEquals("http://localhost2:9999", configServerEndpoints.get(1).getRawUri());
		assertEquals("foo", configServerEndpoints.get(1).getCredentials().getUsername());
		assertEquals("secret", configServerEndpoints.get(1).getCredentials().getPassword());
	}
	
	@SuppressWarnings("deprecation")
	@Test(expected = IllegalStateException.class) 
	public void getUsernameFailsForMissingServerEndpoint() {
		locator.setUri(null);
		locator.getUsername();
	}
	
	@SuppressWarnings("deprecation")
	@Test(expected = IllegalStateException.class) 
	public void getPasswordFailsForMissingServerEndpoint() {
		locator.setUri(null);
		locator.getPassword();
	}
	@SuppressWarnings("deprecation")
	@Test(expected = IllegalStateException.class) 
	public void getRawUriFailsForMissingServerEndpoint() {
		locator.setUri(null);
		locator.getRawUri();
	}
	
}
