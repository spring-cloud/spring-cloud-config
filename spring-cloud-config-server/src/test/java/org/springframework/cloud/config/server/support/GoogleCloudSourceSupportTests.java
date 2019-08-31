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

package org.springframework.cloud.config.server.support;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.jgit.transport.URIish;
import org.junit.Test;
import org.mockito.Mockito;

import org.springframework.cloud.config.server.support.GoogleCloudSourceSupport.CredentialsProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Eduard Wirch
 */
public class GoogleCloudSourceSupportTests {

	private static final String HTTPS_GOOGLE_CLOUD_SOURCE_REPO = "https://source.developers.google.com/r/somerepo";

	private static final String HTTP_GOOGLE_CLOUD_SOURCE_REPO = "http://source.developers.google.com/r/somerepo";

	private static final String SSH_GOOGLE_CLOUD_SOURCE_REPO = "ssh://source.developers.google.com/r/somerepo";

	private static final String HTTPS_OTHER_REPO = "https://somehub.com/r/somerepo";

	@Test
	public void verifySetsAuthHeadersForHttpsGCSRepo() throws URISyntaxException {
		Map<String, String> authHeaders = createAuthHeaders();
		TransportConfigCallback callback = transportConfigCallbackWith(authHeaders);

		TransportHttp transport = mockTransportHttp(HTTPS_GOOGLE_CLOUD_SOURCE_REPO);
		Map<String, String> actualHeaders = recordSetHeaders(transport);

		callback.configure(transport);

		assertThat(actualHeaders).containsAllEntriesOf(authHeaders);
	}

	@Test
	public void verifySetsAuthHeadersForHttpGCSRepo() throws URISyntaxException {
		Map<String, String> authHeaders = createAuthHeaders();
		TransportConfigCallback callback = transportConfigCallbackWith(authHeaders);

		TransportHttp transport = mockTransportHttp(HTTP_GOOGLE_CLOUD_SOURCE_REPO);
		Map<String, String> actualHeaders = recordSetHeaders(transport);

		callback.configure(transport);

		assertThat(actualHeaders).containsAllEntriesOf(authHeaders);
	}

	@Test
	public void verifyDoesNothingForSshGCSRepo() throws URISyntaxException {
		TransportConfigCallback callback = transportConfigCallbackWith(
				createAuthHeaders());
		TransportHttp transport = mockTransportHttp(SSH_GOOGLE_CLOUD_SOURCE_REPO);

		callback.configure(transport);

		verifyOnlyValidInteraction(transport);
	}

	@Test
	public void verifyDoesNothingForHttpsOtherRepo() throws URISyntaxException {
		TransportConfigCallback callback = transportConfigCallbackWith(
				createAuthHeaders());
		TransportHttp transport = mockTransportHttp(HTTPS_OTHER_REPO);

		callback.configure(transport);

		verifyOnlyValidInteraction(transport);
	}

	@Test
	public void verifyDoesNothingForNonHttpTransports() throws URISyntaxException {
		TransportConfigCallback callback = transportConfigCallbackWith(
				createAuthHeaders());
		Transport transport = mockSshTransport(SSH_GOOGLE_CLOUD_SOURCE_REPO);

		callback.configure(transport);

		verifyOnlyValidInteraction(transport);
	}

	private void verifyOnlyValidInteraction(Transport transport) {
		// Actually, we don't care how often getURI() was invoked, simply "allow"
		// invocation of getURI(), so verifyNoMoreInteractions() won't complain
		// about getURI().
		verify(transport, atMost(10000)).getURI();

		verifyNoMoreInteractions(transport);
	}

	private Map<String, String> createAuthHeaders() {
		Map<String, String> headers = new HashMap<>();
		headers.put("WWW-Authorization", "user:password");
		return headers;
	}

	private Map<String, String> recordSetHeaders(TransportHttp transport) {
		Map<String, String> headers = new HashMap<>();
		doAnswer(invocation -> {
			headers.putAll(invocation.getArgument(0));
			return null;
		}).when(transport).setAdditionalHeaders(anyMap());
		return headers;
	}

	private TransportHttp mockTransportHttp(String uri) throws URISyntaxException {
		TransportHttp transport = Mockito.mock(TransportHttp.class);

		when(transport.getURI()).thenReturn(new URIish(uri));

		return transport;
	}

	@SuppressWarnings("SameParameterValue")
	private Transport mockSshTransport(String uri) throws URISyntaxException {
		Transport transport = Mockito.mock(SshTransport.class);

		when(transport.getURI()).thenReturn(new URIish(uri));

		return transport;
	}

	private TransportConfigCallback transportConfigCallbackWith(
			Map<String, String> authHeaders) {
		CredentialsProvider credentialsProvider = () -> authHeaders;
		return new GoogleCloudSourceSupport()
				.createTransportConfigCallback(credentialsProvider);
	}

}
