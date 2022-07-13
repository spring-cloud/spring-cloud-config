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
import java.util.regex.Matcher;

import io.micrometer.observation.ObservationRegistry;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.environment.SearchPathLocator.Locations;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
		ConfigurableApplicationContext context = new SpringApplicationBuilder(NativeEnvironmentRepositoryTests.class)
				.properties("logging.level.org.springframework.boot.context.config=TRACE").web(WebApplicationType.NONE)
				.run();
		this.repository = new NativeEnvironmentRepository(context.getEnvironment(), new NativeEnvironmentProperties(),
				ObservationRegistry.NOOP);
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
		assertThat(environment.getVersion()).as("version was wrong").isEqualTo("myversion");
	}

	@Test
	public void ignoresExistingProfile() {
		System.setProperty("spring.profiles.active", "cloud");
		Environment environment = this.repository.findOne("foo", "main", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getVersion()).as("version was wrong").isEqualTo("myversion");
	}

	@Test
	public void prefixed() {
		this.repository.setSearchLocations("classpath:/test");
		Environment environment = this.repository.findOne("foo", "development", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(environment.getVersion()).as("version was wrong").isEqualTo("myversion");
		// gh-1778 property sources has the same name.
		assertThat(environment.getPropertySources().get(0).getName())
				.isNotEqualTo(environment.getPropertySources().get(1).getName());
	}

	@Test
	public void prefixedYaml() {
		this.repository.setSearchLocations("classpath:/test");
		Environment environment = this.repository.findOne("bar", "development", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(environment.getVersion()).as("version was wrong").isEqualTo("myversion");
		// gh-1778 property sources has the same name.
		assertThat(environment.getPropertySources().get(0).getName())
				.isNotEqualTo(environment.getPropertySources().get(1).getName());
	}

	@Test
	public void prefixedMultiDocProperties() {
		this.repository.setSearchLocations("classpath:/test");
		Environment environment = this.repository.findOne("baz", "development", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(environment.getVersion()).as("version was wrong").isEqualTo("myversion");
		// gh-1778 property sources has the same name.
		assertThat(environment.getPropertySources().get(0).getName())
				.isNotEqualTo(environment.getPropertySources().get(1).getName());
	}

	@Test
	public void prefixedWithFile() {
		this.repository.setSearchLocations("file:./src/test/resources/test");
		Environment environment = this.repository.findOne("foo", "development", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(environment.getVersion()).as("version was wrong").isEqualTo("myversion");
	}

	@Test
	public void cleanBoot24() {
		Environment environment = new Environment("application");
		environment.add(new PropertySource(
				"Config resource 'file [/tmp/config-repo-7780026223759117699/application-dev.yml]' via location 'file:/tmp/config-repo-7780026223759117699/'",
				Collections.singletonMap("foo", "bar")));
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getPropertySources().get(0).getName().contains("application-dev.yml"));
	}

	@Test
	public void cleanBoot240Classpath() {
		Environment environment = new Environment("application");
		environment.add(new PropertySource(
				"Config resource 'classpath:/configs/application-myprofile.yml' via location 'classpath:/configs/' (document #0)",
				Collections.singletonMap("foo", "bar")));
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getPropertySources().get(0).getName().contains("application-myprofile.yml"));
	}

	@Test
	public void cleanBoot241Classpath() {
		Environment environment = new Environment("application");
		environment.add(new PropertySource(
				"Config resource 'class path resource [configs/application.yml]' via location 'classpath:/configs/' (document #0)",
				Collections.singletonMap("foo", "bar")));
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getPropertySources().get(0).getName().contains("application-myprofile.yml"));
	}

	@Test
	@Ignore // FIXME: configdata
	public void labelled() {
		this.repository.setSearchLocations("classpath:/test");
		Environment environment = this.repository.findOne("foo", "development", "dev", false);
		assertThat(environment.getPropertySources().size()).isEqualTo(3);
		// position 1 because it has higher precedence than anything except the
		// foo-development.properties
		assertThat(environment.getPropertySources().get(1).getSource().get("foo")).isEqualTo("dev_bar");
		assertThat(environment.getVersion()).as("version was wrong").isEqualTo("myversion");
	}

	@Test
	public void placeholdersLabel() {
		this.repository.setSearchLocations("classpath:/test/{label}/");
		Environment environment = this.repository.findOne("foo", "development", "dev");
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getPropertySources().get(0).getSource().get("foo")).isEqualTo("dev_bar");
	}

	@Test
	public void placeholdersProfile() {
		this.repository.setSearchLocations("classpath:/test/{profile}/");
		Environment environment = this.repository.findOne("foo", "dev", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getPropertySources().get(0).getSource().get("foo")).isEqualTo("dev_bar");
	}

	@Test
	public void placeholdersProfiles() {
		this.repository.setSearchLocations("classpath:/test/{profile}/");
		Environment environment = this.repository.findOne("foo", "dev,mysql", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(environment.getPropertySources().get(0).getSource().get("foo")).isEqualTo("mysql");
	}

	@Test
	public void placeholdersApplicationAndProfile() {
		this.repository.setSearchLocations("classpath:/test/{profile}/{application}/");
		Environment environment = this.repository.findOne("app", "dev", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getPropertySources().get(0).getSource().get("foo")).isEqualTo("app");
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
		Locations locations = this.repository.getLocations("foo", "dev,one,two", "master");
		assertThat(locations.getLocations().length).isEqualTo(3);
		assertThat(locations.getLocations()[0]).isEqualTo("classpath:/test/dev/");
	}

	@Test
	public void placeholdersNoTrailingSlash() {
		this.repository.setSearchLocations("classpath:/test/{label}");
		Environment environment = this.repository.findOne("foo", "development", "dev");
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getPropertySources().get(0).getSource().get("foo")).isEqualTo("dev_bar");
	}

	@Test
	public void locationAddLabelLocations() {
		this.repository.setSearchLocations("classpath:/test/dev/");
		Environment environment = this.repository.findOne("foo", "development", "ignore");
		assertThat(environment.getPropertySources().size()).isEqualTo(2);
		assertThat(environment.getPropertySources().get(0).getSource().get("foo")).isNotEqualTo("dev_bar");
	}

	@Test
	public void tryToStartReactive() {
		this.repository.setSearchLocations("classpath:/test/reactive/");
		Environment environment = this.repository.findOne("foo", "master", "default");
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getPropertySources().get(0).getSource().get("foo")).isEqualTo("reactive");
	}

	@Test
	public void locationDontAddLabelLocations() {
		this.repository.setSearchLocations("classpath:/test/dev/");
		this.repository.setAddLabelLocations(false);
		Environment environment = this.repository.findOne("foo", "development", "ignore");
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getPropertySources().get(0).getSource().get("foo")).isEqualTo("dev_bar");
	}

	@Test
	public void locationNoDuplicates() {
		this.repository.setSearchLocations("classpath:/test/{profile}", "classpath:/test/dev");
		Locations locations = this.repository.getLocations("foo", "dev", null);
		assertThat(locations.getLocations().length).isEqualTo(1);
	}

	@Test
	public void testDefaultLabel() {
		this.repository.setDefaultLabel("test");
		Environment environment = this.repository.findOne("foo", "default", null);
		assertThat(environment.getPropertySources().get(0).getSource().get("foo")).isEqualTo("test_bar");
	}

	@Test
	public void testImportVanilla() {
		testImport();
	}

	@Test
	public void testImportEmptySearchLocations() {
		this.repository.setSearchLocations();
		testImport();
	}

	@Test
	public void testImportPrefixedWithClasspath() {
		this.repository.setSearchLocations("classpath:/test");
		testImport();
	}

	@Test
	public void testImportPrefixedWithFile() {
		this.repository.setSearchLocations("file:./src/test/resources/test");
		testImport();
	}

	@Test
	public void testImportWithoutPrefix() {
		this.repository.setSearchLocations("src/test/resources/test");
		testImport();
	}

	private void testImport() {
		Environment environment = this.repository.findOne("import", "default", "master");
		// TODO should be 4, bar.yml contains 2 yaml documents
		assertThat(environment.getPropertySources().size()).isEqualTo(3);
		assertThat(environment.getPropertySources().get(0).getSource().get("foo")).isEqualTo("imported");
		assertThat(environment.getPropertySources().get(2).getSource().get("foo")).isEqualTo("importing");
	}

	@Test
	public void duplicateYamlKeys() {
		this.repository.setSearchLocations("classpath:/test/bad-syntax");
		NativeEnvironmentRepository repo = this.repository;
		assertThatExceptionOfType(FailedToConstructEnvironmentException.class)
				.isThrownBy(() -> repo.findOne("foo", "master", "default")).withMessage(
						"Could not construct context for config=foo profile=master label=default includeOrigin=false; nested exception is while constructing a mapping\n"
								+ " in 'reader', line 1, column 1:\n" + "    key: value\n" + "    ^\n"
								+ "found duplicate key key\n" + " in 'reader', line 2, column 1:\n" + "    key: value\n"
								+ "    ^\n");
	}

	@Test
	public void resourcePatternWorks() {
		String name = "Config resource 'abc' via location '123'";
		Matcher matcher = NativeEnvironmentRepository.RESOURCE_PATTERN.matcher(name);
		assertThat(matcher.find()).isTrue();
		assertThat(matcher.group(1)).isEqualTo("abc");
	}

}
