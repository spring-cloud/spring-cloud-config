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

package org.springframework.cloud.config.client;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.config.client.ConfigClientProperties.AUTHORIZATION;

/**
 * @author Bruce Randall
 *
 */
class ConfigClientRequestTemplateFactoryTest {

	private static final Log LOG = LogFactory.getLog(ConfigClientRequestTemplateFactoryTest.class);

	public static MockWebServer mockWebServer;

	private static String idpUrl;

	private static final String TOKEN_RESPONSE = """
			{"access_token": "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJVQ2RaakhjZ0tFU0NFV0w3V1JlMWpnaVRyQVNGbFhndU5CZWVGN1VlUnZJIn0.eyJleHAiOjE3MDE0NDQ2NTcsImlhdCI6MTcwMTQ0NDM1NywianRpIjoiN2IzZTczZTMtZWJlYy00YWE3LWI0MjgtZjRlMmJiYTQxYWNlIiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo5MDgwL3JlYWxtcy9vc2ludC1yZWFsbSIsImF1ZCI6ImFjY291bnQiLCJzdWIiOiJhN2EyNWZiMC02MzljLTQxYTktYmEwNS04NDlkYTE0Y2NiMzQiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJvc2ludC1rZXljbG9hay1jbGllbnQiLCJhY3IiOiIxIiwiYWxsb3dlZC1vcmlnaW5zIjpbImh0dHA6Ly9sb2NhbGhvc3Q6ODM1MSJdLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsib2ZmbGluZV9hY2Nlc3MiLCJkZWZhdWx0LXJvbGVzLW9zaW50LXJlYWxtIiwidW1hX2F1dGhvcml6YXRpb24iXX0sInJlc291cmNlX2FjY2VzcyI6eyJvc2ludC1rZXljbG9hay1jbGllbnQiOnsicm9sZXMiOlsidW1hX3Byb3RlY3Rpb24iLCJtMm0iXX0sImFjY291bnQiOnsicm9sZXMiOlsibWFuYWdlLWFjY291bnQiLCJtYW5hZ2UtYWNjb3VudC1saW5rcyIsInZpZXctcHJvZmlsZSJdfX0sInNjb3BlIjoicHJvZmlsZSBlbWFpbCIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwiY2xpZW50SG9zdCI6IjE3Mi4xNy4wLjEiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJzZXJ2aWNlLWFjY291bnQtb3NpbnQta2V5Y2xvYWstY2xpZW50IiwiY2xpZW50QWRkcmVzcyI6IjE3Mi4xNy4wLjEiLCJjbGllbnRfaWQiOiJvc2ludC1rZXljbG9hay1jbGllbnQifQ.AFY1g8_DxAq1eXd-qpJP2PD-8g7-n_gIeScX_ESLWB6UHnZtxe_MvPYvn13X6K4olDsPZ37EGE4-BkdY8pgs-UBCD6EsFD_aZmf9PjZFsHDacAKotcvjsb5U9kqbm0wPuyhrhgSftkrx9AceHe_wETzAPoI775MuyQgSWjihLqLnOZSIMu24t-Ga07Xn0yaOoTD2tS1lfkgXWwRrQF1_KQxHftvJDDhJEN0rfEVJ7SOr9meWH0IQ1w8HgjRFBBkOhtp",
			"expires_in": 300,
			"refresh_expires_in": 0,
			"token_type": "Bearer",
			"not-before-policy": 0,
			"scope": "profile email"
			}""";

	@BeforeAll
	static void beforeAll() throws IOException {
		mockWebServer = new MockWebServer();
		mockWebServer.start();
		idpUrl = String.format("http://localhost:%s/", mockWebServer.getPort());
	}

	@AfterAll
	static void afterAll() throws IOException {
		mockWebServer.shutdown();
	}

