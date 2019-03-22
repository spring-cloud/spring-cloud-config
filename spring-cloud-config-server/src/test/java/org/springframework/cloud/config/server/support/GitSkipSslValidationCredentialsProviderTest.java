/*
 * Copyright 2018-2019 the original author or authors.
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

import static org.assertj.core.api.Assertions.assertThat;
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
		assertThat(GitSkipSslValidationCredentialsProvider
				.canHandle("https://github.com/org/repo")).as(
						"GitSkipSslValidationCredentialsProvider only handles HTTPS uris")
						.isTrue();
		assertThat(GitSkipSslValidationCredentialsProvider
				.canHandle("git@github.com:org/repo")).as(
						"GitSkipSslValidationCredentialsProvider only handles HTTPS uris")
						.isFalse();
	}

	@Test
	public void testIsInteractive() {
		assertThat(this.skipSslValidationCredentialsProvider.isInteractive()).as(
				"GitSkipSslValidationCredentialsProvider with no delegate requires no user interaction")
				.isFalse();
	}

	@Test
	public void testIsInteractiveWithDelegate() {
		this.skipSslValidationCredentialsProvider = new GitSkipSslValidationCredentialsProvider(
				this.mockDelegateCredentialsProvider);

		when(this.mockDelegateCredentialsProvider.isInteractive()).thenReturn(true);

		assertThat(this.skipSslValidationCredentialsProvider.isInteractive()).as(
				"With a delegate provider, isInteractive value depends on the delegate")
				.isTrue();
	}

	@Test
	public void testSupportsSslFailureInformationalMessage() {
		CredentialItem informationalMessage = new CredentialItem.InformationalMessage(
				"text " + JGitText.get().sslFailureTrustExplanation + " more text");
		assertThat(this.skipSslValidationCredentialsProvider
				.supports(informationalMessage)).as(
						"GitSkipSslValidationCredentialsProvider should always support SSL failure InformationalMessage")
						.isTrue();

		informationalMessage = new CredentialItem.InformationalMessage("unrelated");
		assertThat(this.skipSslValidationCredentialsProvider
				.supports(informationalMessage)).as(
						"GitSkipSslValidationCredentialsProvider should not support unrelated InformationalMessage items")
						.isFalse();
	}

	@Test
	public void testSupportsSslFailureInformationalMessageWithDelegate() {
		this.skipSslValidationCredentialsProvider = new GitSkipSslValidationCredentialsProvider(
				this.mockDelegateCredentialsProvider);

		testSupportsSslFailureInformationalMessage();
	}

	@Test
	public void testSupportsSslValidationYesNoTypes() {
		CredentialItem yesNoType = new CredentialItem.YesNoType(
				JGitText.get().sslTrustNow);
		assertThat(this.skipSslValidationCredentialsProvider.supports(yesNoType)).as(
				"GitSkipSslValidationCredentialsProvider should always support the trust now YesNoType item")
				.isTrue();

		yesNoType = new CredentialItem.YesNoType(
				MessageFormat.format(JGitText.get().sslTrustForRepo, "/a/path.git"));
		assertThat(this.skipSslValidationCredentialsProvider.supports(yesNoType)).as(
				"GitSkipSslValidationCredentialsProvider should always support the trust repo YesNoType item")
				.isTrue();

		yesNoType = new CredentialItem.YesNoType(JGitText.get().sslTrustAlways);
		assertThat(this.skipSslValidationCredentialsProvider.supports(yesNoType)).as(
				"GitSkipSslValidationCredentialsProvider should always support the trust always YesNoType item")
				.isTrue();

		yesNoType = new CredentialItem.YesNoType("unrelated");
		assertThat(this.skipSslValidationCredentialsProvider.supports(yesNoType)).as(
				"GitSkipSslValidationCredentialsProvider should not support unrelated YesNoType items")
				.isFalse();
	}

	@Test
	public void testSupportsYesNoTypeWithDelegate() {
		this.skipSslValidationCredentialsProvider = new GitSkipSslValidationCredentialsProvider(
				this.mockDelegateCredentialsProvider);

		testSupportsSslValidationYesNoTypes();
	}

	@Test
	public void testSupportsUnrelatedCredentialItemTypes() {
		CredentialItem usernameCredentialItem = new CredentialItem.Username();

		boolean supportsItems = this.skipSslValidationCredentialsProvider
				.supports(usernameCredentialItem);

		assertThat(supportsItems).as(
				"Credential item types not related to SSL validation skipping should not be supported")
				.isFalse();
	}

	@Test
	public void testSupportsUnrelatedCredentialItemTypesWithDelegate() {
		this.skipSslValidationCredentialsProvider = new GitSkipSslValidationCredentialsProvider(
				this.mockDelegateCredentialsProvider);
		CredentialItem usernameCredentialItem = new CredentialItem.Username();

		when(this.mockDelegateCredentialsProvider.supports(usernameCredentialItem))
				.thenReturn(true);

		boolean supportsItems = this.skipSslValidationCredentialsProvider
				.supports(usernameCredentialItem);

		assertThat(supportsItems).as(
				"GitSkipSslValidationCredentialsProvider must support the types supported by its delegate CredentialsProvider")
				.isTrue();
	}

	@Test(expected = UnsupportedCredentialItem.class)
	public void testGetUnrelatedCredentialItemTypes() throws URISyntaxException {
		URIish uri = new URIish("https://example.com/repo.git");
		CredentialItem usernameCredentialItem = new CredentialItem.Username();
		CredentialItem passwordCredentialItem = new CredentialItem.Password();

		this.skipSslValidationCredentialsProvider.get(uri, usernameCredentialItem,
				passwordCredentialItem);
	}

	@Test
	public void testGetUnrelatedCredentialItemTypesWithDelegate()
			throws URISyntaxException {
		this.skipSslValidationCredentialsProvider = new GitSkipSslValidationCredentialsProvider(
				this.mockDelegateCredentialsProvider);
		URIish uri = new URIish("https://example.com/repo.git");
		CredentialItem usernameCredentialItem = new CredentialItem.Username();
		CredentialItem passwordCredentialItem = new CredentialItem.Password();

		when(this.mockDelegateCredentialsProvider.get(uri, usernameCredentialItem,
				passwordCredentialItem)).thenReturn(true);

		boolean getSuccessful = this.skipSslValidationCredentialsProvider.get(uri,
				usernameCredentialItem, passwordCredentialItem);

		assertThat(getSuccessful).as("GitSkipSslValidationCredentialsProvider "
				+ "must successfully get the types supported by its delegate CredentialsProvider")
				.isTrue();
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

		boolean getSuccessful = this.skipSslValidationCredentialsProvider.get(uri,
				message, trustNow, trustAlways);

		assertThat(getSuccessful).as(
				"SkipSSlValidationCredentialsProvider must successfully get the types required for SSL validation skipping")
				.isTrue();
		assertThat(trustNow.getValue()).as(
				"SkipSSlValidationCredentialsProvider should trust the current repo operation")
				.isTrue();
		assertThat(trustAlways.getValue())
				.as("We should not globally skip all SSL validation").isFalse();
	}

	@Test
	public void testGetSslTrustItemsWithDelegate() throws URISyntaxException {
		this.skipSslValidationCredentialsProvider = new GitSkipSslValidationCredentialsProvider(
				this.mockDelegateCredentialsProvider);

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

		boolean getSuccessful = this.skipSslValidationCredentialsProvider.get(uri,
				message, trustNow, trustForRepo, trustAlways);

		assertThat(getSuccessful).as(
				"SkipSSlValidationCredentialsProvider must successfully get the types required for SSL validation skipping")
				.isTrue();
		assertThat(trustNow.getValue()).as(
				"SkipSSlValidationCredentialsProvider should trust the current repo operation")
				.isTrue();
		assertThat(trustForRepo.getValue())
				.as("Future operations on this repository should also be trusted")
				.isTrue();
		assertThat(trustAlways.getValue())
				.as("We should not globally skip all SSL validation").isFalse();
	}

	@Test
	public void testGetSslTrustItemsWithLocalRepoAndDelegate() throws URISyntaxException {
		this.skipSslValidationCredentialsProvider = new GitSkipSslValidationCredentialsProvider(
				this.mockDelegateCredentialsProvider);

		testGetSslTrustItemsWithLocalRepo();
	}

	@Test
	public void testReset() throws URISyntaxException {
		URIish uri = new URIish("https://example.com/repo.git");

		this.skipSslValidationCredentialsProvider.reset(uri);
	}

	@Test
	public void testResetWithDelegate() throws URISyntaxException {
		this.skipSslValidationCredentialsProvider = new GitSkipSslValidationCredentialsProvider(
				this.mockDelegateCredentialsProvider);
		URIish uri = new URIish("https://example.com/repo.git");

		this.skipSslValidationCredentialsProvider.reset(uri);

		verify(this.mockDelegateCredentialsProvider).reset(uri);
	}

}
