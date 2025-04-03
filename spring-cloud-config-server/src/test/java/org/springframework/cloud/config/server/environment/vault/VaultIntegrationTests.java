/*
 * Copyright 2018-2025 the original author or authors.
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

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.vault.VaultContainer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.test.TestConfigServerApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.vault.authentication.LifecycleAwareSessionManager;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ryan Baxter
 */
@Testcontainers
public class VaultIntegrationTests {

	private static final String VAULT_TOKEN = "myroot";

	private static final String ROLE_ID = "testroleid";

	private static final String SECRET_ID = "testsecretid";

	private static final int configServerPort = TestSocketUtils.findAvailableTcpPort();

	@Container
	public static VaultContainer<?> vaultContainer = new VaultContainer<>("vault:1.13.3")
			.withVaultToken(VAULT_TOKEN).withClasspathResourceMapping("vault/vault_test_policy.txt",
					"/tmp/vault_test_policy.txt", BindMode.READ_ONLY);

	@BeforeAll
	public static void before() throws IOException, InterruptedException {
		execInVault("vault policy write full_access /tmp/vault_test_policy.txt".split(" "));
		execInVault("vault auth enable approle".split(" "));
		execInVault(("vault write /auth/approle/role/example-app policies=full_access role_id=" + ROLE_ID).split(" "));
		execInVault(("vault write /auth/approle/role/example-app/custom-secret-id secret_id=" + SECRET_ID).split(" "));
		execInVault("vault kv put secret/application foo=bar baz=bam".split(" "));
		execInVault("vault kv put secret/myapp foo=myappsbar".split(" "));
	}

	private static void execInVault(String... command) throws IOException, InterruptedException {
		org.testcontainers.containers.Container.ExecResult execResult = vaultContainer.execInContainer(command);
		assertThat(execResult.getExitCode()).isZero();
		assertThat(execResult.getStderr()).isEmpty();
	}

	@Test
	public void useStatelessLifecycleManager() {
		try (ConfigurableApplicationContext server = SpringApplication.run(
				new Class[] { TestConfigServerApplication.class },

				new String[] { "--spring.config.name=server", "--spring.profiles.active=vault",
						"--server.port=" + configServerPort,
						"--spring.cloud.config.server.vault.port=" + vaultContainer.getFirstMappedPort(),
						"--spring.cloud.config.server.vault.kv-version=2",
						"--logging.level.org.springframework.cloud.config.server.environment=DEBUG",
						"--debug=true" })) {
			RestTemplate rest = new RestTemplateBuilder().build();
			String configServerUrl = "http://localhost:" + configServerPort;
			HttpHeaders headers = new HttpHeaders();
			headers.set("X-Config-Token", VAULT_TOKEN);
			HttpEntity<HttpHeaders> entity = new HttpEntity<>(headers);
			ResponseEntity<Environment> env = rest.exchange(configServerUrl + "/myapp/default",
					org.springframework.http.HttpMethod.GET, entity, Environment.class);
			assertThat(server.getBean(SessionManager.class)).isInstanceOf(StatelessSessionManager.class);
			assertThat(env.getBody().getPropertySources().get(0).getSource().get("foo")).isEqualTo("myappsbar");
		}
	}

	@Test
	public void useLifecycleManagerWithAuthentication() {
		try (ConfigurableApplicationContext server = SpringApplication.run(
				new Class[] { TestConfigServerApplication.class },
				new String[] { "--spring.config.name=server", "--spring.profiles.active=vault",
						"--server.port=" + configServerPort,
						"--spring.cloud.config.server.vault.port=" + vaultContainer.getFirstMappedPort(),
						"--spring.cloud.config.server.vault.kv-version=2",
						"--spring.cloud.config.server.vault.authentication=APPROLE",
						"--spring.cloud.config.server.vault.app-role.role-id=" + ROLE_ID,
						"--spring.cloud.config.server.vault.app-role.secret-id=" + SECRET_ID,
						"--logging.level.org.springframework.cloud.config.server.environment=DEBUG",
						"--debug=true" })) {
			RestTemplate rest = new RestTemplateBuilder().build();
			String configServerUrl = "http://localhost:" + configServerPort;
			HttpHeaders headers = new HttpHeaders();
			headers.set("X-Config-Token", VAULT_TOKEN);
			HttpEntity<HttpHeaders> entity = new HttpEntity<>(headers);
			ResponseEntity<Environment> env = rest.exchange(configServerUrl + "/myapp/default",
					org.springframework.http.HttpMethod.GET, entity, Environment.class);
			assertThat(server.getBean(SessionManager.class)).isInstanceOf(LifecycleAwareSessionManager.class);
			assertThat(env.getBody().getPropertySources().get(0).getSource().get("foo")).isEqualTo("myappsbar");
		}
	}

	@Test
	public void useLifecycleManagerWithToken() {
		try (ConfigurableApplicationContext server = SpringApplication.run(
				new Class[] { TestConfigServerApplication.class },
				new String[] { "--spring.config.name=server", "--spring.profiles.active=vault",
						"--server.port=" + configServerPort,
						"--spring.cloud.config.server.vault.port=" + vaultContainer.getFirstMappedPort(),
						"--spring.cloud.config.server.vault.kv-version=2",
						"--spring.cloud.config.server.vault.token=" + VAULT_TOKEN,
						"--logging.level.org.springframework.cloud.config.server.environment=DEBUG",
						"--debug=true" })) {
			RestTemplate rest = new RestTemplateBuilder().build();
			String configServerUrl = "http://localhost:" + configServerPort;
			HttpHeaders headers = new HttpHeaders();
			headers.set("X-Config-Token", VAULT_TOKEN);
			HttpEntity<HttpHeaders> entity = new HttpEntity<>(headers);
			ResponseEntity<Environment> env = rest.exchange(configServerUrl + "/myapp/default",
					org.springframework.http.HttpMethod.GET, entity, Environment.class);
			assertThat(server.getBean(SessionManager.class)).isInstanceOf(LifecycleAwareSessionManager.class);
			assertThat(env.getBody().getPropertySources().get(0).getSource().get("foo")).isEqualTo("myappsbar");
		}
	}

}
