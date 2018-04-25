/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.cloud.config.server.support;

import java.net.URISyntaxException;
import java.text.MessageFormat;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Gareth Clay
 */
@RunWith(MockitoJUnitRunner.class)
public class GitSkipSslValidationCredentialsProviderTest {

	@Mock
	private CredentialsProvider mockDelegateCredentialsProvider;
	private GitSkipSslValidationCredentialsProvider skipSslValidationCredentialsProvider;

	@Before
	public void setup() {
		this.skipSslValidationCredentialsProvider = new GitSkipSslValidationCredentialsProvider(
				null);
	}

	@Test
	public void testCanHandle() {
		assertTrue("GitSkipSslValidationCredentialsProvider only handles HTTPS uris",
				GitSkipSslValidationCredentialsProvider
						.canHandle("https://github.com/org/repo"));
		assertFalse("GitSkipSslValidationCredentialsProvider only handles HTTPS uris",
				GitSkipSslValidationCredentialsProvider
						.canHandle("git@github.com:org/repo"));
	}

	@Test
	public void testIsInteractive() {
		assertFalse(
				"GitSkipSslValidationCredentialsProvider with no delegate requires no user interaction",
				skipSslValidationCredentialsProvider.isInteractive());
	}

	@Test
	public void testIsInteractiveWithDelegate() {
		this.skipSslValidationCredentialsProvider = new GitSkipSslValidationCredentialsProvider(
				mockDelegateCredentialsProvider);

		when(mockDelegateCredentialsProvider.isInteractive()).thenReturn(true);

		assertTrue(
				"With a delegate provider, isInteractive value depends on the delegate",
				skipSslValidationCredentialsProvider.isInteractive());
	}

	@Test
	public void testSupportsSslFailureInformationalMessage() {
		CredentialItem informationalMessage = new CredentialItem.InformationalMessage(
				"text " + JGitText.get().sslFailureTrustExplanation + " more text");
		assertTrue(
				"GitSkipSslValidationCredentialsProvider should always support SSL failure InformationalMessage",
				skipSslValidationCredentialsProvider.supports(informationalMessage));

		informationalMessage = new CredentialItem.InformationalMessage("unrelated");
		assertFalse(
				"GitSkipSslValidationCredentialsProvider should not support unrelated InformationalMessage items",
				skipSslValidationCredentialsProvider.supports(informationalMessage));
	}

	@Test
	public void testSupportsSslFailureInformationalMessageWithDelegate() {
		this.skipSslValidationCredentialsProvider = new GitSkipSslValidationCredentialsProvider(
				mockDelegateCredentialsProvider);

		testSupportsSslFailureInformationalMessage();
	}

	@Test
	public void testSupportsSslValidationYesNoTypes() {
		CredentialItem yesNoType = new CredentialItem.YesNoType(
				JGitText.get().sslTrustNow);
		assertTrue(
				"GitSkipSslValidationCredentialsProvider should always support the trust now YesNoType item",
				skipSslValidationCredentialsProvider.supports(yesNoType));

		yesNoType = new CredentialItem.YesNoType(
				MessageFormat.format(JGitText.get().sslTrustForRepo, "/a/path.git"));
		assertTrue(
				"GitSkipSslValidationCredentialsProvider should always support the trust repo YesNoType item",
				skipSslValidationCredentialsProvider.supports(yesNoType));

		yesNoType = new CredentialItem.YesNoType(JGitText.get().sslTrustAlways);
		assertTrue(
				"GitSkipSslValidationCredentialsProvider should always support the trust always YesNoType item",
				skipSslValidationCredentialsProvider.supports(yesNoType));

		yesNoType = new CredentialItem.YesNoType("unrelated");
		assertFalse(
				"GitSkipSslValidationCredentialsProvider should not support unrelated YesNoType items",
				skipSslValidationCredentialsProvider.supports(yesNoType));
	}

	@Test
	public void testSupportsYesNoTypeWithDelegate() {
		this.skipSslValidationCredentialsProvider = new GitSkipSslValidationCredentialsProvider(
				mockDelegateCredentialsProvider);

		testSupportsSslValidationYesNoTypes();
	}

	@Test
	public void testSupportsUnrelatedCredentialItemTypes() {
		CredentialItem usernameCredentialItem = new CredentialItem.Username();

		boolean supportsItems = skipSslValidationCredentialsProvider
				.supports(usernameCredentialItem);

		assertFalse(
				"Credential item types not related to SSL validation skipping should not be supported",
				supportsItems);
	}

	@Test
	public void testSupportsUnrelatedCredentialItemTypesWithDelegate() {
		this.skipSslValidationCredentialsProvider = new GitSkipSslValidationCredentialsProvider(
				mockDelegateCredentialsProvider);
		CredentialItem usernameCredentialItem = new CredentialItem.Username();

		when(mockDelegateCredentialsProvider.supports(usernameCredentialItem))
				.thenReturn(true);

		boolean supportsItems = skipSslValidationCredentialsProvider
				.supports(usernameCredentialItem);

		assertTrue(
				"GitSkipSslValidationCredentialsProvider must support the types supported by its delegate CredentialsProvider",
				supportsItems);
	}

