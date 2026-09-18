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
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

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

import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.cloud.config.client.oauth2.ConfigClientOAuth2Support;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * IntegrationTest for OAuth2 support in ConfigClientRequestTemplateFactory using Keycloak
 * Test container as the Authorization Server and MockServer as a protected resource
 * server.
 *
 * <p>
 * Each test drives the real wiring entry point
 * {@link ConfigClientOAuth2Support#registerInterceptor} with bound properties, exactly as
 * the Config Data flow does at runtime.
 * </p>
 */
@Tag("DockerRequired")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfigClientRequestTemplateFactoryOAuth2Tests {

	private static final Log log = LogFactory.getLog(ConfigClientRequestTemplateFactoryOAuth2Tests.class);

	private static final String REGISTRATION_ID = "config-client";

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

		Map<String, Object> properties = baseOAuth2Properties();
		properties.put("spring.security.oauth2.client.provider." + REGISTRATION_ID + ".token-uri", tokenUri);

		RestTemplate restTemplate = createRestTemplate(new DefaultBootstrapContext(), properties);

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

		Map<String, Object> properties = baseOAuth2Properties();
		properties.put("spring.security.oauth2.client.provider." + REGISTRATION_ID + ".issuer-uri", issuerUri);

		RestTemplate restTemplate = createRestTemplate(new DefaultBootstrapContext(), properties);

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
		// given config-client OAuth2 disabled
		Map<String, Object> properties = new HashMap<>();
		properties.put("spring.cloud.config.oauth2.enabled", "false");

		RestTemplate restTemplate = createRestTemplate(new DefaultBootstrapContext(), properties);

		// when
		String url = "http://localhost:" + mockServer.getLocalPort() + "/secure";

		assertThatThrownBy(() -> restTemplate.getForEntity(URI.create(url), String.class))
			.isInstanceOf(org.springframework.web.client.HttpClientErrorException.Unauthorized.class);

		// then
		HttpRequest[] recorded = mockClient.retrieveRecordedRequests(request().withPath("/secure"));
		assertThat(recorded).hasSize(1); // only this test's request
		assertThat(recorded[0].containsHeader("Authorization")).isFalse();
	}

	@Test
	void userSuppliedAuthorizedClientManagerWins() {
		// given a bootstrap context with a user-supplied OAuth2AuthorizedClientManager
		// already registered — Keycloak is not configured at all, proving the default
		// path is bypassed.
		Map<String, Object> properties = new HashMap<>();
		properties.put("spring.cloud.config.oauth2.enabled", "true");
		properties.put("spring.cloud.config.oauth2.client-registration-id", REGISTRATION_ID);

		DefaultBootstrapContext bootstrapContext = new DefaultBootstrapContext();
		OAuth2AuthorizedClientManager stubManager = stubManagerReturning("user-supplied-token");
		bootstrapContext.register(OAuth2AuthorizedClientManager.class, ctx -> stubManager);

		RestTemplate restTemplate = createRestTemplate(bootstrapContext, properties);

		// when
		String url = "http://localhost:" + mockServer.getLocalPort() + "/secure";
		ResponseEntity<String> response = restTemplate.getForEntity(URI.create(url), String.class);

		// then
		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		HttpRequest[] recorded = mockClient.retrieveRecordedRequests(request().withPath("/secure"));
		assertThat(recorded).hasSize(1);
		assertThat(recorded[0].getFirstHeader("Authorization")).isEqualTo("Bearer user-supplied-token");
	}

	private static Map<String, Object> baseOAuth2Properties() {
		Map<String, Object> properties = new HashMap<>();
		properties.put("spring.cloud.config.oauth2.enabled", "true");
		properties.put("spring.cloud.config.oauth2.client-registration-id", REGISTRATION_ID);
		properties.put("spring.security.oauth2.client.registration." + REGISTRATION_ID + ".client-id", "config-client");
		properties.put("spring.security.oauth2.client.registration." + REGISTRATION_ID + ".client-secret",
				"my-client-secret");
		properties.put("spring.security.oauth2.client.registration." + REGISTRATION_ID + ".authorization-grant-type",
				"client_credentials");
		return properties;
	}

	private static RestTemplate createRestTemplate(DefaultBootstrapContext bootstrapContext,
			Map<String, Object> properties) {
		Binder binder = new Binder(new MapConfigurationPropertySource(properties));
		ConfigClientRequestTemplateFactory factory = new ConfigClientRequestTemplateFactory(log,
				new ConfigClientProperties());
		ConfigClientOAuth2Support.registerInterceptor(bootstrapContext, binder, null, factory);
		return factory.create();
	}

	private static OAuth2AuthorizedClientManager stubManagerReturning(String tokenValue) {
		ClientRegistration registration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
			.clientId("config-client")
			.clientSecret("my-client-secret")
			.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
			.tokenUri("http://stub/token")
			.build();
		OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, tokenValue,
				Instant.now(), Instant.now().plusSeconds(60));
		OAuth2AuthorizedClient authorizedClient = new OAuth2AuthorizedClient(registration, "config-client",
				accessToken);
		return authorizeRequest -> authorizedClient;
	}

}
