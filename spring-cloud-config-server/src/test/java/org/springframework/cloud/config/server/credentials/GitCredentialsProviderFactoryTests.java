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

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.Before;
import org.junit.Test;

import org.springframework.cloud.config.server.support.AwsCodeCommitCredentialProvider;
import org.springframework.cloud.config.server.support.GitCredentialsProviderFactory;
import org.springframework.cloud.config.server.support.GitSkipSslValidationCredentialsProvider;
import org.springframework.cloud.config.server.support.PassphraseCredentialsProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author don laidlaw
 * @author Gareth Clay
 */
public class GitCredentialsProviderFactoryTests {

	private static final String PASSWORD = "secret";

	private static final String USER = "test";

	private static final String FILE_REPO = "file:///home/user/repo";

	private static final String HTTPS_GIT_REPO = "https://github.com/spring-cloud/spring-cloud-config-test";

	private static final String SSH_GIT_REPO = "git@github.com:spring-cloud/spring-cloud-config-test.git";

	private static final String AWS_REPO = "https://git-codecommit.us-east-1.amazonaws.com/v1/repos/test";

	private GitCredentialsProviderFactory factory;

	@Before
	public void init() {
		this.factory = new GitCredentialsProviderFactory();
	}

	@Test
	public void testCreateForFileNoUsernameIsNull() {
		CredentialsProvider provider = this.factory.createFor(FILE_REPO, null, null, null,
				false);
		assertThat(provider).isNull();
	}

	@Test
	public void testCreateForFileWithUsername() {
		CredentialsProvider provider = this.factory.createFor(FILE_REPO, USER, PASSWORD,
				null, false);
		assertThat(provider).isNotNull();
		assertThat(provider instanceof UsernamePasswordCredentialsProvider).isTrue();
	}

	@Test
	public void testCreateForServerNoUsernameIsNull() {
		CredentialsProvider provider = this.factory.createFor(HTTPS_GIT_REPO, null, null,
				null, false);
		assertThat(provider).isNull();
	}

	@Test
	public void testCreateForServerWithUsername() {
		CredentialsProvider provider = this.factory.createFor(HTTPS_GIT_REPO, USER,
				PASSWORD, null, false);
		assertThat(provider).isNotNull();
		assertThat(provider instanceof UsernamePasswordCredentialsProvider).isTrue();
	}

	@Test
	public void testCreateForHttpsServerWithSkipSslValidation() {
		CredentialsProvider provider = this.factory.createFor(HTTPS_GIT_REPO, USER,
				PASSWORD, null, true);
		assertThat(provider).isNotNull();
		assertThat(provider instanceof GitSkipSslValidationCredentialsProvider).isTrue();
	}

	@Test
	public void testCreateForHttpsServerWithoutSpecifyingSkipSslValidation() {
		CredentialsProvider provider = this.factory.createFor(HTTPS_GIT_REPO, USER,
				PASSWORD, null);
		assertThat(provider).isNotNull();
		assertThat(provider instanceof UsernamePasswordCredentialsProvider)
				.as("deprecated createFor() should not enable ssl validation skipping")
				.isTrue();
	}

	@Test
	public void testCreateForSshServerWithSkipSslValidation() {
		CredentialsProvider provider = this.factory.createFor(SSH_GIT_REPO, USER,
				PASSWORD, null, true);
		assertThat(provider).isNotNull();
		assertThat(provider instanceof UsernamePasswordCredentialsProvider).isTrue();
	}

	@Test
	public void testCreateForAwsNoUsername() {
		CredentialsProvider provider = this.factory.createFor(AWS_REPO, null, null, null,
				false);
		assertThat(provider).isNotNull();
		assertThat(provider instanceof AwsCodeCommitCredentialProvider).isTrue();
		AwsCodeCommitCredentialProvider aws = (AwsCodeCommitCredentialProvider) provider;
		assertThat(aws.getUsername()).isNull();
		assertThat(aws.getPassword()).isNull();
	}

	@Test
	public void testCreateForAwsWithUsername() {
		CredentialsProvider provider = this.factory.createFor(AWS_REPO, USER, PASSWORD,
				null, false);
		assertThat(provider).isNotNull();
		assertThat(provider instanceof AwsCodeCommitCredentialProvider).isTrue();
		AwsCodeCommitCredentialProvider aws = (AwsCodeCommitCredentialProvider) provider;
		assertThat(aws.getUsername()).isEqualTo(USER);
		assertThat(aws.getPassword()).isEqualTo(PASSWORD);
	}

	@Test
	public void testCreateForAwsDisabled() {
		this.factory.setAwsCodeCommitEnabled(false);
		CredentialsProvider provider = this.factory.createFor(AWS_REPO, null, null, null,
				false);
		assertThat(provider).isNull();
		provider = this.factory.createFor(AWS_REPO, USER, PASSWORD, null, false);
		assertThat(provider).isNotNull();
		assertThat(provider instanceof UsernamePasswordCredentialsProvider).isTrue();
	}

	@Test
	public void testCreatePassphraseCredentialProvider() {
		CredentialsProvider provider = this.factory.createFor(HTTPS_GIT_REPO, null, null,
				PASSWORD, false);
		assertThat(provider).isNotNull();
		assertThat(provider instanceof PassphraseCredentialsProvider).isTrue();
	}

	@Test
	public void testIsAwsCodeCommitEnabled() {
		assertThat(this.factory.isAwsCodeCommitEnabled()).isTrue();
	}

	@Test
	public void testSetAwsCodeCommitEnabled() {
		assertThat(this.factory.isAwsCodeCommitEnabled()).isTrue();
		this.factory.setAwsCodeCommitEnabled(false);
		assertThat(this.factory.isAwsCodeCommitEnabled()).isFalse();
	}

}
