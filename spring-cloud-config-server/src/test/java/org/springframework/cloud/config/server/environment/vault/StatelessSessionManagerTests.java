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

import org.junit.jupiter.api.Test;

import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.support.VaultToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Ryan Baxter
 */
class StatelessSessionManagerTests {

	@Test
	public void verifyClientAuthenticationIsCalledEveryTime() {
		ClientAuthentication clientAuthentication = mock(ClientAuthentication.class);
		when(clientAuthentication.login()).thenReturn(VaultToken.of("mytoken"), VaultToken.of("anothertoken"));
		StatelessSessionManager sessionManager = new StatelessSessionManager(clientAuthentication);
		assertThat(sessionManager.getSessionToken().getToken()).isEqualTo("mytoken");
		assertThat(sessionManager.getSessionToken().getToken()).isEqualTo("anothertoken");
		verify(clientAuthentication, times(2)).login();
	}

}
