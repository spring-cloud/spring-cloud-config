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

package org.springframework.cloud.config.client;

import java.net.URI;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.MediaType;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * IntegrationTest for OAuth2 support in ConfigClientRequestTemplateFactory using Keycloak
 * Test container as the Authorization Server and MockServer as a protected resource
 * server.
 */
@Tag("DockerRequired")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfigClientRequestTemplateFactoryOAuth2Tests {

	private static final Log log = LogFactory.getLog(ConfigClientRequestTemplateFactoryOAuth2Tests.class);

	@Container
	static KeycloakContainer keycloak = new KeycloakContainer().withRealmImportFile("test-realm.json"); // classpath
																										// resource

	private ClientAndServer mockServer;

	private MockServerClient mockClient;

	@BeforeAll
	void startMockServer() {
		mockServer = ClientAndServer.startClientAndServer(0);
		mockClient = new MockServerClient("localhost", mockServer.getLocalPort());
	}

	@BeforeEach
	void resetExpectations() {
		mockClient.clear(request().withPath("/secure"));
		mockClient.when(request().withMethod("GET").withPath("/secure")).respond(request -> {
			if (request.containsHeader("Authorization")) {
				String authHeader = request.getFirstHeader("Authorization");
				if (authHeader != null && authHeader.startsWith("Bearer ")) {
					return response().withStatusCode(200).withContentType(MediaType.TEXT_PLAIN).withBody("ok");
				}
			}
			return response().withStatusCode(401);
		});
	}

	@AfterAll
	void tearDown() {
		if (mockClient != null) {
			mockClient.close();
		}
		if (mockServer != null) {
			mockServer.stop();
		}
	}

	@Test
	void restTemplateAddsBearerTokenFromKeycloakUsingClientCredentials() {
		// given OAuth2 client configuration pointing to Keycloak token endpoint
		String tokenUri = keycloak.getAuthServerUrl() + "/realms/test-realm/protocol/openid-connect/token";

		ConfigClientProperties props = new ConfigClientProperties();
		props.getOauth2().setEnabled(true);

		OAuth2ClientProperties.Provider provider = props.getOauth2().getProvider();
		provider.setTokenUri(tokenUri);

		OAuth2ClientProperties.Registration registration = props.getOauth2().getRegistration();
		registration.setClientId("config-client");
		registration.setClientSecret("my-client-secret");
		registration.setAuthorizationGrantType("client_credentials");

		ConfigClientRequestTemplateFactory factory = new ConfigClientRequestTemplateFactory(log, props);
		RestTemplate restTemplate = factory.create();

		// when
		String url = "http://localhost:" + mockServer.getLocalPort() + "/secure";
		ResponseEntity<String> response = restTemplate.getForEntity(URI.create(url), String.class);

		// then
		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getBody()).isEqualTo("ok");

		HttpRequest[] recorded = mockClient.retrieveRecordedRequests(request().withPath("/secure"));
		assertThat(recorded).hasSize(1);
		String authHeader = recorded[0].getFirstHeader("Authorization");
		assertThat(authHeader).isNotNull().startsWith("Bearer ");
	}

	@Test
	void restTemplateAddsBearerTokenFromKeycloakUsingClientCredentialsAndIssuerUri() {
		// given OAuth2 client configuration pointing to Keycloak issuer endpoint
		String issuerUri = keycloak.getAuthServerUrl() + "/realms/test-realm";

		ConfigClientProperties props = new ConfigClientProperties();
		props.getOauth2().setEnabled(true);

		OAuth2ClientProperties.Provider provider = props.getOauth2().getProvider();
		provider.setIssuerUri(issuerUri);

		OAuth2ClientProperties.Registration registration = props.getOauth2().getRegistration();
		registration.setClientId("config-client");
		registration.setClientSecret("my-client-secret");
		registration.setAuthorizationGrantType("client_credentials");

		ConfigClientRequestTemplateFactory factory = new ConfigClientRequestTemplateFactory(log, props);
		RestTemplate restTemplate = factory.create();

		// when
		String url = "http://localhost:" + mockServer.getLocalPort() + "/secure";
		ResponseEntity<String> response = restTemplate.getForEntity(URI.create(url), String.class);

		// then
		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(response.getBody()).isEqualTo("ok");

		HttpRequest[] recorded = mockClient.retrieveRecordedRequests(request().withPath("/secure"));
		assertThat(recorded).hasSize(1);
		String authHeader = recorded[0].getFirstHeader("Authorization");
		assertThat(authHeader).isNotNull().startsWith("Bearer ");
	}

	@Test
	void restTemplateDoesNotAddAuthorizationHeaderWhenOauth2Disabled() {
		// given ConfigClientProperties with OAuth2 disabled
		ConfigClientProperties props = new ConfigClientProperties();
		props.getOauth2().setEnabled(false); // explicitly disabled

		ConfigClientRequestTemplateFactory factory = new ConfigClientRequestTemplateFactory(log, props);
		RestTemplate restTemplate = factory.create();

		// when
		String url = "http://localhost:" + mockServer.getLocalPort() + "/secure";

		assertThatThrownBy(() -> restTemplate.getForEntity(URI.create(url), String.class))
			.isInstanceOf(org.springframework.web.client.HttpClientErrorException.Unauthorized.class);

		// then

		HttpRequest[] recorded = mockClient.retrieveRecordedRequests(request().withPath("/secure"));
		assertThat(recorded).hasSize(1); // only this test's request
		assertThat(recorded[0].containsHeader("Authorization")).isFalse();
	}

}
