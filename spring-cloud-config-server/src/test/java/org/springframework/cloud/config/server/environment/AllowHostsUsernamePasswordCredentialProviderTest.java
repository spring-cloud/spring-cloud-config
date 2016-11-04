/*
 * Copyright 2013-2015 the original author or authors.
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
package org.springframework.cloud.config.server.environment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URISyntaxException;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.URIish;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Michael Davis
 */
public class AllowHostsUsernamePasswordCredentialProviderTest {

	private AllowHostsUsernamePasswordCredentialProvider provider;

	private CredentialItem.CharArrayType charArrayType = new CredentialItem.CharArrayType(
			null, true);
	private CredentialItem.Password password = new CredentialItem.Password();
	private CredentialItem.StringType stringType = new CredentialItem.StringType(null,
			false);
	private CredentialItem.Username username = new CredentialItem.Username();
	private CredentialItem.YesNoType yesNo = new CredentialItem.YesNoType(null);

	@Before
	public void setUp() throws Exception {
		provider = new AllowHostsUsernamePasswordCredentialProvider("username",
				"password");
	}

	@Test
	public void testSupports() {
		assertTrue(provider.supports(username, password, stringType, yesNo));
		assertFalse(provider.supports(username, password, charArrayType));
	}

	@Test
	public void testGet() {
		URIish uri;
		try {
			uri = new URIish("uri");
			assertTrue(provider.get(uri, password, stringType, username, yesNo));
			assertTrue(provider.get(uri, username, password, charArrayType));
			fail("provider should have thrown an exception when passed a CredentialItem.CharArrayType.");
		}
		catch (UnsupportedCredentialItem uci) {
			// this is expected when provider is passed the charArrayType.
		}
		catch (URISyntaxException e) {
			fail("Unexpected exception: " + e);
		}
	}
}
