/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.config.server.environment;

import java.util.Optional;

import javax.net.ssl.SSLHandshakeException;
import javax.servlet.http.HttpServletRequest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Dylan Roberts
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
		classes = VaultEnvironmentRepositoryIntegrationTests.TestApplication.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "server.ssl.key-store=classpath:ssl-test.jks",
				"server.ssl.key-store-password=password",
				"server.ssl.key-password=password", "server.key-alias=ssl-test" })
public class VaultEnvironmentRepositoryIntegrationTests {

	@Rule
	public ExpectedException expectedException = ExpectedException.none();

	@LocalServerPort
	private String localServerPort;

	@Test
	public void withSslValidation() throws Exception {
		ObjectProvider<HttpServletRequest> request = withRequest();
		VaultEnvironmentRepositoryFactory vaultEnvironmentRepositoryFactory = new VaultEnvironmentRepositoryFactory(
				request, new EnvironmentWatch.Default(),
				Optional.of(new HttpClientVaultRestTemplateFactory()),
				withTokenProvider(request));
		VaultEnvironmentRepository vaultEnvironmentRepository = vaultEnvironmentRepositoryFactory
				.build(withEnvironmentProperties(false));
		this.expectedException.expectCause(instanceOf(SSLHandshakeException.class));

		vaultEnvironmentRepository.findOne("application", "profile", "label");
	}

	@Test
	public void skipSslValidation() throws Exception {
		ObjectProvider<HttpServletRequest> request = withRequest();
		VaultEnvironmentRepositoryFactory vaultEnvironmentRepositoryFactory = new VaultEnvironmentRepositoryFactory(
				request, new EnvironmentWatch.Default(),
				Optional.of(new HttpClientVaultRestTemplateFactory()),
				withTokenProvider(request));
		VaultEnvironmentRepository vaultEnvironmentRepository = vaultEnvironmentRepositoryFactory
				.build(withEnvironmentProperties(true));

		Environment actual = vaultEnvironmentRepository.findOne("application", "profile",
				"label");

		assertThat(actual).isNotNull();
	}

	private VaultEnvironmentProperties withEnvironmentProperties(
			boolean skipSslValidation) {
		VaultEnvironmentProperties environmentProperties = new VaultEnvironmentProperties();
		environmentProperties.setPort(Integer.decode(this.localServerPort));
		environmentProperties.setScheme("https");
		environmentProperties.setSkipSslValidation(skipSslValidation);
		return environmentProperties;
	}

	private ObjectProvider<HttpServletRequest> withRequest() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getHeader("X-Config-Token")).thenReturn("configToken");
		ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);
		when(requestProvider.getIfAvailable()).thenReturn(request);
		return requestProvider;
	}

	private ConfigTokenProvider withTokenProvider(
			ObjectProvider<HttpServletRequest> request) {
		return new HttpRequestConfigTokenProvider(request);
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	public static class TestApplication {

		public static void main(String[] args) {
			SpringApplication.run(TestApplication.class, args);
		}

	}

}
