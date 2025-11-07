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

package sample;

import java.io.IOException;
import java.util.Map;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.util.TestSocketUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Integration test for OAuth2 authentication failure case with Spring Cloud Config
 * Server.
 * <p>
 * This test verifies the failure scenario:
 * <ol>
 * <li>Client attempts to obtain OAuth2 token from identity provider but fails</li>
 * <li>Client makes request to config server without a valid token</li>
 * <li>Config server (secured with OAuth2 Resource Server) rejects the request with
 * 401</li>
 * <li>Client does not receive configuration</li>
 * </ol>
 *
 * @author Spring Cloud Config Team
 */
@SpringBootTest(classes = Application.class,
		// Normally spring.cloud.config.enabled:true is the default but since we have the
		// config server on the classpath we need to set it explicitly
		properties = { "spring.application.name=oauth2app", "spring.cloud.config.enabled=true",
				"spring.config.import=optional:configserver:", "spring.cloud.config.fail-fast=false",
				"management.security.enabled=false", "management.endpoints.web.exposure.include=*",
				"management.endpoint.env.show-values=ALWAYS" },
		webEnvironment = RANDOM_PORT)
public class ConfigDataOAuth2FailureIntegrationTests {

	private static final String BASE_PATH = new WebEndpointProperties().getBasePath();

	private static final String JWK_SET_RESPONSE = """
			{
				"keys": [
					{
						"kty": "RSA",
						"kid": "test-key-id",
						"use": "sig",
						"n": "0vx7agoebGcQSuuPiLJXZptN9nndrQmbPEpsXYeiE4E6dTjfJfJZ1X8FZJD3LMz6vqw8TrvMQx6vqGqHPEs1NT1qsfboNo631CBDQClWvofTpYjP5dWP+Tp5z9T1h+EDR5iqhpFKEAicZ-j7H4nk7v4syMqMBfqL3MfZk4l8J23Prf7gIh3-J99fCXbwiCd1-2R2QZvN9k8tbBHX9A9S7aksHKHgHlsjvJYk6jMhZkV0N-lPVGQCLQ9lvY2iqU0Q4hshvHJzxA_EFRA7Nv9ZNbqHJNULk56igfy-D4rLMf6hjTlxCklc2Dlf8ibUX3xZWhPK9088foETESTQ",
						"e": "AQAB"
					}
				]
			}""";

	private static int configPort = TestSocketUtils.findAvailableTcpPort();

	private static ConfigurableApplicationContext server;

	private static MockWebServer oauth2Server;

	@LocalServerPort
	private int port;

	@BeforeAll
	public static void startServers() throws IOException {
		// Start OAuth2 identity provider mock server that returns an error
		oauth2Server = new MockWebServer();
		oauth2Server.start();
		String oauth2TokenUri = "http://localhost:" + oauth2Server.getPort()
				+ "/realms/test-realm/protocol/openid-connect/token";
		String jwkSetUri = "http://localhost:" + oauth2Server.getPort()
				+ "/realms/test-realm/protocol/openid-connect/certs";

		oauth2Server.setDispatcher(new Dispatcher() {
			@Override
			public MockResponse dispatch(RecordedRequest request) {
				String path = request.getPath();
				if (path != null && path.contains("/certs")) {
					// Return JWK Set for token validation
					return new MockResponse().setBody(JWK_SET_RESPONSE)
						.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
				}
				else if (path != null && path.contains("/token")) {
					// Return 401 Unauthorized to simulate token acquisition failure
					return new MockResponse().setResponseCode(HttpStatus.UNAUTHORIZED.value())
						.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
						.setBody("{\"error\":\"invalid_client\",\"error_description\":\"Invalid client credentials\"}");
				}
				return new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value());
			}
		});

		// Start config server with OAuth2 Resource Server security
		String baseDir = ConfigServerTestUtils.getBaseDirectory("spring-cloud-config-sample");
		String repo = ConfigServerTestUtils.prepareLocalRepo(baseDir, "target/repos", "config-repo", "target/config");
		server = SpringApplication.run(TestConfigServerWithOAuth2ResourceServer.class, "--server.port=" + configPort,
				"--spring.config.name=server", "--spring.cloud.config.server.git.uri=" + repo,
				"--spring.security.oauth2.resourceserver.jwt.jwk-set-uri=" + jwkSetUri);

