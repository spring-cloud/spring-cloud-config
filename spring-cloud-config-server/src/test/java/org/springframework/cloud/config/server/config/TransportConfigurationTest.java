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

package org.springframework.cloud.config.server.config;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.junit.Test;
import org.springframework.cloud.config.server.ssh.SshUri;
import org.springframework.cloud.config.server.ssh.SshUriProperties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.instanceOf;
/**
* @author Ollie Hughes
*/
public class TransportConfigurationTest {
	@Test
	public void propertiesBasedSshTransportCallbackCreated() throws Exception {
		SshUriProperties ignoreLocalSettings = SshUri.builder()
				.uri("user@gitrepo.com:proj/repo")
				.ignoreLocalSshSettings(true)
				.build();
		TransportConfiguration transportConfiguration = new TransportConfiguration();
		TransportConfigCallback transportConfigCallback = transportConfiguration.propertiesBasedSshTransportCallback(ignoreLocalSettings);
		assertThat(transportConfigCallback, is(instanceOf(TransportConfiguration.PropertiesBasedSshTransportConfigCallback.class)));
	}

	@Test
	public void fileBasedSshTransportCallbackCreated() throws Exception {
		SshUriProperties dontIgnoreLocalSettings = SshUri.builder()
				.uri("user@gitrepo.com:proj/repo")
				.ignoreLocalSshSettings(false)
				.build();

		TransportConfiguration transportConfiguration = new TransportConfiguration();
		TransportConfigCallback transportConfigCallback = transportConfiguration.propertiesBasedSshTransportCallback(dontIgnoreLocalSettings);
		assertThat(transportConfigCallback, is(instanceOf(TransportConfiguration.FileBasedSshTransportConfigCallback.class)));

	}

}