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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import com.google.auth.oauth2.GoogleCredentials;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.jgit.transport.URIish;

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
public final class GoogleCloudSourceSupport {

	boolean canHandle(String uri) {
		try {
			return GCSTransportConfigCallback.canHandle(new URIish(uri));
		}
		catch (URISyntaxException e) {
			return false;
		}
	}

	// This detour via GCSTransportConfigCallback was necessary because:
	// - we want the Google Cloud credentials provider to be a bean, so we can use
	// @ConditionalOnClass to conditionally disable support, if required classes
	// are not on the class path.
	// - We cannot make a class implementing TransportConfigCallback a bean,
	// because Spring would populate customTransportConfigCallback with this bean
	// (see JGitFactoryConfig.gitEnvironmentRepositoryFactory()), and report a
	// conflict whenever there is a real "custom" TransportConfigCallback
	// implementation in the Spring context.
	// This is why GoogleCloudSourceSupport is the optional bean, which can provide
	// the TransportConfigCallback on request.
	TransportConfigCallback createTransportConfigCallback() {
		return new GCSTransportConfigCallback(
				new ApplicationDefaultCredentialsProvider());
	}

	TransportConfigCallback createTransportConfigCallback(
			CredentialsProvider credentialsProvider) {
		return new GCSTransportConfigCallback(credentialsProvider);
	}

	private static final class GCSTransportConfigCallback
			implements TransportConfigCallback {

		private static final String GOOGLE_CLOUD_SOURCE_HOST = "source.developers.google.com";

		private final CredentialsProvider credentialsProvider;

		private GCSTransportConfigCallback(CredentialsProvider credentialsProvider) {
			this.credentialsProvider = credentialsProvider;
		}

		@Override
		public void configure(Transport transport) {
			if (transport instanceof TransportHttp && canHandle(transport.getURI())) {
				addHeaders((TransportHttp) transport,
						credentialsProvider.getAuthorizationHeaders());
			}
		}

		private static boolean canHandle(URIish uri) {
			return isHttpScheme(uri) && isGoogleCloudSourceHost(uri);
		}

		private static boolean isHttpScheme(URIish uri) {
			final String scheme = uri.getScheme();
			return Objects.equals(scheme, "http") || Objects.equals(scheme, "https");
		}

		private static boolean isGoogleCloudSourceHost(URIish uri) {
			return Objects.equals(uri.getHost(), GOOGLE_CLOUD_SOURCE_HOST);
		}

		private void addHeaders(TransportHttp transport, Map<String, String> headers) {
			transport.setAdditionalHeaders(headers);
		}

	}

	interface CredentialsProvider {

		Map<String, String> getAuthorizationHeaders();

	}

	private static class ApplicationDefaultCredentialsProvider
			implements CredentialsProvider {

		@Override
		public Map<String, String> getAuthorizationHeaders() {
			try {
				return GoogleCredentials.getApplicationDefault().getRequestMetadata()
						.entrySet().stream()
						.collect(toMap(Entry::getKey, this::joinValues));
			}
			catch (IOException ex) {
				throw new IllegalStateException(ex);
			}
		}

		private String joinValues(Entry<?, List<String>> entry) {
			return String.join(", ", entry.getValue());
		}

	}

}
