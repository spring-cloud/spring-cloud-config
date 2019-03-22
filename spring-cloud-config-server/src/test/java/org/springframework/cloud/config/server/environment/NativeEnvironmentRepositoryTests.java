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

import org.junit.Before;
import org.junit.Test;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.environment.SearchPathLocator.Locations;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 * @author Spencer Gibb
 * @author Venil Noronha
 * @author Daniel Lavoie
 */
public class NativeEnvironmentRepositoryTests {

	private NativeEnvironmentRepository repository;

	@Before
	public void init() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(
				NativeEnvironmentRepositoryTests.class).web(WebApplicationType.NONE)
						.run();
		this.repository = new NativeEnvironmentRepository(context.getEnvironment(),
				new NativeEnvironmentProperties());
		this.repository.setVersion("myversion");
		this.repository.setDefaultLabel(null);
		context.close();
	}

	@Test
	public void emptySearchLocations() {
		this.repository.setSearchLocations((String[]) null);
		Environment environment = this.repository.findOne("foo", "development", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
	}

	@Test
	public void vanilla() {
		Environment environment = this.repository.findOne("foo", "development", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(environment.getVersion()).as("version was wrong")
				.isEqualTo("myversion");
	}

	@Test
	public void ignoresExistingProfile() {
		System.setProperty("spring.profiles.active", "cloud");
		Environment environment = this.repository.findOne("foo", "main", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getVersion()).as("version was wrong")
				.isEqualTo("myversion");
	}

	@Test
	public void prefixed() {
		this.repository.setSearchLocations("classpath:/test");
		Environment environment = this.repository.findOne("foo", "development", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(environment.getVersion()).as("version was wrong")
				.isEqualTo("myversion");
	}

	@Test
	public void prefixedWithFile() {
		this.repository.setSearchLocations("file:./src/test/resources/test");
		Environment environment = this.repository.findOne("foo", "development", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(environment.getVersion()).as("version was wrong")
				.isEqualTo("myversion");
	}

	@Test
	public void labelled() {
		this.repository.setSearchLocations("classpath:/test");
		Environment environment = this.repository.findOne("foo", "development", "dev");
		assertThat(environment.getPropertySources().size()).isEqualTo(3);
		// position 1 because it has higher precedence than anything except the
		// foo-development.properties
		assertThat(environment.getPropertySources().get(1).getSource().get("foo"))
				.isEqualTo("dev_bar");
		assertThat(environment.getVersion()).as("version was wrong")
				.isEqualTo("myversion");
	}

	@Test
	public void placeholdersLabel() {
		this.repository.setSearchLocations("classpath:/test/{label}/");
		Environment environment = this.repository.findOne("foo", "development", "dev");
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getPropertySources().get(0).getSource().get("foo"))
				.isEqualTo("dev_bar");
	}

	@Test
	public void placeholdersProfile() {
		this.repository.setSearchLocations("classpath:/test/{profile}/");
		Environment environment = this.repository.findOne("foo", "dev", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getPropertySources().get(0).getSource().get("foo"))
				.isEqualTo("dev_bar");
	}

	@Test
	public void placeholdersProfiles() {
		this.repository.setSearchLocations("classpath:/test/{profile}/");
		Environment environment = this.repository.findOne("foo", "dev,mysql", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(environment.getPropertySources().get(0).getSource().get("foo"))
				.isEqualTo("mysql");
	}

	@Test
	public void placeholdersApplicationAndProfile() {
		this.repository.setSearchLocations("classpath:/test/{profile}/{application}/");
		Environment environment = this.repository.findOne("app", "dev", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getPropertySources().get(0).getSource().get("foo"))
				.isEqualTo("app");
	}

	@Test
	public void locationPlaceholdersApplication() {
		this.repository.setSearchLocations("classpath:/test/{application}");
		Locations locations = this.repository.getLocations("foo", "dev", "master");
		assertThat(locations.getLocations().length).isEqualTo(1);
		assertThat(locations.getLocations()[0]).isEqualTo("classpath:/test/foo/");
	}

	@Test
	public void locationPlaceholdersMultipleApplication() {
		this.repository.setSearchLocations("classpath:/test/{application}");
		Locations locations = this.repository.getLocations("foo,bar", "dev", "master");
		assertThat(locations.getLocations().length).isEqualTo(2);
		assertThat(locations.getLocations()[0]).isEqualTo("classpath:/test/foo/");
		assertThat(locations.getLocations()[1]).isEqualTo("classpath:/test/bar/");
	}

	@Test
	public void locationProfilesApplication() {
		this.repository.setSearchLocations("classpath:/test/{profile}");
		Locations locations = this.repository.getLocations("foo", "dev,one,two",
				"master");
		assertThat(locations.getLocations().length).isEqualTo(3);
		assertThat(locations.getLocations()[0]).isEqualTo("classpath:/test/dev/");
	}

	@Test
	public void placeholdersNoTrailingSlash() {
		this.repository.setSearchLocations("classpath:/test/{label}");
		Environment environment = this.repository.findOne("foo", "development", "dev");
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getPropertySources().get(0).getSource().get("foo"))
				.isEqualTo("dev_bar");
	}

	@Test
	public void locationAddLabelLocations() {
		this.repository.setSearchLocations("classpath:/test/dev/");
		Environment environment = this.repository.findOne("foo", "development", "ignore");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(environment.getPropertySources().get(0).getSource().get("foo"))
				.isNotEqualTo("dev_bar");
	}

	@Test
	public void tryToStartReactive() {
		this.repository.setSearchLocations("classpath:/test/reactive/");
		Environment environment = this.repository.findOne("foo", "master", "default");
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getPropertySources().get(0).getSource().get("foo"))
				.isEqualTo("reactive");
	}

	@Test
	public void locationDontAddLabelLocations() {
		this.repository.setSearchLocations("classpath:/test/dev/");
		this.repository.setAddLabelLocations(false);
		Environment environment = this.repository.findOne("foo", "development", "ignore");
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getPropertySources().get(0).getSource().get("foo"))
				.isEqualTo("dev_bar");
	}

	@Test
	public void locationNoDuplicates() {
		this.repository.setSearchLocations("classpath:/test/{profile}",
				"classpath:/test/dev");
		Locations locations = this.repository.getLocations("foo", "dev", null);
		assertThat(locations.getLocations().length).isEqualTo(1);
	}

	@Test
	public void testDefaultLabel() {
		this.repository.setDefaultLabel("test");
		assertThat(this.repository.findOne("foo", "default", null).getPropertySources()
				.get(0).getSource().get("foo")).isEqualTo("test_bar");
	}

}