	@Test
	void whenParseTokenResponse_givenValidJson_thenParseToken() {
		// given
		ConfigClientProperties properties = new ConfigClientProperties(new MockEnvironment());
		ConfigClientRequestTemplateFactory templateFactory = new ConfigClientRequestTemplateFactory(LOG, properties);

		// when
		Optional<AccessTokenResponse> tokenOpt = ReflectionTestUtils.invokeMethod(templateFactory, "parseTokenResponse",
				TOKEN_RESPONSE);
		// then
		assertThat(tokenOpt).isPresent();
		AccessTokenResponse tokenResponse = tokenOpt.get();
		assertThat(tokenResponse.getAccessToken()).isNotNull();
		assertThat(tokenResponse.getAccessToken())
				.startsWith("eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJVQ2RaakhjZ0tFU0NFV0w3V1JlMWpnaVR");
		assertThat(tokenResponse.getAccessToken()).endsWith("WH0IQ1w8HgjRFBBkOhtp");
		assertThat(tokenResponse.getTokenType()).isNotNull();
		assertThat(tokenResponse.getTokenType()).isEqualTo("Bearer");
		assertThat(tokenResponse.getBearerHeader())
				.isEqualTo(tokenResponse.getTokenType() + " " + tokenResponse.getAccessToken());
		assertThat(templateFactory.getLog()).isNotNull();
		assertThat(templateFactory.getProperties()).isNotNull();
	}

	@Test
	void whenCreate_givenTokenUri_thenGetOAuthToken() {
		// given
		ConfigClientProperties properties = new ConfigClientProperties(new MockEnvironment());
		ConfigClientOAuth2Properties oauth2Properties = new ConfigClientOAuth2Properties();
		oauth2Properties.setTokenUri(idpUrl + "/realms/test-realm/protocol/openid-connect/token");
		oauth2Properties.setClientId("clientId");
		oauth2Properties.setClientSecret("clientSecret");
		oauth2Properties.setGrantType("client_credentials");
		properties.setConfigClientOAuth2Properties(oauth2Properties);

		ConfigClientRequestTemplateFactory templateFactory = new ConfigClientRequestTemplateFactory(LOG, properties);
		Optional<AccessTokenResponse> tokenOpt = ReflectionTestUtils.invokeMethod(templateFactory, "parseTokenResponse",
				TOKEN_RESPONSE);
		assertThat(tokenOpt).isPresent();
		AccessTokenResponse tokenResponse = tokenOpt.get();
		mockWebServer.enqueue(new MockResponse().setBody(TOKEN_RESPONSE).setHeader(HttpHeaders.CONTENT_TYPE,
				MediaType.APPLICATION_JSON_VALUE));
		// when
		RestTemplate restTemplate = templateFactory.create();
		List<ClientHttpRequestInterceptor> interceptors = List
				.of(new ConfigClientRequestTemplateFactory.GenericRequestHeaderInterceptor(properties.getHeaders()));
		restTemplate.setInterceptors(interceptors);

		// then
		assertThat(restTemplate).isNotNull();
		assertThat(restTemplate.getInterceptors()).isNotNull();
		assertThat(restTemplate.getInterceptors()).isNotEmpty();
		ConfigClientRequestTemplateFactory.GenericRequestHeaderInterceptor genericInterceptor = (ConfigClientRequestTemplateFactory.GenericRequestHeaderInterceptor) restTemplate
				.getInterceptors().get(0);
		assertThat(genericInterceptor.getHeaders()).isNotNull();
		assertThat(properties.getHeaders()).isNotNull();
		assertThat(properties.getHeaders()).isNotEmpty();
		String header = properties.getHeaders().get(AUTHORIZATION);
		assertThat(header).isEqualTo(tokenResponse.getTokenType() + " " + tokenResponse.getAccessToken());
		assertThat(genericInterceptor.getHeaders().get(AUTHORIZATION))
				.isEqualTo(properties.getHeaders().get(AUTHORIZATION));

	}

