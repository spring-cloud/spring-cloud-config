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
import org.springframework.cloud.config.server.environment.SearchPathLocator.Locations;
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
		this.repository.setSearchLocations((String[]) null);
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

	@Test
	public void placeholdersLabel() {
		this.repository.setSearchLocations("classpath:/test/{label}/");
		Environment environment = this.repository.findOne("foo", "development", "dev");
		assertEquals(1, environment.getPropertySources().size());
		assertEquals("dev_bar",
				environment.getPropertySources().get(0).getSource().get("foo"));
	}

	@Test
	public void placeholdersProfile() {
		this.repository.setSearchLocations("classpath:/test/{profile}/");
		Environment environment = this.repository.findOne("foo", "dev", "master");
		assertEquals(1, environment.getPropertySources().size());
		assertEquals("dev_bar",
				environment.getPropertySources().get(0).getSource().get("foo"));
	}

	@Test
	public void placeholdersProfiles() {
		this.repository.setSearchLocations("classpath:/test/{profile}/");
		Environment environment = this.repository.findOne("foo", "dev,mysql", "master");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals("mysql",
				environment.getPropertySources().get(0).getSource().get("foo"));
	}

	@Test
	public void placeholdersApplicationAndProfile() {
		this.repository.setSearchLocations("classpath:/test/{profile}/{application}/");
		Environment environment = this.repository.findOne("app", "dev", "master");
		assertEquals(1, environment.getPropertySources().size());
		assertEquals("app",
				environment.getPropertySources().get(0).getSource().get("foo"));
	}

	@Test
	public void locationPlaceholdersApplication() {
		this.repository.setSearchLocations("classpath:/test/{application}");
		Locations locations = this.repository.getLocations("foo", "dev", "master");
		assertEquals(1, locations.getLocations().length);
		assertEquals("classpath:/test/foo/", locations.getLocations()[0]);
	}

	@Test
	public void locationPlaceholdersMultipleApplication() {
		this.repository.setSearchLocations("classpath:/test/{application}");
		Locations locations = this.repository.getLocations("foo,bar", "dev", "master");
		assertEquals(2, locations.getLocations().length);
		assertEquals("classpath:/test/foo/", locations.getLocations()[0]);
		assertEquals("classpath:/test/bar/", locations.getLocations()[1]);
	}

	@Test
	public void locationProfilesApplication() {
		this.repository.setSearchLocations("classpath:/test/{profile}");
		Locations locations = this.repository.getLocations("foo", "dev,one,two",
				"master");
		assertEquals(3, locations.getLocations().length);
		assertEquals("classpath:/test/dev/", locations.getLocations()[0]);
	}

	@Test
	public void placeholdersNoTrailingSlash() {
		this.repository.setSearchLocations("classpath:/test/{label}");
		Environment environment = this.repository.findOne("foo", "development", "dev");
		assertEquals(1, environment.getPropertySources().size());
		assertEquals("dev_bar",
				environment.getPropertySources().get(0).getSource().get("foo"));
	}

}
