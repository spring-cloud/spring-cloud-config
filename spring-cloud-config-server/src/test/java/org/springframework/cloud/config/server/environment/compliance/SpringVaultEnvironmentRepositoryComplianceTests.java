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

package org.springframework.cloud.config.server.environment.compliance;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.vault.VaultContainer;

import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.environment.EnvironmentWatch;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties;
import org.springframework.cloud.config.server.environment.vault.SpringVaultEnvironmentRepository;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.core.VaultTemplate;

/**
 * Compliance tests for {@link SpringVaultEnvironmentRepository} using a KV version 2
 * backend. Each file is a secret named after the application and profile. Vault stores
 * configuration without a profile under the {@code default} profile.
 *
 * @author Ryan Baxter
 */
@Testcontainers
@Tag("DockerRequired")
class SpringVaultEnvironmentRepositoryComplianceTests extends EnvironmentRepositoryComplianceTests {

	private static final String TOKEN = "compliance";

	@Container
	static VaultContainer<?> vaultContainer = new VaultContainer<>("hashicorp/vault:1.13.3").withVaultToken(TOKEN);

	private static VaultKeyValueOperations keyValue;

	private final Fixture fixture = new Fixture();

	@BeforeAll
	static void connect() {
		VaultEndpoint endpoint = VaultEndpoint.create(vaultContainer.getHost(), vaultContainer.getFirstMappedPort());
		endpoint.setScheme("http");
		keyValue = new VaultTemplate(endpoint, new TokenAuthentication(TOKEN)).opsForKeyValue("secret",
				KeyValueBackend.KV_2);
	}

	@Override
	protected EnvironmentRepositoryFixture fixture() {
		return this.fixture;
	}

	static class Fixture implements EnvironmentRepositoryFixture {

		private final VaultEnvironmentProperties properties = new VaultEnvironmentProperties();

		Fixture() {
			this.properties.setKvVersion(2);
		}

		@Override
		public void reset() {
			List<String> keys = keyValue.list("");
			if (keys != null) {
				keys.forEach(keyValue::delete);
			}
		}

		@Override
		public void store(ConfigFile file) {
			String key = file.profile() == null ? file.application()
					: file.application() + this.properties.getProfileSeparator() + file.profile();
			keyValue.put(key, file.properties());
		}

		@Override
		public EnvironmentRepository repository() {
			return new SpringVaultEnvironmentRepository(
					new StaticListableBeanFactory().getBeanProvider(HttpServletRequest.class),
					new EnvironmentWatch.Default(), this.properties, keyValue);
		}

		@Override
		public Set<Capability> capabilities() {
			return EnumSet.of(Capability.MULTIPLE_APPLICATIONS);
		}

	}

}