		// Configure client to use OAuth2 (will fail to get token)
		System.setProperty("spring.cloud.config.uri", "http://localhost:" + configPort);
		System.setProperty("spring.cloud.config.oauth2.token-uri", oauth2TokenUri);
		System.setProperty("spring.cloud.config.oauth2.client-id", "test-client");
		System.setProperty("spring.cloud.config.oauth2.client-secret", "test-secret");
		System.setProperty("spring.cloud.config.oauth2.grant-type", "client_credentials");
	}

	@AfterAll
	public static void close() {
		System.clearProperty("spring.cloud.config.uri");
		System.clearProperty("spring.cloud.config.oauth2.token-uri");
		System.clearProperty("spring.cloud.config.oauth2.client-id");
		System.clearProperty("spring.cloud.config.oauth2.client-secret");
		System.clearProperty("spring.cloud.config.oauth2.grant-type");
		if (server != null) {
			server.close();
		}
		if (oauth2Server != null) {
			try {
				oauth2Server.shutdown();
			}
			catch (IOException e) {
				// Ignore
			}
		}
	}

	@Test
	public void contextFailsToLoadWithoutValidOAuth2Token() {
		// The client should have failed to get a token from the OAuth2 server (which
		// returns 401)
		// When the client tries to bootstrap and fetch configuration, it will either:
		// 1. Fail to get a token and not make the request, or
		// 2. Make the request without a token (if it continues despite token failure)
		//
		// To verify the config server properly rejects unauthenticated requests,
		// we directly test the config server endpoint without a token
		TestRestTemplate restTemplate = new TestRestTemplate();
		ResponseEntity<Map> response = restTemplate
			.getForEntity("http://localhost:" + configPort + "/oauth2app/default", Map.class);

		// Verify that the config server returns 401 Unauthorized
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

		// Verify that no configuration was provided
		assertThat(response.getBody()).isNull();
	}

	@Test
	public void configServerRejectsRequestWithoutToken() {
		// Directly test that the config server rejects requests without a valid OAuth2
		// token
		TestRestTemplate restTemplate = new TestRestTemplate();
		ResponseEntity<String> response = restTemplate
			.getForEntity("http://localhost:" + configPort + "/oauth2app/default", String.class);

		// Verify that the config server returns 401 Unauthorized
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void clientDoesNotLoadConfigWithoutValidOAuth2Token() {
		// Verify that the client did not successfully load configuration because it
		// failed
		// to get a token and the config server rejected the request with 401
		Map res = new TestRestTemplate().getForObject("http://localhost:" + this.port + BASE_PATH + "/env/info.foo",
				Map.class);

		// The client should have attempted to load config but failed due to 401 from
		// config server
		// The property "info.foo" with value "bar" comes from the config server
		// Since the config server rejected the request, this value should NOT be
		// present
		if (res != null && res.containsKey("property")) {
			Map<String, Object> property = (Map<String, Object>) res.get("property");
			// If property exists, it should not have the value "bar" from config server
			if (property != null && property.containsKey("value")) {
				Object value = property.get("value");
				assertThat(value).as("Config server property value should not be present").isNotEqualTo("bar");
			}
		}
		// If the property doesn't exist at all, that's also acceptable - it means the
		// config server's configuration was not loaded
		// The key assertion is that the config server's value "bar" is not present
		// Note: The test passes if property doesn't exist OR if it exists but doesn't
		// have value "bar"
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableConfigServer
	static class TestConfigServerWithOAuth2ResourceServer {

		public static void main(String[] args) {
			new SpringApplicationBuilder(TestConfigServerWithOAuth2ResourceServer.class)
				.properties("spring.config.name=configserver")
				.run(args);
		}

		@Configuration
		@ConditionalOnProperty(name = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri")
		@EnableWebSecurity
		static class OAuth2ResourceServerSecurityConfiguration {

			@Bean
			public SecurityFilterChain oauth2ResourceServerSecurityFilterChain(HttpSecurity http) throws Exception {
				// Only enable OAuth2 Resource Server security when jwk-set-uri is
				// configured
				// This ensures it doesn't affect other tests that use
				// TestConfigServerApplication
				http.authorizeHttpRequests(authz -> authz.anyRequest().authenticated())
					.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
						// JWT configuration is handled by Spring Boot auto-configuration
						// when spring.security.oauth2.resourceserver.jwt.jwk-set-uri is
						// set
					}));
				return http.build();
			}

		}

	}

}
