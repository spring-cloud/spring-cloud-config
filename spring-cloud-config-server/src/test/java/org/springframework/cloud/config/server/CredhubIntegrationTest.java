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

package org.springframework.cloud.config.server;

import org.junit.Before;
import org.mockito.Mockito;

import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.credhub.core.CredHubOperations;
import org.springframework.credhub.core.credential.CredHubCredentialOperations;
import org.springframework.credhub.support.CredentialDetails;
import org.springframework.credhub.support.CredentialSummary;
import org.springframework.credhub.support.CredentialType;
import org.springframework.credhub.support.SimpleCredentialName;
import org.springframework.credhub.support.json.JsonCredential;

import static java.util.Collections.singletonList;
import static org.mockito.Mockito.when;

/**
 * @author Alberto C. Ríos
 */
public class CredhubIntegrationTest {

	@MockBean
	private CredHubOperations credHubOperations;

	@Before
	public void setUp() {
		CredHubCredentialOperations credhubCredentialOperations = Mockito
				.mock(CredHubCredentialOperations.class);

		String expectedPath = "/myapp/master/default";
		SimpleCredentialName togglesCredentialName = new SimpleCredentialName(
				expectedPath + "/toggles");
		when(credhubCredentialOperations.findByPath(expectedPath))
				.thenReturn(singletonList(new CredentialSummary(togglesCredentialName)));
		JsonCredential credentials = new JsonCredential();
		credentials.put("key", "value");
		when(credhubCredentialOperations.getByName(
				new SimpleCredentialName(expectedPath + "/toggles"),
				JsonCredential.class))
						.thenReturn(new CredentialDetails<>("id1", togglesCredentialName,
								CredentialType.JSON, credentials));

		when(this.credHubOperations.credentials())
				.thenReturn(credhubCredentialOperations);
	}

}
