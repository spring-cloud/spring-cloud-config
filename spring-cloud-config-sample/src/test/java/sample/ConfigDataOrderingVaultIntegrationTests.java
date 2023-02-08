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

package sample;

import java.io.IOException;

import org.json.JSONException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.vault.VaultContainer;

import org.springframework.boot.SpringApplication;
import org.springframework.cloud.config.server.test.TestConfigServerApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.TestSocketUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for https://github.com/spring-cloud/spring-cloud-config/issues/1997
 * The error only occurs if a profile specific config imports is used, otherwise
 * reordering does not take place. A profile specific config import is defined in
 * vaultordering/client-dev.yml
 */
@Testcontainers
public class ConfigDataOrderingVaultIntegrationTests {

	private static final int configServerPort = TestSocketUtils.findAvailableTcpPort();

	private static final int configClientPort = TestSocketUtils.findAvailableTcpPort();

	private static ConfigurableApplicationContext client;

	private static ConfigurableApplicationContext server;

	@Container
	public static VaultContainer vaultContainer = new VaultContainer<>(DockerImageName.parse("vault"))
			.withVaultToken("my-root-token").withClasspathResourceMapping("vaultordering/vault_test_policy.txt",
					"/tmp/vault_test_policy.txt", BindMode.READ_ONLY);

	@BeforeAll
	public static void startConfigServer() throws IOException, InterruptedException, JSONException {
		server = SpringApplication.run(TestConfigServerApplication.class,
				"--spring.config.location=classpath:/vaultordering/", "--spring.config.name=server",
				"--server.port=" + configServerPort,
				"--spring.cloud.config.server.vault.port=" + vaultContainer.getFirstMappedPort());

		execInVault("vault", "kv", "put", "secret/client-app,dev", "my.prop=vaultdev");
		execInVault("vault", "kv", "put", "secret/client-app", "my.prop=vault");

	}

	@AfterAll
	public static void close() {
		if (server != null) {
			server.close();
		}
		if (server != null) {
			client.close();
		}
	}

	@Test
	void propertyFromVaultIsUsed() {
		client = SpringApplication.run(TestConfigServerApplication.class, "--server.port=" + configClientPort,
				"--spring.config.location=classpath:/vaultordering/", "--spring.config.name=client",
				"--spring.profiles.active=dev", "--spring.application.name=client-app",
				"--spring.cloud.config.enabled=true", "--spring.cloud.config.server.enabled=false",
				"--config.server.port=" + configServerPort);

		assertThat(client.getEnvironment().getProperty("my.prop")).isEqualTo("vaultdev");

	}

	private static String execInVault(String... command) throws IOException, InterruptedException {
		org.testcontainers.containers.Container.ExecResult execResult = vaultContainer.execInContainer(command);
		assertThat(execResult.getExitCode()).isZero();
		assertThat(execResult.getStderr()).isEmpty();
		return execResult.getStdout();
	}

}
