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

package org.springframework.cloud.config.environment;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnvironmentTests {

	@Test
	public void normalizeWorks() {
		assertThat(Environment.normalize("abc123")).isEqualTo("abc123");
		assertThat(Environment.normalize("abc(_)123")).isEqualTo("abc/123");
		assertThat(Environment.normalize("abc(%5F)123")).isEqualTo("abc(%5F)123");
		assertThat(Environment.normalize("abc%28_%29123")).isEqualTo("abc%28_%29123");
		assertThat(Environment.normalize("abc%28%5F%29123")).isEqualTo("abc%28%5F%29123");
	}

	@Test
	public void denormalizeWorks() {
		assertThat(Environment.denormalize("abc123")).isEqualTo("abc123");
		assertThat(Environment.denormalize("abc/123")).isEqualTo("abc(_)123");
		assertThat(Environment.denormalize("abc%2F123")).isEqualTo("abc%2F123");
		assertThat(Environment.denormalize("abc%25F123")).isEqualTo("abc%25F123");
	}

}
