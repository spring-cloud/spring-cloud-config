/*
 * Copyright 2018-present the original author or authors.
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

package org.springframework.cloud.config.server.environment.vault;

import java.util.Collections;
import java.util.List;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.server.environment.ConfigTokenProvider;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties;
import org.springframework.cloud.config.server.environment.vault.authentication.AppRoleClientAuthenticationProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.config.AbstractVaultConfiguration;
import org.springframework.vault.support.VaultToken;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * @author Kaveh Shamsi
 * @author Max Brauer
 */
class SpringVaultTemplateBuilderTest {

	private WireMockServer mockVaultServer;

	@BeforeEach
	void setUp() {
		mockVaultServer = new WireMockServer();
		mockVaultServer.start();
		WireMock.configureFor("http", "localhost", mockVaultServer.port());
	}

	@AfterEach
	void tearDown() {
		mockVaultServer.stop();
	}

	@Test
	void shouldUseDefaultToken() {
		ConfigTokenProvider defaultTokenProvider = () -> "default-token";

		var springVaultTemplateBuilder = new SpringVaultTemplateBuilder(defaultTokenProvider, Collections.emptyList(),
				givenApplicationContext(defaultTokenProvider));

		var vaultProperties = new VaultEnvironmentProperties();
		vaultProperties.setPort(mockVaultServer.port());

		springVaultTemplateBuilder.build(vaultProperties).read("/secrets/test");

		verify(1,
				getRequestedFor(urlEqualTo("/v1/secrets/test")).withHeader("X-Vault-Token", equalTo("default-token")));
	}

	@Test
	void shouldUseStaticToken() {
		ConfigTokenProvider defaultTokenProvider = () -> "default-token";

		var springVaultTemplateBuilder = new SpringVaultTemplateBuilder(defaultTokenProvider, Collections.emptyList(),
				givenApplicationContext(defaultTokenProvider));

		var vaultProperties = new VaultEnvironmentProperties();
		vaultProperties.setPort(mockVaultServer.port());
		vaultProperties.setToken("config-token");

		springVaultTemplateBuilder.build(vaultProperties).read("/secrets/test");

		verify(1, getRequestedFor(urlEqualTo("/v1/secrets/test")).withHeader("X-Vault-Token", equalTo("config-token")));
	}

	@Test
	void shouldUseAppRoleToken() {
		ConfigTokenProvider defaultTokenProvider = () -> "default-token";

		mockVaultServer.stubFor(post("/v1/auth/approle/login").willReturn(aResponse().withStatus(200).withBody("""
					{"auth": {"client_token": "approle-token"}}
				""").withHeader("Content-Type", "application/json")));

		var springVaultTemplateBuilder = new SpringVaultTemplateBuilder(defaultTokenProvider,
				List.of(new AppRoleClientAuthenticationProvider()), givenApplicationContext(defaultTokenProvider));

		var vaultProperties = new VaultEnvironmentProperties();
		vaultProperties.setPort(mockVaultServer.port());
		vaultProperties.setAuthentication(VaultEnvironmentProperties.AuthenticationMethod.APPROLE);

		var appRole = vaultProperties.getAppRole();
		appRole.setSecretId("secret-id");
		appRole.setRoleId("role-id");

		springVaultTemplateBuilder.build(vaultProperties).read("/secrets/test");

		verify(1,
				getRequestedFor(urlEqualTo("/v1/secrets/test")).withHeader("X-Vault-Token", equalTo("approle-token")));
		verify(1, postRequestedFor(urlEqualTo("/v1/auth/approle/login")).withRequestBody(equalToJson("""
					{"role_id": "role-id", "secret_id": "secret-id"}
				""")));
	}

	@Test
	void buildShouldUseStaticTokenWhenAuthenticationIsToken() {
		VaultEnvironmentProperties properties = new VaultEnvironmentProperties();
		properties.setToken("my-static-token");
		properties.setAuthentication(VaultEnvironmentProperties.AuthenticationMethod.TOKEN);

		ConfigTokenProvider defaultTokenProvider = mock(ConfigTokenProvider.class);
		ApplicationContext mockContext = mock(ApplicationContext.class);

		SpringVaultTemplateBuilder builder = new SpringVaultTemplateBuilder(
			defaultTokenProvider,
			Collections.emptyList(),
			mockContext
		);

		assertThrows(Exception.class, () -> builder.build(properties));
		verifyNoInteractions(defaultTokenProvider);
	}

	private static StaticApplicationContext givenApplicationContext(ConfigTokenProvider defaultTokenProvider) {
		var context = new StaticApplicationContext();
		context.getBeanFactory()
			.registerSingleton("sessionManager", (SessionManager) () -> VaultToken.of(defaultTokenProvider.getToken()));
		context.getBeanFactory()
			.registerSingleton("vaultThreadPoolTaskScheduler",
					new AbstractVaultConfiguration.TaskSchedulerWrapper(new ThreadPoolTaskScheduler()));
		context.getBeanFactory()
			.registerSingleton("clientHttpRequestFactoryWrapper",
					new AbstractVaultConfiguration.ClientFactoryWrapper(new SimpleClientHttpRequestFactory()));
		return context;
	}

}
