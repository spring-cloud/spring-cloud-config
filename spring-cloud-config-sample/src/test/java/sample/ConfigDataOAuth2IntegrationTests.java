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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Integration test for OAuth2 authentication with Spring Cloud Config Server.
 * <p>
 * This test verifies the end-to-end flow:
 * <ol>
 * <li>Client obtains OAuth2 token from identity provider (mocked with MockWebServer)</li>
 * <li>Client sends Bearer token to config server</li>
 * <li>Config server receives and validates the token</li>
 * <li>Client successfully retrieves configuration</li>
 * </ol>
 *
 * @author Spring Cloud Config Team
 */
@SpringBootTest(classes = Application.class,
		// Normally spring.cloud.config.enabled:true is the default but since we have the
		// config server on the classpath we need to set it explicitly
		properties = { "spring.application.name=oauth2app", "spring.cloud.config.enabled=true",
				"spring.config.import=configserver:", "management.security.enabled=false",
				"management.endpoints.web.exposure.include=*", "management.endpoint.env.show-values=ALWAYS" },
		webEnvironment = RANDOM_PORT)
public class ConfigDataOAuth2IntegrationTests {

	private static final String BASE_PATH = new WebEndpointProperties().getBasePath();

	private static final String TOKEN_RESPONSE = """
			{"access_token": "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJVQ2RaakhjZ0tFU0NFV0w3V1JlMWpnaVRyQVNGbFhndU5CZWVGN1VlUnZJIn0.eyJleHAiOjE3MDE0NDQ2NTcsImlhdCI6MTcwMTQ0NDM1NywianRpIjoiN2IzZTczZTMtZWJlYy00YWE3LWI0MjgtZjRlMmJiYTQxYWNlIiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo5MDgwL3JlYWxtcy9vc2ludC1yZWFsbSIsImF1ZCI6ImFjY291bnQiLCJzdWIiOiJhN2EyNWZiMC02MzljLTQxYTktYmEwNS04NDlkYTE0Y2NiMzQiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJvc2ludC1rZXljbG9hay1jbGllbnQiLCJhY3IiOiIxIiwiYWxsb3dlZC1vcmlnaW5zIjpbImh0dHA6Ly9sb2NhbGhvc3Q6ODM1MSJdLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsib2ZmbGluZV9hY2Nlc3MiLCJkZWZhdWx0LXJvbGVzLW9zaW50LXJlYWxtIiwidW1hX2F1dGhvcml6YXRpb24iXX0sInJlc291cmNlX2FjY2VzcyI6eyJvc2ludC1rZXljbG9hay1jbGllbnQiOnsicm9sZXMiOlsidW1hX3Byb3RlY3Rpb24iLCJtMm0iXX0sImFjY291bnQiOnsicm9sZXMiOlsibWFuYWdlLWFjY291bnQiLCJtYW5hZ2UtYWNjb3VudC1saW5rcyIsInZpZXctcHJvZmlsZSJdfX0sInNjb3BlIjoicHJvZmlsZSBlbWFpbCIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwiY2xpZW50SG9zdCI6IjE3Mi4xNy4wLjEiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJzZXJ2aWNlLWFjY291bnQtb3NpbnQta2V5Y2xvYWstY2xpZW50IiwiY2xpZW50QWRkcmVzcyI6IjE3Mi4xNy4wLjEiLCJjbGllbnRfaWQiOiJvc2ludC1rZXljbG9hay1jbGllbnQifQ.AFY1g8_DxAq1eXd-qpJP2PD-8g7-n_gIeScX_ESLWB6UHnZtxe_MvPYvn13X6K4olDsPZ37EGE4-BkdY8pgs-UBCD6EsFD_aZmf9PjZFsHDacAKotcvjsb5U9kqbm0wPuyhrhgSftkrx9AceHe_wETzAPoI775MuyQgSWjihLqLnOZSIMu24t-Ga07Xn0yaOoTD2tS1lfkgXWwRrQF1_KQxHftvJDDhJEN0rfEVJ7SOr9meWH0IQ1w8HgjRFBBkOhtp",
			"expires_in": 300,
			"refresh_expires_in": 0,
			"token_type": "Bearer",
			"not-before-policy": 0,
			"scope": "profile email"
			}""";

	private static int configPort = TestSocketUtils.findAvailableTcpPort();

	private static ConfigurableApplicationContext server;

	private static MockWebServer oauth2Server;

	// Captures the Authorization header sent to the config server
	private static final AtomicReference<String> capturedAuthorizationHeader = new AtomicReference<>();

	@LocalServerPort
	private int port;

	@BeforeAll
	public static void startServers() throws IOException {
		// Start OAuth2 identity provider mock server
		oauth2Server = new MockWebServer();
		oauth2Server.start();
		String oauth2TokenUri = "http://localhost:" + oauth2Server.getPort()
				+ "/realms/test-realm/protocol/openid-connect/token";
		oauth2Server.setDispatcher(new Dispatcher() {
			@Override
			public MockResponse dispatch(RecordedRequest request) {
				return new MockResponse().setBody(TOKEN_RESPONSE)
					.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
			}
		});

		// Start config server with OAuth2 token validation interceptor
		String baseDir = ConfigServerTestUtils.getBaseDirectory("spring-cloud-config-sample");
		String repo = ConfigServerTestUtils.prepareLocalRepo(baseDir, "target/repos", "config-repo", "target/config");
		server = SpringApplication.run(
				new Class[] { TestConfigServerWithOAuth2.class, DisableSpringSecurityConfig.class },
				new String[] { "--server.port=" + configPort, "--spring.config.name=server",
						"--spring.cloud.config.server.git.uri=" + repo });

		// Configure client to use OAuth2
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

	static Map<String, String> parseFormBody(String body) {
		// Parse the form-encoded string
		Map<String, String> parsedBody = new HashMap<>();
		String[] pairs = body.split("&");
		for (String pair : pairs) {
			int idx = pair.indexOf("=");
			if (idx > 0) {
				String key = java.net.URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
				String value = java.net.URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
				parsedBody.put(key, value);
			}
		}
		return parsedBody;
	}

	@Test
	@SuppressWarnings("unchecked")
	public void contextLoadsWithOAuth2() {
		// Verify the client successfully loaded configuration using OAuth2
		Map res = new TestRestTemplate().getForObject("http://localhost:" + this.port + BASE_PATH + "/env/info.foo",
				Map.class);
		assertThat(res).containsKey("propertySources");
		Map<String, Object> property = (Map<String, Object>) res.get("property");
		assertThat(property).containsEntry("value", "bar");

		// Verify that the OAuth2 Bearer token was sent to the config server
		String authHeader = capturedAuthorizationHeader.get();
		assertThat(authHeader).isNotNull();
		assertThat(authHeader).startsWith("Bearer ");
		assertThat(authHeader.length()).isGreaterThan(7); // "Bearer " + token
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableConfigServer
	static class TestConfigServerWithOAuth2 implements WebMvcConfigurer {

		public static void main(String[] args) {
			new SpringApplicationBuilder(TestConfigServerWithOAuth2.class).properties("spring.config.name=configserver")
				.run(args);
		}

		@Override
		public void addInterceptors(InterceptorRegistry registry) {
			registry.addInterceptor(new HandlerInterceptor() {
				@Override
				public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
						throws Exception {
					// Capture the Authorization header to verify OAuth2 token was sent
					String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
					if (authHeader != null) {
						capturedAuthorizationHeader.set(authHeader);
					}
					return true;
				}
			});
		}

	}

}