	@Test
	void whenCreate_givenNoGrantType_thenIllegalState() {
		// given
		ConfigClientProperties properties = new ConfigClientProperties(new MockEnvironment());
		ConfigClientOAuth2Properties oauth2Properties = new ConfigClientOAuth2Properties();
		oauth2Properties.setTokenUri(idpUrl + "/realms/test-realm/protocol/openid-connect/token");
		oauth2Properties.setClientId("clientId");
		oauth2Properties.setClientSecret("clientSecret");
		properties.setConfigClientOAuth2Properties(oauth2Properties);

		// when
		try {
			ConfigClientRequestTemplateFactory templateFactory = new ConfigClientRequestTemplateFactory(LOG,
					properties);
			templateFactory.create();
		}
		catch (IllegalStateException e) {
			// then
			assertThat(e.getMessage()).startsWith("OAuth2 Grant Type property required.");
		}
	}

	@Test
	void whenCreate_givenBadTokenResponse_thenNoHeaderSet() {
		// given
		ConfigClientProperties properties = new ConfigClientProperties(new MockEnvironment());
		ConfigClientOAuth2Properties oauth2Properties = new ConfigClientOAuth2Properties();
		oauth2Properties.setTokenUri(idpUrl + "/realms/test-realm/protocol/openid-connect/token");
		oauth2Properties.setOauthUsername("oauthUsername");
		oauth2Properties.setOauthPassword("oauthPassword");
		oauth2Properties.setGrantType("password");
		properties.setConfigClientOAuth2Properties(oauth2Properties);

		ConfigClientRequestTemplateFactory templateFactory = new ConfigClientRequestTemplateFactory(LOG, properties);
		mockWebServer.enqueue(new MockResponse().setBody("TOKEN_RESPONSE").setHeader(HttpHeaders.CONTENT_TYPE,
				MediaType.APPLICATION_JSON_VALUE));

		// when
		templateFactory.create();

		// then
		assertThat(properties.getHeaders()).isNotNull();
	}

	@Test
	void whenDecryptProperty_givenEncryptedProp_thenDecryptProp() {
		// given
		ConfigClientProperties properties = new ConfigClientProperties(new MockEnvironment());
		System.setProperty(EncryptorConfig.ENCRYPTOR_SYSTEM_PROPERTY, "YaddaYaddaYadda");
		EncryptorConfig encryptorConfig = new EncryptorConfig();
		encryptorConfig.setEncryptorAlgorithm("PBEWITHHMACSHA512ANDAES_256");
		properties.setEncryptorConfig(encryptorConfig);

		properties.setConfigClientOAuth2Properties(new ConfigClientOAuth2Properties());
		properties.getConfigClientOAuth2Properties().setGrantType("client_credentials");
		properties.getConfigClientOAuth2Properties()
				.setTokenUri(idpUrl + "/realms/test-realm/protocol/openid-connect/token");
		properties.getConfigClientOAuth2Properties().setOauthUsername("oauthUsername");
		properties.getConfigClientOAuth2Properties().setOauthPassword("oauthPassword");

		StringEncryptor encryptor = encryptorConfig.getEncryptor();
		String secret = UUID.randomUUID().toString();
		String encryptedProp = encryptor.encrypt(secret);
		properties.getConfigClientOAuth2Properties().setClientSecret("ENC(" + encryptedProp + ")");
		properties.getConfigClientOAuth2Properties().setOauthPassword("PLAIN OLD TEXT");
		ConfigClientRequestTemplateFactory templateFactory = new ConfigClientRequestTemplateFactory(LOG, properties);
		// when
		String actualSecret = ReflectionTestUtils.invokeMethod(templateFactory, "decryptProperty",
				properties.getConfigClientOAuth2Properties().getClientSecret());

		// then
		assertThat(secret).isEqualTo(actualSecret);
		actualSecret = encryptorConfig.decryptProperty(properties.getConfigClientOAuth2Properties().getOauthPassword());
		assertThat(actualSecret).isEqualTo("PLAIN OLD TEXT");
	}

