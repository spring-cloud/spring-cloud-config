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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.cloud.config.server.environment.CredhubEnvironmentProperties;
import org.springframework.cloud.config.server.environment.CredhubEnvironmentRepository;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.credhub.core.CredHubOperations;
import org.springframework.credhub.core.credential.CredHubCredentialOperations;
import org.springframework.credhub.support.CredentialDetails;
import org.springframework.credhub.support.CredentialSummary;
import org.springframework.credhub.support.CredentialType;
import org.springframework.credhub.support.SimpleCredentialName;
import org.springframework.credhub.support.json.JsonCredential;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Compliance tests for {@link CredhubEnvironmentRepository}. There is no CredHub
 * emulator, so credentials are kept in memory behind the CredHub client API. Each file is
 * a JSON credential below {@code /<application>/<profile>/<label>}. CredHub stores
 * configuration without a profile under the {@code default} profile.
 *
 * @author Ryan Baxter
 */
class CredhubEnvironmentRepositoryComplianceTests extends EnvironmentRepositoryComplianceTests {

	private final Fixture fixture = new Fixture();

	@Override
	protected EnvironmentRepositoryFixture fixture() {
		return this.fixture;
	}

	static class Fixture implements EnvironmentRepositoryFixture {

		private static final String CREDENTIAL = "/compliance";

		private final CredhubEnvironmentProperties properties = new CredhubEnvironmentProperties();

		private final Map<String, Map<String, Object>> credentials = new HashMap<>();

		private final CredHubOperations credHub = mock(CredHubOperations.class);

		Fixture() {
			CredHubCredentialOperations operations = mock(CredHubCredentialOperations.class);
			given(this.credHub.credentials()).willReturn(operations);
			given(operations.findByPath(anyString())).willAnswer(invocation -> {
				String path = invocation.getArgument(0);
				return this.credentials.containsKey(path)
						? List.of(new CredentialSummary(new SimpleCredentialName(path + CREDENTIAL))) : List.of();
			});
			given(operations.getByName(any(SimpleCredentialName.class), eq(JsonCredential.class)))
				.willAnswer(invocation -> {
					SimpleCredentialName name = invocation.getArgument(0);
					String path = name.getName().substring(0, name.getName().length() - CREDENTIAL.length());
					return new CredentialDetails<>("id", name, CredentialType.JSON,
							new JsonCredential(this.credentials.get(path)));
				});
		}

		@Override
		public void reset() {
			this.credentials.clear();
		}

		@Override
		public void store(ConfigFile file) {
			String profile = file.profile() != null ? file.profile() : "default";
			String label = file.label() != null ? file.label() : this.properties.getDefaultLabel();
			this.credentials.put("/" + file.application() + "/" + profile + "/" + label, file.properties());
		}

		@Override
		public EnvironmentRepository repository() {
			return new CredhubEnvironmentRepository(this.credHub, this.properties);
		}

		@Override
		public Set<Capability> capabilities() {
			return EnumSet.of(Capability.LABELS, Capability.MULTIPLE_LABELS, Capability.MULTIPLE_APPLICATIONS);
		}

	}

}
