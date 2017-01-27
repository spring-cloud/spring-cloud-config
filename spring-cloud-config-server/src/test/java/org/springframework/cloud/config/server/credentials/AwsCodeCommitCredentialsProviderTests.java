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

package org.springframework.cloud.config.server.credentials;

import static org.junit.Assert.*;

import java.net.URISyntaxException;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.URIish;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.config.server.support.AwsCodeCommitCredentialProvider;
import org.springframework.cloud.config.server.support.GitCredentialsProviderFactory;

import com.amazonaws.auth.AWSCredentialsProvider;

/**
 * It would be nice to do an integration test, however, this would require
 * using real AWS credentials. How can we test the credential generation
 * without real credentials? 
 * 
 * @author don laidlaw
 *
 */
public class AwsCodeCommitCredentialsProviderTests {
	private static final String PASSWORD = "secret";
	private static final String USER = "test";
	private static final String AWS_REPO = "https://git-codecommit.us-east-1.amazonaws.com/v1/repos/test";
	private static final String BAD_REPO = "https://amazonaws.com/v1/repos/test";

	private AwsCodeCommitCredentialProvider provider;
	
	@Before
	public void init() {
		GitCredentialsProviderFactory factory = new GitCredentialsProviderFactory();
		provider = (AwsCodeCommitCredentialProvider) 
				factory.createFor(AWS_REPO, USER, PASSWORD, null);
	}
	
	@Test
	public void basics() {
		assertNotNull(provider);
		assertEquals(USER, provider.getUsername());
		assertEquals(PASSWORD, provider.getPassword());
		assertFalse(provider.isInteractive());
	}

	@Test
	public void testSupportsUsernamePassword() {
		assertTrue(provider.supports(new CredentialItem[] {
			new CredentialItem.Username(),
			new CredentialItem.Password()
		}));
	}

	@Test
	public void testNotSupportsOther() {
		assertFalse(provider.supports(new CredentialItem[] {
			new CredentialItem.YesNoType("OK To Login?")	// this is not ok
		}));
		assertFalse(provider.supports(new CredentialItem[] {
			new CredentialItem.StringType("OK To Login?", true)	// this is not ok
		}));
		assertFalse(provider.supports(new CredentialItem[] {
				new CredentialItem.Username(),	// this is ok
				new CredentialItem.Password(),	// this is ok
				new CredentialItem.StringType("OK To Login?", true) // this is not ok
		}));
	}
	
	@Test
	public void testAwsCredentialsProviderIsNullInitially() {
		AWSCredentialsProvider awsProvider = provider.getAwsCredentialProvider();
		assertNull(awsProvider);
	}

	@Test
	public void testAwsCredentialsProviderIsDefinedAfterGet() throws URISyntaxException {
		AWSCredentialsProvider awsProvider = provider.getAwsCredentialProvider();
		assertNull(awsProvider);
		assertTrue(provider.get(new URIish(AWS_REPO), makeCredentialItems()));
		awsProvider = provider.getAwsCredentialProvider();
		assertNotNull(awsProvider);
		assertTrue(awsProvider instanceof AwsCodeCommitCredentialProvider.AWSStaticCredentialsProvider);
	}
	
	@Test
	public void testBadUriReturnsFalse() throws UnsupportedCredentialItem, URISyntaxException {
		CredentialItem[] credentialItems = makeCredentialItems();
		assertFalse(provider.get(new URIish(BAD_REPO), credentialItems));
	}
	
	@Test
	public void testThrowsUnsupportedCredentialException() throws URISyntaxException {
		CredentialItem[] goodCredentialItems = makeCredentialItems();
		CredentialItem[] badCredentialItems = new CredentialItem[] {
				goodCredentialItems[0],
				goodCredentialItems[1],
				new CredentialItem.YesNoType("OK?")
		};
		try {
			provider.get(new URIish(AWS_REPO), badCredentialItems);
			fail("Expected UnsupportedCredentialItem exception");
		} catch (UnsupportedCredentialItem e) {
			assertNotNull(e.getMessage());
		}
	}
	
	@Test
	public void testReturnsCredentials() throws URISyntaxException {
		CredentialItem[] credentialItems = makeCredentialItems();
		assertTrue(provider.get(new URIish(AWS_REPO), credentialItems));
		
		String theUsername = ((CredentialItem.Username) credentialItems[0]).getValue();
		char[] thePassword = ((CredentialItem.Password) credentialItems[1]).getValue();
		
		assertEquals(USER, theUsername);
		assertNotNull(thePassword);
		
		// The password will always begin with a timestamp like
		// 20161113T121314Z
		assertTrue(thePassword.length > 16);
		assertEquals('T', thePassword[8]);
		assertEquals('Z', thePassword[15]);
	}

	private CredentialItem[] makeCredentialItems() {
		CredentialItem[] credentialItems = new CredentialItem[2];
		credentialItems[0] = new CredentialItem.Username();
		credentialItems[1] = new CredentialItem.Password();
		return credentialItems;
	}
}
