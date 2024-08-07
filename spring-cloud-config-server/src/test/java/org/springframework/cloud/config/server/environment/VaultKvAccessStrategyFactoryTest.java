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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.server.environment.VaultKvAccessStrategyFactory.V1VaultKvAccessStrategy;
import org.springframework.cloud.config.server.environment.VaultKvAccessStrategyFactory.V2VaultKvAccessStrategy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Haroun Pacquee
 */
public class VaultKvAccessStrategyFactoryTest {

	@Test
	public void testGetV1Strategy() {
		VaultKvAccessStrategy vaultKvAccessStrategy = VaultKvAccessStrategyFactory.forVersion(null, "foo", 1, "");
		assertThat(vaultKvAccessStrategy instanceof V1VaultKvAccessStrategy).isTrue();
	}

	@Test
	public void testGetV2Strategy() {
		VaultKvAccessStrategy vaultKvAccessStrategy = VaultKvAccessStrategyFactory.forVersion(null, "foo", 2, "");
		assertThat(vaultKvAccessStrategy instanceof V2VaultKvAccessStrategy).isTrue();
	}

	@Test
	public void testGetUnsupportedStrategy() {
		Assertions.assertThatThrownBy(() -> VaultKvAccessStrategyFactory.forVersion(null, "foo", 0, ""))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
