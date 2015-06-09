/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.config.server.encryption;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.Map;

import org.junit.Test;

/**
 * @author Dave Syer
 *
 */
public class EnvironmentPrefixHelperTests {

	private EnvironmentPrefixHelper helper = new EnvironmentPrefixHelper();

	@Test
	public void testAddPrefix() {
		assertEquals("{bar:spam}foo",
				this.helper.addPrefix(Collections.singletonMap("bar", "spam"), "foo"));
	}

	@Test
	public void testAddNoPrefix() {
		assertEquals("foo",
				this.helper.addPrefix(Collections.<String, String> emptyMap(), "foo"));
	}

	@Test
	public void testStripNoPrefix() {
		assertEquals("foo", this.helper.stripPrefix("foo"));
	}

	@Test
	public void testStripPrefix() {
		assertEquals("foo", this.helper.stripPrefix("{key:foo}foo"));
	}

	@Test
	public void testStripPrefixWithEscape() {
		assertEquals("{key:foo}foo", this.helper.stripPrefix("{plain}{key:foo}foo"));
	}

	@Test
	public void testKeysDefaults() {
		Map<String, String> keys = this.helper.getEncryptorKeys("foo", "bar", "spam");
		assertEquals("foo", keys.get("name"));
		assertEquals("bar", keys.get("profiles"));
	}

	@Test
	public void testKeysWithPrefix() {
		Map<String, String> keys = this.helper.getEncryptorKeys("foo", "bar",
				"{key:mykey}foo");
		assertEquals(3, keys.size());
		assertEquals("mykey", keys.get("key"));
	}

	@Test
	public void testKeysWithPrefixAndEscape() {
		Map<String, String> keys = this.helper.getEncryptorKeys("foo", "bar",
				"{key:mykey}{plain}{foo:bar}foo");
		assertEquals(3, keys.size());
		assertEquals("mykey", keys.get("key"));
	}

}
