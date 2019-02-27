/*
 * Copyright 2013-2019 the original author or authors.
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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import com.google.auth.oauth2.GoogleCredentials;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;

import static java.util.stream.Collectors.toMap;

/**
 * Provides credentials for Google Cloud Source repositories by adding a
 * {@code Authenticate} http header.
 * <p/>
 * It does so by acting as a transport configurer. If a transport instance targets a
 * Google Cloud Source repository, this implementation retrieves Google Cloud application
 * default credentials and adds them as a http header.
 *
 * @author Eduard Wirch
 * @see <a href=
 * "https://cloud.google.com/sdk/gcloud/reference/auth/application-default/login"> gcloud
 * auth application-default login</a>
 */
public class GoogleCloudSourceCredentialsProvider implements TransportConfigCallback {

	private static final String GOOGLE_CLOUD_SOURCE_DOMAIN = "source.developers.google.com";

	@Override
	public void configure(Transport transport) {
		if (transport instanceof TransportHttp && isGoogleCloudSourceUri(transport)) {
			addHeaders((TransportHttp) transport, getAuthorizationHeaders());
		}
	}

	private boolean isGoogleCloudSourceUri(Transport transport) {
		return Objects.equals(transport.getURI().getHost(), GOOGLE_CLOUD_SOURCE_DOMAIN);
	}

	private void addHeaders(TransportHttp transport, Map<String, String> headers) {
		transport.setAdditionalHeaders(headers);
	}

	private Map<String, String> getAuthorizationHeaders() {
		try {
			return GoogleCredentials.getApplicationDefault().getRequestMetadata()
					.entrySet().stream().collect(toMap(Entry::getKey, this::joinValues));
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private String joinValues(Entry<?, List<String>> entry) {
		return String.join(", ", entry.getValue());
	}

}
