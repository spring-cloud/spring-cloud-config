/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.cloud.config.server.ssh;

import org.eclipse.jgit.transport.SshSessionFactory;
import org.junit.After;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

/**
 * Unit tests for property based SSH config processor
 * @author William Tran
 * @author Ollie Hughes
 */
public class SshUriPropertyProcessorTest {

	private static final String PRIVATE_KEY1 = "privateKey";
	private static final String HOST_KEY1 = "hostKey";
	private static final String ALGO1 = "ssh-rsa";
	private static final String URI1 = "git@gitlab.test.local:wtran/my-repo";
	private static final String HOST1 = "gitlab.test.local";
	private static final String PRIVATE_KEY2 = "privateKey2";
	private static final String URI2 = "git@gitlab2.test.local:wtran/my-repo";
	private static final String HOST2 = "gitlab2.test.local";

	@After
	public void cleanup() {
		SshSessionFactory.setInstance(null);
	}

	@Test
	public void testSingleSshUriProperties() {
		SshUriPropertyProcessor sshUriPropertyProcessor = new SshUriPropertyProcessor(mainRepoPropertiesFixture());
		Map<String, SshUriProperties> sshKeysByHostname = sshUriPropertyProcessor.getSshKeysByHostname();

		assertThat(sshKeysByHostname.values(), hasSize(1));

		SshUriProperties sshKey = sshKeysByHostname.get(HOST1);
		assertMainRepo(sshKey);
	}

	@Test
	public void testMultipleSshUriPropertiess() {
		SshUriProperties sshUriProperties = mainRepoPropertiesFixture();
		addRepoProperties(sshUriProperties, SshUriProperties.builder()
				.uri(URI2)
				.privateKey(PRIVATE_KEY2)
				.build());

		SshUriPropertyProcessor sshUriPropertyProcessor = new SshUriPropertyProcessor(sshUriProperties);

		Map<String, SshUriProperties> sshKeysByHostname = sshUriPropertyProcessor.getSshKeysByHostname();


		SshUriProperties sshKey = sshKeysByHostname.get(HOST1);
		assertMainRepo(sshKey);

		sshKey = sshKeysByHostname.get(HOST2);

		assertThat(sshKeysByHostname.values(), hasSize(2));

		assertThat(sshKey.getHostname(), is(equalTo(HOST2)));

		assertThat(sshKey.getHostKeyAlgorithm(), is(nullValue()));

		assertThat(sshKey.getHostKey(), is(nullValue()));

		assertThat(sshKey.getPrivateKey(), is(equalTo(PRIVATE_KEY2)));
	}

	@Test
	public void testSameHostnameDifferentKeysFirstOneWins() {
		SshUriProperties sshUriProperties = mainRepoPropertiesFixture();
		addRepoProperties(sshUriProperties, SshUriProperties.builder().uri(URI1)
				.privateKey(PRIVATE_KEY1)
				.hostKey(HOST_KEY1)
				.hostKeyAlgorithm(ALGO1)
				.build());

		SshUriPropertyProcessor sshUriPropertyProcessor = new SshUriPropertyProcessor(sshUriProperties);
		Map<String, SshUriProperties> sshKeysByHostname = sshUriPropertyProcessor.getSshKeysByHostname();

		assertThat(sshKeysByHostname.values(), hasSize(1));

		SshUriProperties sshKey = sshKeysByHostname.get(HOST1);
		assertMainRepo(sshKey);
	}

	@Test
	public void testNoSshUriPropertiess() {
		SshUriPropertyProcessor sshUriPropertyProcessor = new SshUriPropertyProcessor(new SshUriProperties());
		Map<String, SshUriProperties> sshKeysByHostname = sshUriPropertyProcessor.getSshKeysByHostname();
		assertThat(sshKeysByHostname.values(), hasSize(0));
	}

	private SshUriProperties mainRepoPropertiesFixture() {

		return SshUriProperties.builder()
				.uri(URI1)
				.hostKeyAlgorithm(ALGO1)
				.hostKey(HOST_KEY1)
				.privateKey(PRIVATE_KEY1)
				.build();
	}

	private void addRepoProperties(SshUriProperties mainRepoProperties, SshUriProperties repoProperties) {
		Map<String, SshUriProperties> repos = new HashMap<>();
		repos.put("repo2", repoProperties);
		mainRepoProperties.setRepos(repos);
	}

	private void assertMainRepo(SshUriProperties sshKey) {
		assertThat(sshKey.getHostname(), is(equalTo(HOST1)));
		assertThat(sshKey.getHostKeyAlgorithm(), is(equalTo(ALGO1)));
		assertThat(sshKey.getHostKey(), is(equalTo(HOST_KEY1)));
		assertThat(sshKey.getPrivateKey(), is(equalTo(PRIVATE_KEY1)));
	}

}
