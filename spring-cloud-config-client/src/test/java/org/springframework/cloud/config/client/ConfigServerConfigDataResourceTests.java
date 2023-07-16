/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.config.client;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.config.Profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Ryan Baxter
 */
class ConfigServerConfigDataResourceTests {

	@Test
	void testEquals() {
		ConfigClientProperties r1Properties = new ConfigClientProperties();
		r1Properties.setUri(new String[] { "http://localhost:8888" });
		ConfigClientProperties r2Properties = new ConfigClientProperties();
		r1Properties.setUri(new String[] { "http://localhost:8888" });
		ConfigServerConfigDataResource r1 = new ConfigServerConfigDataResource(r1Properties, true,
				mock(Profiles.class));
		ConfigServerConfigDataResource r2 = new ConfigServerConfigDataResource(r2Properties, true,
				mock(Profiles.class));
		assertThat(r1).isEqualTo(r2);
		assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
	}

	@Test
	void testEqualsDifferentSchemes() {
		ConfigClientProperties r1Properties = new ConfigClientProperties();
		r1Properties.setUri(new String[] { "https://localhost:8888" });
		ConfigClientProperties r2Properties = new ConfigClientProperties();
		r2Properties.setUri(new String[] { "http://localhost:8888" });
		ConfigServerConfigDataResource r1 = new ConfigServerConfigDataResource(r1Properties, true,
				mock(Profiles.class));
		ConfigServerConfigDataResource r2 = new ConfigServerConfigDataResource(r2Properties, true,
				mock(Profiles.class));
		assertThat(r1).isEqualTo(r2);
		assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
	}

	@Test
	void testEqualsDifferentHosts() {
		ConfigClientProperties r1Properties = new ConfigClientProperties();
		r1Properties.setUri(new String[] { "http://localhost:8888" });
		ConfigClientProperties r2Properties = new ConfigClientProperties();
		r2Properties.setUri(new String[] { "http://remotehost:8888" });
		ConfigServerConfigDataResource r1 = new ConfigServerConfigDataResource(r1Properties, true,
				mock(Profiles.class));
		ConfigServerConfigDataResource r2 = new ConfigServerConfigDataResource(r2Properties, true,
				mock(Profiles.class));
		assertThat(r1).isNotEqualTo(r2);
		assertThat(r1.hashCode()).isNotEqualTo(r2.hashCode());
	}

	@Test
	void testEqualsDifferentPorts() {
		ConfigClientProperties r1Properties = new ConfigClientProperties();
		r1Properties.setUri(new String[] { "http://localhost:8888" });
		ConfigClientProperties r2Properties = new ConfigClientProperties();
		r2Properties.setUri(new String[] { "http://localhost" });
		ConfigServerConfigDataResource r1 = new ConfigServerConfigDataResource(r1Properties, true,
				mock(Profiles.class));
		ConfigServerConfigDataResource r2 = new ConfigServerConfigDataResource(r2Properties, true,
				mock(Profiles.class));
		assertThat(r1).isNotEqualTo(r2);
		assertThat(r1.hashCode()).isNotEqualTo(r2.hashCode());
	}

	@Test
	void testEqualsOptionalFalse() {
		ConfigClientProperties r1Properties = new ConfigClientProperties();
		r1Properties.setUri(new String[] { "http://localhost:8888" });
		ConfigClientProperties r2Properties = new ConfigClientProperties();
		r2Properties.setUri(new String[] { "http://localhost:8888" });
		ConfigServerConfigDataResource r1 = new ConfigServerConfigDataResource(r1Properties, false,
				mock(Profiles.class));
		ConfigServerConfigDataResource r2 = new ConfigServerConfigDataResource(r2Properties, false,
				mock(Profiles.class));
		assertThat(r1).isEqualTo(r2);
		assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
	}

	@Test
	void testEqualsSamePath() {
		ConfigClientProperties r1Properties = new ConfigClientProperties();
		r1Properties.setUri(new String[] { "http://localhost:8888/p1" });
		ConfigClientProperties r2Properties = new ConfigClientProperties();
		r2Properties.setUri(new String[] { "http://localhost:8888/p1" });
		ConfigServerConfigDataResource r1 = new ConfigServerConfigDataResource(r1Properties, true,
				mock(Profiles.class));
		ConfigServerConfigDataResource r2 = new ConfigServerConfigDataResource(r2Properties, true,
				mock(Profiles.class));
		assertThat(r1).isEqualTo(r2);
		assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
	}

