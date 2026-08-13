/*
 * Copyright 2013-present the original author or authors.
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
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class AzureDevOpsWorkloadIdentitySupportTests {

	private static final String HTTPS_AZURE_DEVOPS_REPO = "https://dev.azure.com/tenant/repo";

	private static final String HTTP_AZURE_DEVOPS_REPO = "http://dev.azure.com/tenant/repo";

	private static final String SSH_AZURE_DEVOPS_REPO = "ssh://dev.azure.com/tenant/repo";

	private static final String HTTPS_OTHER_REPO = "https://github.com/example/repo";

	@Test
	public void canHandleHttpsAzureDevOpsRepo() {
		assertThat(new AzureDevOpsWorkloadIdentitySupport().canHandle(HTTPS_AZURE_DEVOPS_REPO)).isTrue();
	}

	@Test
	public void canHandleHttpAzureDevOpsRepo() {
		assertThat(new AzureDevOpsWorkloadIdentitySupport().canHandle(HTTP_AZURE_DEVOPS_REPO)).isTrue();
	}

	@Test
	public void doesNotHandleSshAzureDevOpsRepo() {
		assertThat(new AzureDevOpsWorkloadIdentitySupport().canHandle(SSH_AZURE_DEVOPS_REPO)).isFalse();
	}

	@Test
	public void doesNotHandleOtherRepo() {
		assertThat(new AzureDevOpsWorkloadIdentitySupport().canHandle(HTTPS_OTHER_REPO)).isFalse();
	}

	@Test
	public void verifySetsAuthHeaderForAzureDevOpsRepo() throws URISyntaxException {
		TokenCredential credential = mock(TokenCredential.class);
		when(credential.getToken(any(TokenRequestContext.class)))
			.thenReturn(Mono.just(new AccessToken("test-access-token", OffsetDateTime.now().plusHours(1))));

		TransportConfigCallback callback = new AzureDevOpsWorkloadIdentitySupport()
			.createTransportConfigCallback(credential);

		TransportHttp transport = mockTransportHttp(HTTPS_AZURE_DEVOPS_REPO);
		Map<String, String> actualHeaders = recordSetHeaders(transport);

		callback.configure(transport);

		assertThat(actualHeaders).containsEntry("Authorization", "Bearer test-access-token");
	}

	@Test
	public void verifyDoesNothingForOtherRepo() throws URISyntaxException {
		TokenCredential credential = mock(TokenCredential.class);

		TransportConfigCallback callback = new AzureDevOpsWorkloadIdentitySupport()
			.createTransportConfigCallback(credential);

		TransportHttp transport = mockTransportHttp(HTTPS_OTHER_REPO);

		callback.configure(transport);

		verifyNoMoreInteractions(credential);
		verifyOnlyValidInteraction(transport);
	}

	@Test
	public void verifyDoesNothingForNonHttpTransport() throws URISyntaxException {
		TokenCredential credential = mock(TokenCredential.class);

		TransportConfigCallback callback = new AzureDevOpsWorkloadIdentitySupport()
			.createTransportConfigCallback(credential);

		Transport transport = mockSshTransport(SSH_AZURE_DEVOPS_REPO);

		callback.configure(transport);

		verifyNoMoreInteractions(credential);
		verifyOnlyValidInteraction(transport);
	}

	@Test
	public void verifyDoesNothingWhenCredentialReturnsNoToken() throws URISyntaxException {
		TokenCredential credential = mock(TokenCredential.class);
		when(credential.getToken(any(TokenRequestContext.class))).thenReturn(Mono.empty());

		TransportConfigCallback callback = new AzureDevOpsWorkloadIdentitySupport()
			.createTransportConfigCallback(credential);

		TransportHttp transport = mockTransportHttp(HTTPS_AZURE_DEVOPS_REPO);
		Map<String, String> actualHeaders = recordSetHeaders(transport);

		callback.configure(transport);

		assertThat(actualHeaders).isEmpty();
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
		TransportHttp transport = mock(TransportHttp.class);
		when(transport.getURI()).thenReturn(new URIish(uri));
		return transport;
	}

	private Transport mockSshTransport(String uri) throws URISyntaxException {
		Transport transport = mock(SshTransport.class);
		when(transport.getURI()).thenReturn(new URIish(uri));
		return transport;
	}

	private void verifyOnlyValidInteraction(Transport transport) {
		verify(transport, atMost(10000)).getURI();
		verifyNoMoreInteractions(transport);
	}

}
