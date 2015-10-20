/*
 * Copyright 2013-2014 the original author or authors.
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
package org.springframework.cloud.config.server.environment;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * @author Dave Syer
 * @author Spencer Gibb
 *
 */
public class NativeEnvironmentRepositoryTests {

	private NativeEnvironmentRepository repository;

	@Before
	public void init() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(
				NativeEnvironmentRepositoryTests.class).web(false).run();
		this.repository = new NativeEnvironmentRepository(context.getEnvironment());
		this.repository.setVersion("myversion");
		context.close();
	}

	@Test
	public void emptySearchLocations() {
		this.repository.setSearchLocations((String[])null);
		Environment environment = this.repository.findOne("foo", "development", "master");
		assertEquals(2, environment.getPropertySources().size());
	}

	@Test
	public void vanilla() {
		Environment environment = this.repository.findOne("foo", "development", "master");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals("version was wrong", "myversion", environment.getVersion());
	}

	@Test
	public void ignoresExistingProfile() {
		System.setProperty("spring.profiles.active", "cloud");
		Environment environment = this.repository.findOne("foo", "main", "master");
		assertEquals(1, environment.getPropertySources().size());
		assertEquals("version was wrong", "myversion", environment.getVersion());
	}

	@Test
	public void prefixed() {
		this.repository.setSearchLocations("classpath:/test");
		Environment environment = this.repository.findOne("foo", "development", "master");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals("version was wrong", "myversion", environment.getVersion());
	}

	@Test
	public void prefixedWithFile() {
		this.repository.setSearchLocations("file:./src/test/resources/test");
		Environment environment = this.repository.findOne("foo", "development", "master");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals("version was wrong", "myversion", environment.getVersion());
	}

	@Test
	public void labelled() {
		this.repository.setSearchLocations("classpath:/test");
		Environment environment = this.repository.findOne("foo", "development", "dev");
		assertEquals(3, environment.getPropertySources().size());
		// position 1 because it has higher precedence than anything except the
		// foo-development.properties
		assertEquals("dev_bar",
				environment.getPropertySources().get(1).getSource().get("foo"));
		assertEquals("version was wrong", "myversion", environment.getVersion());
	}

}
