/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.config.server.credentials;

import java.net.URISyntaxException;

import com.amazonaws.auth.AWSCredentialsProvider;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.URIish;
import org.junit.Before;
import org.junit.Test;

import org.springframework.cloud.config.server.support.AwsCodeCommitCredentialProvider;
import org.springframework.cloud.config.server.support.GitCredentialsProviderFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * It would be nice to do an integration test, however, this would require using real AWS
 * credentials. How can we test the credential generation without real credentials?
 *
 * @author don laidlaw
 *
 */
public class AwsCodeCommitCredentialsProviderTests {

	private static final String PASSWORD = "secret";

	private static final String USER = "test";

	private static final String AWS_REPO = "https://git-codecommit.us-east-1.amazonaws.com/v1/repos/test";

	private static final String BAD_REPO = "https://amazonaws.com/v1/repos/test";

	private static final String CURLY_BRACES_REPO = "https://git-codecommit.us-east-1.amazonaws.com/"
			+ "v1/repos/{application}";

	private AwsCodeCommitCredentialProvider provider;

	@Before
	public void init() {
		GitCredentialsProviderFactory factory = new GitCredentialsProviderFactory();
		this.provider = (AwsCodeCommitCredentialProvider) factory.createFor(AWS_REPO,
				USER, PASSWORD, null, false);
	}

	@Test
	public void basics() {
		assertThat(this.provider).isNotNull();
		assertThat(this.provider.getUsername()).isEqualTo(USER);
		assertThat(this.provider.getPassword()).isEqualTo(PASSWORD);
		assertThat(this.provider.isInteractive()).isFalse();
	}

	@Test
	public void testSupportsUsernamePassword() {
		assertThat(this.provider.supports(new CredentialItem[] {
				new CredentialItem.Username(), new CredentialItem.Password() })).isTrue();
	}

	@Test
	public void testNotSupportsOther() {
		assertThat(this.provider.supports(
				new CredentialItem[] { new CredentialItem.YesNoType("OK To Login?") // this
				// is
				// not
				// ok
				})).isFalse();
		assertThat(this.provider.supports(
				new CredentialItem[] { new CredentialItem.StringType("OK To Login?", true) // this
				// is
				// not
				// ok
				})).isFalse();
		assertThat(this.provider
				.supports(new CredentialItem[] { new CredentialItem.Username(), // this
						// is
						// ok
						new CredentialItem.Password(), // this is ok
						new CredentialItem.StringType("OK To Login?", true) // this is not
				// ok
		})).isFalse();
	}

	@Test
	public void testAwsCredentialsProviderIsNullInitially() {
		AWSCredentialsProvider awsProvider = this.provider.getAwsCredentialProvider();
		assertThat(awsProvider).isNull();
	}

	@Test
	public void testAwsCredentialsProviderIsDefinedAfterGet() throws URISyntaxException {
		AWSCredentialsProvider awsProvider = this.provider.getAwsCredentialProvider();
		assertThat(awsProvider).isNull();
		assertThat(this.provider.get(new URIish(AWS_REPO), makeCredentialItems()))
				.isTrue();
		awsProvider = this.provider.getAwsCredentialProvider();
		assertThat(awsProvider).isNotNull();
		assertThat(
				awsProvider instanceof AwsCodeCommitCredentialProvider.AWSStaticCredentialsProvider)
						.isTrue();
	}

	@Test
	public void testBadUriReturnsFalse()
			throws UnsupportedCredentialItem, URISyntaxException {
		CredentialItem[] credentialItems = makeCredentialItems();
		assertThat(this.provider.get(new URIish(BAD_REPO), credentialItems)).isFalse();
	}

	@Test
	public void testUriWithCurlyBracesReturnsTrue()
			throws UnsupportedCredentialItem, URISyntaxException {
		GitCredentialsProviderFactory factory = new GitCredentialsProviderFactory();
		this.provider = (AwsCodeCommitCredentialProvider) factory
				.createFor(CURLY_BRACES_REPO, USER, PASSWORD, null, false);
		CredentialItem[] credentialItems = makeCredentialItems();
		assertThat(this.provider.get(new URIish(CURLY_BRACES_REPO), credentialItems))
				.isTrue();
	}

	@Test
	public void testThrowsUnsupportedCredentialException() throws URISyntaxException {
		CredentialItem[] goodCredentialItems = makeCredentialItems();
		CredentialItem[] badCredentialItems = new CredentialItem[] {
				goodCredentialItems[0], goodCredentialItems[1],
				new CredentialItem.YesNoType("OK?") };
		try {
			this.provider.get(new URIish(AWS_REPO), badCredentialItems);
			fail("Expected UnsupportedCredentialItem exception");
		}
		catch (UnsupportedCredentialItem e) {
			assertThat(e.getMessage()).isNotNull();
		}
	}

	@Test
	public void testReturnsCredentials() throws URISyntaxException {
		CredentialItem[] credentialItems = makeCredentialItems();
		assertThat(this.provider.get(new URIish(AWS_REPO), credentialItems)).isTrue();

		String theUsername = ((CredentialItem.Username) credentialItems[0]).getValue();
		char[] thePassword = ((CredentialItem.Password) credentialItems[1]).getValue();

		assertThat(theUsername).isEqualTo(USER);
		assertThat(thePassword).isNotNull();

		// The password will always begin with a timestamp like
		// 20161113T121314Z
		assertThat(thePassword.length > 16).isTrue();
		assertThat(thePassword[8]).isEqualTo('T');
		assertThat(thePassword[15]).isEqualTo('Z');
	}

	private CredentialItem[] makeCredentialItems() {
		CredentialItem[] credentialItems = new CredentialItem[2];
		credentialItems[0] = new CredentialItem.Username();
		credentialItems[1] = new CredentialItem.Password();
		return credentialItems;
	}

}