	@Test
	void testEqualsDifferentPath() {
		ConfigClientProperties r1Properties = new ConfigClientProperties();
		r1Properties.setUri(new String[] { "http://localhost:8888/p1" });
		ConfigClientProperties r2Properties = new ConfigClientProperties();
		r2Properties.setUri(new String[] { "http://localhost:8888/p2" });
		ConfigServerConfigDataResource r1 = new ConfigServerConfigDataResource(r1Properties, true,
				mock(Profiles.class));
		ConfigServerConfigDataResource r2 = new ConfigServerConfigDataResource(r2Properties, true,
				mock(Profiles.class));
		assertThat(r1).isNotEqualTo(r2);
		assertThat(r1.hashCode()).isNotEqualTo(r2.hashCode());
	}

	@Test
	void testMultipleUrisDifferentOrder() {
		ConfigClientProperties r1Properties = new ConfigClientProperties();
		r1Properties.setUri(new String[] { "http://localhost:8888", "http://localhost:9999", "http://localhost:7777" });
		ConfigClientProperties r2Properties = new ConfigClientProperties();
		r2Properties.setUri(new String[] { "http://localhost:7777", "http://localhost:8888", "http://localhost:9999" });
		ConfigServerConfigDataResource r1 = new ConfigServerConfigDataResource(r1Properties, true,
				mock(Profiles.class));
		ConfigServerConfigDataResource r2 = new ConfigServerConfigDataResource(r2Properties, true,
				mock(Profiles.class));
		assertThat(r1).isEqualTo(r2);
		assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
	}

	@Test
	void testMultipleUrisDifferentOrderWithDifferentSchemes() {
		ConfigClientProperties r1Properties = new ConfigClientProperties();
		r1Properties.setUri(new String[] { "http://localhost:8888", "http://localhost:9999", "http://localhost:7777" });
		ConfigClientProperties r2Properties = new ConfigClientProperties();
		r2Properties
				.setUri(new String[] { "https://localhost:7777", "https://localhost:8888", "https://localhost:9999" });
		ConfigServerConfigDataResource r1 = new ConfigServerConfigDataResource(r1Properties, true,
				mock(Profiles.class));
		ConfigServerConfigDataResource r2 = new ConfigServerConfigDataResource(r2Properties, true,
				mock(Profiles.class));
		assertThat(r1).isEqualTo(r2);
		assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
	}

	@Test
	void testMultipleUrisDifferentLengths() {
		ConfigClientProperties r1Properties = new ConfigClientProperties();
		r1Properties.setUri(new String[] { "http://localhost:8888", "http://localhost:9999", "http://localhost:7777" });
		ConfigClientProperties r2Properties = new ConfigClientProperties();
		r2Properties.setUri(new String[] { "http://localhost:7777", "http://localhost:8888", "http://localhost:9999",
				"http://localhost:6666" });
		ConfigServerConfigDataResource r1 = new ConfigServerConfigDataResource(r1Properties, true,
				mock(Profiles.class));
		ConfigServerConfigDataResource r2 = new ConfigServerConfigDataResource(r2Properties, true,
				mock(Profiles.class));
		assertThat(r1).isNotEqualTo(r2);
		assertThat(r1.hashCode()).isNotEqualTo(r2.hashCode());
	}

	@Test
	void testEqualsOptionalFalseAndTrue() {
		ConfigClientProperties r1Properties = new ConfigClientProperties();
		r1Properties.setUri(new String[] { "http://localhost:8888" });
		ConfigClientProperties r2Properties = new ConfigClientProperties();
		r2Properties.setUri(new String[] { "http://localhost:8888" });
		ConfigServerConfigDataResource r1 = new ConfigServerConfigDataResource(r1Properties, true,
				mock(Profiles.class));
		ConfigServerConfigDataResource r2 = new ConfigServerConfigDataResource(r2Properties, false,
				mock(Profiles.class));
		assertThat(r1).isNotEqualTo(r2);
		assertThat(r1.hashCode()).isNotEqualTo(r2.hashCode());
	}

