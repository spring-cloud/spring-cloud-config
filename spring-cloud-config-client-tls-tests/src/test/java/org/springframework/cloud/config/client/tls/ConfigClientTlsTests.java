/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.config.client.tls;

import java.io.File;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.config.server.EnableConfigServer;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigClientTlsTests extends AbstractTlsSetup {

	protected static TlsConfigServerRunner server;

	@BeforeClass
	public static void setupAll() throws Exception {
		startConfigServer();
	}

	@AfterClass
	public static void tearDownAll() {
		stopConfigServer();
	}

	private static void startConfigServer() {
		server = new TlsConfigServerRunner(TestConfigServer.class);
		server.enableTls();
		server.setKeyStore(serverCert, KEY_STORE_PASSWORD, "server", KEY_PASSWORD);
		server.setTrustStore(caCert, KEY_STORE_PASSWORD);
		server.property("logging.level.org.springframework.cloud.config.server", "TRACE");

		server.start();
	}

	private static void stopConfigServer() {
		server.stop();
	}

	@Test
	public void clientCertCanWork() {
		try (TlsConfigClientRunner client = createConfigClient()) {
			enableTlsClient(client);
			client.property("logging.level.org.springframework.boot.context.config", "TRACE");
			client.property("logging.level.org.springframework.cloud.config.client", "DEBUG");
			client.start();
			assertThat(client.getProperty("dumb.key")).isEqualTo("dumb-value");
		}
	}

	@Test
	public void tlsClientCanBeDisabled() {
		try (TlsConfigClientRunner client = createConfigClient()) {
			enableTlsClient(client);
			client.property("spring.cloud.config.tls.enabled", "false");
			client.start();
			assertThat(client.getProperty("dumb.key")).isNull();
		}
	}

	@Test
	public void noCertCannotWork() {
		try (TlsConfigClientRunner client = createConfigClient()) {
			client.disableTls();
			client.start();
			assertThat(client.getProperty("dumb.key")).isNull();
		}
	}

	@Test
	public void wrongCertCannotWork() {
		try (TlsConfigClientRunner client = createConfigClient()) {
			enableTlsClient(client);
			client.setKeyStore(wrongClientCert);
			client.start();
			assertThat(client.getProperty("dumb.key")).isNull();
		}
	}

	@Test(expected = IllegalStateException.class)
	public void wrongPasswordCauseFailure() {
		TlsConfigClientRunner client = createConfigClient(false);
		enableTlsClient(client);
		client.setKeyStore(clientCert, WRONG_PASSWORD, WRONG_PASSWORD);
		client.start();
	}

	@Test(expected = IllegalStateException.class)
	public void nonExistKeyStoreCauseFailure() {
		TlsConfigClientRunner client = createConfigClient(false);
		enableTlsClient(client);
		client.setKeyStore(new File("nonExistFile"));
		client.start();
	}

	@Test
	public void wrongTrustStoreCannotWork() {
		try (TlsConfigClientRunner client = createConfigClient()) {
			enableTlsClient(client);
			client.setTrustStore(wrongCaCert);
			client.start();
			assertThat(client.getProperty("dumb.key")).isNull();
		}
	}

	protected TlsConfigClientRunner createConfigClient(boolean optional) {
		TlsConfigClientRunner runner = createConfigClient();
		if (!optional) {
			runner.property("spring.cloud.config.fail-fast", "true");
		}
		return runner;
	}

	protected TlsConfigClientRunner createConfigClient() {
		return new TlsConfigClientRunner(TestApp.class, server);
	}

	private void enableTlsClient(TlsConfigClientRunner runner) {
		runner.enableTls();
		runner.setKeyStore(clientCert, KEY_STORE_PASSWORD, KEY_PASSWORD);
		runner.setTrustStore(caCert, KEY_STORE_PASSWORD);
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	public static class TestApp {

	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@EnableConfigServer
	public static class TestConfigServer {

	}

}
