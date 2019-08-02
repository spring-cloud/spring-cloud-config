/*
 * Copyright 2013-2019 the original author or authors.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author Dave Syer
 * @author Roy Clarkson
 */
public class EnvironmentEncryptorEnvironmentRepositoryTests {

	@Rule
	public ExpectedException expected = ExpectedException.none();

	private EnvironmentRepository repository = Mockito.mock(EnvironmentRepository.class);

	private EnvironmentEncryptorEnvironmentRepository controller;

	private Environment environment = new Environment("foo", "master");

	@Before
	public void init() {
		this.controller = new EnvironmentEncryptorEnvironmentRepository(this.repository);
	}

	@Test
	public void allowOverrideFalse() throws Exception {
		this.controller.setOverrides(Collections.singletonMap("foo", "bar"));
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("a.b.c", "d");
		this.environment.add(new PropertySource("one", map));
		when(this.repository.findOne("foo", "bar", "master", false))
				.thenReturn(this.environment);
		assertThat(this.controller.findOne("foo", "bar", "master", false)
				.getPropertySources().get(0).getSource().toString())
						.isEqualTo("{foo=bar}");
	}

	@Test
	public void overrideWithEscapedPlaceholders() throws Exception {
		this.controller.setOverrides(Collections.singletonMap("foo", "$\\{bar}"));
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("bar", "foo");
		this.environment.add(new PropertySource("one", map));
		when(this.repository.findOne("foo", "bar", "master", false))
				.thenReturn(this.environment);
		assertThat(this.controller.findOne("foo", "bar", "master", false)
				.getPropertySources().get(0).getSource().toString())
						.isEqualTo("{foo=${bar}}");
	}

}