	@Test
	void testEqualsNullProfilesAndDefaultProfile() {
		ConfigClientProperties r1Properties = new ConfigClientProperties();
		r1Properties.setUri(new String[] { "http://localhost:8888" });
		ConfigClientProperties r2Properties = new ConfigClientProperties();
		r2Properties.setUri(new String[] { "http://localhost:8888" });
		ConfigServerConfigDataResource r1 = new ConfigServerConfigDataResource(r1Properties, true, null);
		ConfigServerConfigDataResource r2 = new ConfigServerConfigDataResource(r2Properties, true,
				mock(Profiles.class));
		assertThat(r1).isEqualTo(r2);
		assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
	}

	@Test
	void testEqualsNullProfiles() {
		ConfigClientProperties r1Properties = new ConfigClientProperties();
		r1Properties.setUri(new String[] { "http://localhost:8888" });
		ConfigClientProperties r2Properties = new ConfigClientProperties();
		r2Properties.setUri(new String[] { "http://localhost:8888" });
		ConfigServerConfigDataResource r1 = new ConfigServerConfigDataResource(r1Properties, true, null);
		ConfigServerConfigDataResource r2 = new ConfigServerConfigDataResource(r2Properties, true, null);
		assertThat(r1).isEqualTo(r2);
		assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
	}

	@Test
	void testEqualsProfilesAndProperties() {
		ConfigClientProperties r1Properties = new ConfigClientProperties();
		r1Properties.setUri(new String[] { "http://localhost:8888" });
		Profiles r1Profiles = mock(Profiles.class);
		when(r1Profiles.getAccepted()).thenReturn(Collections.singletonList("foo"));
		ConfigClientProperties r2Properties = new ConfigClientProperties();
		r2Properties.setUri(new String[] { "http://localhost:8888" });
		r2Properties.setProfile("foo");
		ConfigServerConfigDataResource r1 = new ConfigServerConfigDataResource(r1Properties, true, r1Profiles);
		ConfigServerConfigDataResource r2 = new ConfigServerConfigDataResource(r2Properties, true, null);
		assertThat(r1).isEqualTo(r2);
		assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
	}

	@Test
	void testEqualsProfiles() {
		ConfigClientProperties r1Properties = new ConfigClientProperties();
		r1Properties.setUri(new String[] { "http://localhost:8888" });
		Profiles r1Profiles = mock(Profiles.class);
		when(r1Profiles.getAccepted()).thenReturn(Collections.singletonList("foo"));
		ConfigClientProperties r2Properties = new ConfigClientProperties();
		r2Properties.setUri(new String[] { "http://localhost:8888" });
		Profiles r2Profiles = mock(Profiles.class);
		when(r2Profiles.getAccepted()).thenReturn(Collections.singletonList("foo"));
		ConfigServerConfigDataResource r1 = new ConfigServerConfigDataResource(r1Properties, true, r1Profiles);
		ConfigServerConfigDataResource r2 = new ConfigServerConfigDataResource(r2Properties, true, r2Profiles);
		assertThat(r1).isEqualTo(r2);
		assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
	}

	@Test
	void testEqualsProfilesDifferent() {
		ConfigClientProperties r1Properties = new ConfigClientProperties();
		r1Properties.setUri(new String[] { "http://localhost:8888" });
		Profiles r1Profiles = mock(Profiles.class);
		when(r1Profiles.getAccepted()).thenReturn(Arrays.asList("foo", "bar"));
		ConfigClientProperties r2Properties = new ConfigClientProperties();
		r2Properties.setUri(new String[] { "http://localhost:8888" });
		Profiles r2Profiles = mock(Profiles.class);
		when(r2Profiles.getAccepted()).thenReturn(Collections.singletonList("foo"));
		ConfigServerConfigDataResource r1 = new ConfigServerConfigDataResource(r1Properties, true, r1Profiles);
		ConfigServerConfigDataResource r2 = new ConfigServerConfigDataResource(r2Properties, true, r2Profiles);
		assertThat(r1).isNotEqualTo(r2);
		assertThat(r1.hashCode()).isNotEqualTo(r2.hashCode());
	}