	@Test
	void whenRefreshJwt_givenExpiredToken_thenRefreshToken() {
		// given
		ConfigClientProperties properties = new ConfigClientProperties(new MockEnvironment());
		ConfigClientOAuth2Properties oauth2Properties = new ConfigClientOAuth2Properties();
		oauth2Properties.setTokenUri(idpUrl + "/realms/test-realm/protocol/openid-connect/token");
		oauth2Properties.setClientId("clientId");
		oauth2Properties.setClientSecret("clientSecret");
		oauth2Properties.setGrantType("client_credentials");
		properties.setConfigClientOAuth2Properties(oauth2Properties);

		ConfigClientRequestTemplateFactory templateFactory = new ConfigClientRequestTemplateFactory(LOG, properties);
		Optional<AccessTokenResponse> tokenOpt = ReflectionTestUtils.invokeMethod(templateFactory, "parseTokenResponse",
				TOKEN_RESPONSE);
		assertThat(tokenOpt).isPresent();
		AccessTokenResponse tokenResponse = tokenOpt.get();
		mockWebServer.enqueue(new MockResponse().setBody(TOKEN_RESPONSE).setHeader(HttpHeaders.CONTENT_TYPE,
				MediaType.APPLICATION_JSON_VALUE));
		// second one for refresh
		mockWebServer.enqueue(new MockResponse().setBody(TOKEN_RESPONSE).setHeader(HttpHeaders.CONTENT_TYPE,
				MediaType.APPLICATION_JSON_VALUE));
		RestTemplate restTemplate = templateFactory.create();
		List<ClientHttpRequestInterceptor> interceptors = List
				.of(new ConfigClientRequestTemplateFactory.GenericRequestHeaderInterceptor(properties.getHeaders()));
		restTemplate.setInterceptors(interceptors);
		// when
		templateFactory.refreshJwt(restTemplate);

		// then
		assertThat(restTemplate).isNotNull();
		assertThat(restTemplate.getInterceptors()).isNotNull();
		assertThat(restTemplate.getInterceptors()).isNotEmpty();
		ConfigClientRequestTemplateFactory.GenericRequestHeaderInterceptor genericInterceptor = (ConfigClientRequestTemplateFactory.GenericRequestHeaderInterceptor) restTemplate
				.getInterceptors().get(0);
		assertThat(genericInterceptor.getHeaders()).isNotNull();
		assertThat(properties.getHeaders()).isNotNull();
		assertThat(properties.getHeaders()).isNotEmpty();
		String header = properties.getHeaders().get(AUTHORIZATION);
		assertThat(header).isEqualTo(tokenResponse.getTokenType() + " " + tokenResponse.getAccessToken());
		assertThat(genericInterceptor.getHeaders().get(AUTHORIZATION))
				.isEqualTo(properties.getHeaders().get(AUTHORIZATION));

	}

	@Test
	void whenJwtExpired_givenNoBearer_thenNotExpired() {
		// given
		ConfigClientProperties properties = new ConfigClientProperties(new MockEnvironment());
		ConfigClientOAuth2Properties oauth2Properties = new ConfigClientOAuth2Properties();
		oauth2Properties.setTokenUri(idpUrl + "/realms/test-realm/protocol/openid-connect/token");
		oauth2Properties.setClientId("clientId");
		oauth2Properties.setClientSecret("clientSecret");
		oauth2Properties.setGrantType("client_credentials");
		properties.setConfigClientOAuth2Properties(oauth2Properties);
		ConfigClientRequestTemplateFactory templateFactory = new ConfigClientRequestTemplateFactory(LOG, properties);

		boolean actualExpired = templateFactory.jwtExpired(UUID.randomUUID().toString());

		assertThat(actualExpired).isFalse();
	}

	@Test
	void whenJwtExpired_givenNoOAuth_thenNotExpired() {
		// given
		ConfigClientProperties properties = new ConfigClientProperties(new MockEnvironment());
		ConfigClientRequestTemplateFactory templateFactory = new ConfigClientRequestTemplateFactory(LOG, properties);
		// when
		boolean actualExpired = templateFactory.jwtExpired(UUID.randomUUID().toString());
		// then
		assertThat(actualExpired).isFalse();
	}

}
