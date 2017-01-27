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

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.config.server.support.AwsCodeCommitCredentialProvider;
import org.springframework.cloud.config.server.support.GitCredentialsProviderFactory;
import org.springframework.cloud.config.server.support.PassphraseCredentialsProvider;

/**
 * @author don laidlaw
 *
 */
public class GitCredentialsProviderFactoryTests {
	private static final String PASSWORD = "secret";
	private static final String USER = "test";
	private static final String FILE_REPO = "file:///home/user/repo";
	private static final String GIT_REPO = "https://github.com/spring-cloud/spring-cloud-config-test";
	private static final String AWS_REPO = "https://git-codecommit.us-east-1.amazonaws.com/v1/repos/test";

	private GitCredentialsProviderFactory factory;
	
	@Before
	public void init() {
		factory = new GitCredentialsProviderFactory();
	}

	@Test
	public void testCreateForFileNoUsernameIsNull() {
		CredentialsProvider provider = factory.createFor(FILE_REPO, null, null, null);
		assertNull(provider);
	}

	@Test
	public void testCreateForFileWithUsername() {
		CredentialsProvider provider = factory.createFor(FILE_REPO, USER, PASSWORD, null);
		assertNotNull(provider);
		assertTrue(provider instanceof UsernamePasswordCredentialsProvider);
	}

	@Test
	public void testCreateForServerNoUsernameIsNull() {
		CredentialsProvider provider = factory.createFor(GIT_REPO, null, null, null);
		assertNull(provider);
	}

	@Test
	public void testCreateForServerWithUsername() {
		CredentialsProvider provider = factory.createFor(GIT_REPO, USER, PASSWORD, null);
		assertNotNull(provider);
		assertTrue(provider instanceof UsernamePasswordCredentialsProvider);
	}

	@Test
	public void testCreateForAwsNoUsername() {
		CredentialsProvider provider = factory.createFor(AWS_REPO, null, null, null);
		assertNotNull(provider);
		assertTrue(provider instanceof AwsCodeCommitCredentialProvider);
		AwsCodeCommitCredentialProvider aws = (AwsCodeCommitCredentialProvider) provider;
		assertNull(aws.getUsername());
		assertNull(aws.getPassword());
	}

	@Test
	public void testCreateForAwsWithUsername() {
		CredentialsProvider provider = factory.createFor(AWS_REPO, USER, PASSWORD, null);
		assertNotNull(provider);
		assertTrue(provider instanceof AwsCodeCommitCredentialProvider);
		AwsCodeCommitCredentialProvider aws = (AwsCodeCommitCredentialProvider) provider;
		assertEquals(USER, aws.getUsername());
		assertEquals(PASSWORD, aws.getPassword());
	}
	
	@Test
	public void testCreateForAwsDisabled() {
		factory.setAwsCodeCommitEnabled(false);
		CredentialsProvider provider = factory.createFor(AWS_REPO, null, null, null);
		assertNull(provider);
		provider = factory.createFor(AWS_REPO, USER, PASSWORD, null);
		assertNotNull(provider);
		assertTrue(provider instanceof UsernamePasswordCredentialsProvider);
	}
	
	@Test
	public void testCreatePassphraseCredentialProvider() {
		CredentialsProvider provider = factory.createFor(GIT_REPO, null, null, PASSWORD);
		assertNotNull(provider);
		assertTrue(provider instanceof PassphraseCredentialsProvider);
	}
	

	@Test
	public void testIsAwsCodeCommitEnabled() {
		assertTrue(factory.isAwsCodeCommitEnabled());
	}

	@Test
	public void testSetAwsCodeCommitEnabled() {
		assertTrue(factory.isAwsCodeCommitEnabled());
		factory.setAwsCodeCommitEnabled(false);
		assertFalse(factory.isAwsCodeCommitEnabled());
	}

}
