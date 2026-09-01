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
import java.util.Map;
import java.util.function.Supplier;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.jgit.transport.URIish;

import org.springframework.util.StringUtils;

public class AzureDevOpsWorkloadIdentitySupport {

	private static final String AZURE_DEVOPS_HOST = "dev.azure.com";

	private static final String AZURE_DEVOPS_SCOPE = "499b84ac-1321-427f-aa17-267ca6975798/.default";

	public boolean canHandle(String uri) {
		try {
			URIish urish = new URIish(uri);
			return ("http".equals(urish.getScheme()) || "https".equals(urish.getScheme()))
					&& AZURE_DEVOPS_HOST.equalsIgnoreCase(urish.getHost());
		}
		catch (URISyntaxException ex) {
			return false;
		}
	}

	public TransportConfigCallback createTransportConfigCallback(String clientId) {
		ManagedIdentityCredentialBuilder builder = new ManagedIdentityCredentialBuilder();
		if (StringUtils.hasText(clientId)) {
			builder.clientId(clientId);
		}
		TokenCredential credential = builder.build();
		return createTransportConfigCallback(() -> {
			AccessToken accessToken = credential.getToken(new TokenRequestContext().addScopes(AZURE_DEVOPS_SCOPE))
				.block();
			return accessToken != null ? accessToken.getToken() : null;
		});
	}

	TransportConfigCallback createTransportConfigCallback(Supplier<String> tokenSupplier) {
		return transport -> {
			if (transport instanceof TransportHttp && canHandle(transport.getURI().toString())) {
				String accessToken = tokenSupplier.get();

				if (accessToken != null) {
					((TransportHttp) transport).setAdditionalHeaders(Map.of("Authorization", "Bearer " + accessToken));
				}
			}
		};
	}

}
