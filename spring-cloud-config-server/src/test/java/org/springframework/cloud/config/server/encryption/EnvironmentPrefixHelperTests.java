/*
 * Copyright 2015-2019 the original author or authors.
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

package org.springframework.cloud.config.server.encryption;

import java.util.Collections;
import java.util.Map;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class EnvironmentPrefixHelperTests {

	private EnvironmentPrefixHelper helper = new EnvironmentPrefixHelper();

	@Test
	public void testAddPrefix() {
		assertThat(this.helper.addPrefix(Collections.singletonMap("bar", "spam"), "foo"))
				.isEqualTo("{bar:spam}foo");
	}

	@Test
	public void testAddNoPrefix() {
		assertThat(this.helper.addPrefix(Collections.<String, String>emptyMap(), "foo"))
				.isEqualTo("foo");
	}

	@Test
	public void testStripNoPrefix() {
		assertThat(this.helper.stripPrefix("foo")).isEqualTo("foo");
	}

	@Test
	public void testStripPrefix() {
		assertThat(this.helper.stripPrefix("{key:foo}foo")).isEqualTo("foo");
	}

	@Test
	public void testStripPrefixWithEscape() {
		assertThat(this.helper.stripPrefix("{plain}{key:foo}foo"))
				.isEqualTo("{key:foo}foo");
	}

	@Test
	public void testKeysDefaults() {
		Map<String, String> keys = this.helper.getEncryptorKeys("foo", "bar", "spam");
		assertThat(keys.get("name")).isEqualTo("foo");
		assertThat(keys.get("profiles")).isEqualTo("bar");
	}

	@Test
	public void testKeysWithPrefix() {
		Map<String, String> keys = this.helper.getEncryptorKeys("foo", "bar",
				"{key:mykey}foo");
		assertThat(keys.size()).isEqualTo(3);
		assertThat(keys.get("key")).isEqualTo("mykey");
	}

	@Test
	public void testKeysWithPrefixAndEscape() {
		Map<String, String> keys = this.helper.getEncryptorKeys("foo", "bar",
				"{key:mykey}{plain}{foo:bar}foo");
		assertThat(keys.size()).isEqualTo(3);
		assertThat(keys.get("key")).isEqualTo("mykey");
	}

	@Test
	public void testTextWithCurlyBracesNoPrefix() {
		assertThat(this.helper.stripPrefix("textwith}brac{es"))
				.isEqualTo("textwith}brac{es");
	}

	@Test
	public void testTextWithCurlyBracesPrefix() {
		assertThat(
				this.helper.stripPrefix("{key:foo}{name:bar}textwith}brac{es{and}prefix"))
						.isEqualTo("textwith}brac{es{and}prefix");
	}

}