	@Test(expected = UnsupportedCredentialItem.class)
	public void testGetUnrelatedCredentialItemTypes() throws URISyntaxException {
		URIish uri = new URIish("https://example.com/repo.git");
		CredentialItem usernameCredentialItem = new CredentialItem.Username();
		CredentialItem passwordCredentialItem = new CredentialItem.Password();

		skipSslValidationCredentialsProvider.get(uri, usernameCredentialItem,
				passwordCredentialItem);
	}

	@Test
	public void testGetUnrelatedCredentialItemTypesWithDelegate()
			throws URISyntaxException {
		this.skipSslValidationCredentialsProvider = new GitSkipSslValidationCredentialsProvider(
				mockDelegateCredentialsProvider);
		URIish uri = new URIish("https://example.com/repo.git");
		CredentialItem usernameCredentialItem = new CredentialItem.Username();
		CredentialItem passwordCredentialItem = new CredentialItem.Password();

		when(mockDelegateCredentialsProvider.get(uri, usernameCredentialItem,
				passwordCredentialItem)).thenReturn(true);

		boolean getSuccessful = skipSslValidationCredentialsProvider.get(uri,
				usernameCredentialItem, passwordCredentialItem);

		assertTrue(
				"GitSkipSslValidationCredentialsProvider must successfully get the types supported by its delegate CredentialsProvider",
				getSuccessful);
	}

	@Test
	public void testGetSslTrustItems() throws URISyntaxException {
		URIish uri = new URIish("https://example.com/repo.git");
		CredentialItem message = new CredentialItem.InformationalMessage(
				JGitText.get().sslFailureTrustExplanation);
		CredentialItem.YesNoType trustNow = new CredentialItem.YesNoType(
				JGitText.get().sslTrustNow);
		CredentialItem.YesNoType trustAlways = new CredentialItem.YesNoType(
				JGitText.get().sslTrustAlways);

		boolean getSuccessful = skipSslValidationCredentialsProvider.get(uri, message,
				trustNow, trustAlways);

		assertTrue(
				"SkipSSlValidationCredentialsProvider must successfully get the types required for SSL validation skipping",
				getSuccessful);
		assertTrue(
				"SkipSSlValidationCredentialsProvider should trust the current repo operation",
				trustNow.getValue());
		assertFalse("We should not globally skip all SSL validation",
				trustAlways.getValue());
	}

	@Test
	public void testGetSslTrustItemsWithDelegate() throws URISyntaxException {
		this.skipSslValidationCredentialsProvider = new GitSkipSslValidationCredentialsProvider(
				mockDelegateCredentialsProvider);

		testGetSslTrustItems();
	}

	@Test
	public void testGetSslTrustItemsWithLocalRepo() throws URISyntaxException {
		URIish uri = new URIish("https://example.com/repo.git");
		CredentialItem message = new CredentialItem.InformationalMessage(
				JGitText.get().sslFailureTrustExplanation);
		CredentialItem.YesNoType trustNow = new CredentialItem.YesNoType(
				JGitText.get().sslTrustNow);
		CredentialItem.YesNoType trustForRepo = new CredentialItem.YesNoType(
				JGitText.get().sslTrustForRepo);
		CredentialItem.YesNoType trustAlways = new CredentialItem.YesNoType(
				JGitText.get().sslTrustAlways);

		boolean getSuccessful = skipSslValidationCredentialsProvider.get(uri, message,
				trustNow, trustForRepo, trustAlways);

		assertTrue(
				"SkipSSlValidationCredentialsProvider must successfully get the types required for SSL validation skipping",
				getSuccessful);
		assertTrue(
				"SkipSSlValidationCredentialsProvider should trust the current repo operation",
				trustNow.getValue());
		assertTrue("Future operations on this repository should also be trusted",
				trustForRepo.getValue());
		assertFalse("We should not globally skip all SSL validation",
				trustAlways.getValue());
	}

	@Test
	public void testGetSslTrustItemsWithLocalRepoAndDelegate() throws URISyntaxException {
		this.skipSslValidationCredentialsProvider = new GitSkipSslValidationCredentialsProvider(
				mockDelegateCredentialsProvider);

		testGetSslTrustItemsWithLocalRepo();
	}

	@Test
	public void testReset() throws URISyntaxException {
		URIish uri = new URIish("https://example.com/repo.git");

		skipSslValidationCredentialsProvider.reset(uri);
	}

	@Test
	public void testResetWithDelegate() throws URISyntaxException {
		this.skipSslValidationCredentialsProvider = new GitSkipSslValidationCredentialsProvider(
				mockDelegateCredentialsProvider);
		URIish uri = new URIish("https://example.com/repo.git");

		skipSslValidationCredentialsProvider.reset(uri);

		verify(mockDelegateCredentialsProvider).reset(uri);
	}
}