	@Test
	void testEqualsProfilesDifferentOrder() {
		ConfigClientProperties r1Properties = new ConfigClientProperties();
		r1Properties.setUri(new String[] { "http://localhost:8888" });
		Profiles r1Profiles = mock(Profiles.class);
		when(r1Profiles.getAccepted()).thenReturn(Arrays.asList("foo", "bar"));
		ConfigClientProperties r2Properties = new ConfigClientProperties();
		r2Properties.setUri(new String[] { "http://localhost:8888" });
		Profiles r2Profiles = mock(Profiles.class);
		when(r2Profiles.getAccepted()).thenReturn(Arrays.asList("bar", "foo"));
		ConfigServerConfigDataResource r1 = new ConfigServerConfigDataResource(r1Properties, true, r1Profiles);
		ConfigServerConfigDataResource r2 = new ConfigServerConfigDataResource(r2Properties, true, r2Profiles);
		assertThat(r1).isNotEqualTo(r2);
		assertThat(r1.hashCode()).isNotEqualTo(r2.hashCode());
	}

	@Test
	void testEqualsProfilesProperties() {
		ConfigClientProperties r1Properties = new ConfigClientProperties();
		r1Properties.setUri(new String[] { "http://localhost:8888" });
		Profiles r1Profiles = mock(Profiles.class);
		when(r1Profiles.getAccepted()).thenReturn(Arrays.asList("foo", "bar"));
		ConfigClientProperties r2Properties = new ConfigClientProperties();
		r2Properties.setUri(new String[] { "http://localhost:8888" });
		r2Properties.setProfile("foo,bar");
		ConfigServerConfigDataResource r1 = new ConfigServerConfigDataResource(r1Properties, true, r1Profiles);
		ConfigServerConfigDataResource r2 = new ConfigServerConfigDataResource(r2Properties, true, null);
		assertThat(r1).isEqualTo(r2);
		assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
	}

	@Test
	void testEqualsLabels() {
		ConfigClientProperties r1Properties = new ConfigClientProperties();
		r1Properties.setUri(new String[] { "http://localhost:8888" });
		r1Properties.setLabel("foo");
		ConfigClientProperties r2Properties = new ConfigClientProperties();
		r2Properties.setUri(new String[] { "http://localhost:8888" });
		r2Properties.setLabel("foo");
		ConfigServerConfigDataResource r1 = new ConfigServerConfigDataResource(r1Properties, true, null);
		ConfigServerConfigDataResource r2 = new ConfigServerConfigDataResource(r2Properties, true, null);
		assertThat(r1).isEqualTo(r2);
		assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
	}

	@Test
	void testEqualsDifferentLabels() {
		ConfigClientProperties r1Properties = new ConfigClientProperties();
		r1Properties.setUri(new String[] { "http://localhost:8888" });
		r1Properties.setLabel("foo");
		ConfigClientProperties r2Properties = new ConfigClientProperties();
		r2Properties.setUri(new String[] { "http://localhost:8888" });
		ConfigServerConfigDataResource r1 = new ConfigServerConfigDataResource(r1Properties, true, null);
		ConfigServerConfigDataResource r2 = new ConfigServerConfigDataResource(r2Properties, true, null);
		assertThat(r1).isNotEqualTo(r2);
		assertThat(r1.hashCode()).isNotEqualTo(r2.hashCode());
	}

	@Test
	void testInvalidUri() {
		ConfigClientProperties r1Properties = new ConfigClientProperties();
		r1Properties.setUri(new String[] { "//" });
		ConfigServerConfigDataResource r1 = new ConfigServerConfigDataResource(r1Properties, true, null);
		ConfigClientProperties r2Properties = new ConfigClientProperties();
		r2Properties.setUri(new String[] { "//" });
		ConfigServerConfigDataResource r2 = new ConfigServerConfigDataResource(r2Properties, true, null);
		assertThat(r1).isEqualTo(r2);
	}

}
