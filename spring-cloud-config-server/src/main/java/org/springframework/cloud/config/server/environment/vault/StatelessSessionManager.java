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

import java.util.concurrent.locks.ReentrantLock;

import org.springframework.util.Assert;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.support.VaultToken;

/**
 * @author Ryan Baxter
 */
public class StatelessSessionManager implements SessionManager {

	private final ClientAuthentication clientAuthentication;

	private final ReentrantLock lock = new ReentrantLock();

	public StatelessSessionManager(ClientAuthentication clientAuthentication) {
		Assert.notNull(clientAuthentication, "ClientAuthentication must not be null");
		this.clientAuthentication = clientAuthentication;
	}

	public VaultToken getSessionToken() {
		this.lock.lock();
		try {
			return this.clientAuthentication.login();
		}
		finally {
			this.lock.unlock();
		}
	}

}
