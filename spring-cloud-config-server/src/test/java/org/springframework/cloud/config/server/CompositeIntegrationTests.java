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

package org.springframework.cloud.config.server;

import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.util.SystemReader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.cloud.config.server.test.TestConfigServerApplication;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.config.server.test.ConfigServerTestUtils.getV2AcceptEntity;

/**
 * @author Ryan Baxter
 * @author Dylan Roberts
 */
public class CompositeIntegrationTests {

	@SpringBootTest(classes = TestConfigServerApplication.class,
			properties = { "spring.config.name:compositeconfigserver",
					"spring.cloud.config.server.svn.uri:file:///./target/repos/svn-config-repo",
					"spring.cloud.config.server.svn.order:2",
					"spring.cloud.config.server.git.uri:file:./target/repos/config-repo",
					"spring.cloud.config.server.git.order:1" },
			webEnvironment = RANDOM_PORT)
	@ActiveProfiles({ "test", "git", "subversion" })
	public static class StaticTests {

		@LocalServerPort
		private int port;

		@BeforeAll
		public static void init() throws Exception {
			// mock Git configuration to make tests independent of local Git configuration
			SystemReader.setInstance(new MockSystemReader());

			ConfigServerTestUtils.prepareLocalRepo();
			ConfigServerTestUtils.prepareLocalSvnRepo("src/test/resources/svn-config-repo",
					"target/repos/svn-config-repo");
		}

		@Test
		public void contextLoads() {
			ResponseEntity<Environment> response = new TestRestTemplate().exchange(
					"http://localhost:" + this.port + "/foo/development", HttpMethod.GET, getV2AcceptEntity(),
					Environment.class);
			Environment environment = response.getBody();
			assertThat(3).isEqualTo(environment.getPropertySources().size());
			assertThat("overrides").isEqualTo(environment.getPropertySources().get(0).getName());
			assertThat(environment.getPropertySources().get(1).getName().contains("config-repo")
					&& !environment.getPropertySources().get(1).getName().contains("svn-config-repo")).isTrue();
			assertThat(environment.getPropertySources().get(2).getName()).contains("svn-config-repo");
			ConfigServerTestUtils.assertConfigEnabled(environment);
		}

		@Test
		public void resourceEndpointsWork() {
			// This request will get the file from the Git Repo because its order is first
			// The SVN repo should have the content foo: bar
			String text = new TestRestTemplate().getForObject(
					"http://localhost:" + this.port + "/foo/development/composite/bar.properties", String.class);

			String expected = "foo: barconfig\n";
			assertThat(expected).isEqualTo(text).as("invalid content");
		}

	}

	@SpringBootTest(classes = TestConfigServerApplication.class,
			properties = { "spring.config.name:compositeconfigserver",
					"spring.cloud.config.server.svn.uri:file:///./target/repos/svn-config-repo",
					"spring.cloud.config.server.svn.order:2",
					"spring.cloud.config.server.git.uri:file:./target/repos/config-repo",
					"spring.cloud.config.server.git.order:1", "spring.cloud.config.server.reverseLocationOrder:true" },
			webEnvironment = RANDOM_PORT)
	@ActiveProfiles({ "test", "git", "subversion" })
	public static class ReverseLocationOrderTest {

		@LocalServerPort
		private int port;

		@BeforeAll
		public static void init() throws Exception {
			// mock Git configuration to make tests independent of local Git configuration
			SystemReader.setInstance(new MockSystemReader());

			ConfigServerTestUtils.prepareLocalRepo();
			ConfigServerTestUtils.prepareLocalSvnRepo("src/test/resources/svn-config-repo",
					"target/repos/svn-config-repo");
		}

		@Test
		public void resourceEndpointsWork() {
			// This request will get the file from the Git Repo because its order is first
			// However since spring.cloud.config.server.reverseLocationOrder is true, the
			// SVN repo
			// will be searched first and return the file from that repo
			String text = new TestRestTemplate().getForObject(
					"http://localhost:" + this.port + "/foo/development/composite/bar.properties", String.class);

			String expected = "foo: bar";
			assertThat(expected).isEqualTo(text).as("invalid content");
		}

	}

	@SpringBootTest(classes = TestConfigServerApplication.class,
			properties = { "spring.config.name:compositeconfigserver",
					"spring.cloud.config.server.composite[0].uri:file:./target/repos/config-repo",
					"spring.cloud.config.server.composite[0].type:git",
					"spring.cloud.config.server.composite[1].uri:file:///./target/repos/svn-config-repo",
					"spring.cloud.config.server.composite[1].type:svn" },
			webEnvironment = RANDOM_PORT)
	@ActiveProfiles({ "test", "composite" })
	public static class ListTests {

		@LocalServerPort
		private int port;

		@BeforeAll
		public static void init() throws Exception {
			// mock Git configuration to make tests independent of local Git configuration
			SystemReader.setInstance(new MockSystemReader());

			ConfigServerTestUtils.prepareLocalRepo();
			ConfigServerTestUtils.prepareLocalSvnRepo("src/test/resources/svn-config-repo",
					"target/repos/svn-config-repo");
		}

		@Test
		public void contextLoads() {
			ResponseEntity<Environment> response = new TestRestTemplate().exchange(
					"http://localhost:" + this.port + "/foo/development", HttpMethod.GET, getV2AcceptEntity(),
					Environment.class);
			Environment environment = response.getBody();
			assertThat(environment.getPropertySources()).hasSize(3);
			assertThat("overrides").isEqualTo(environment.getPropertySources().get(0).getName());
			assertThat(environment.getPropertySources().get(1).getName().contains("config-repo")
					&& !environment.getPropertySources().get(1).getName().contains("svn-config-repo")).isTrue();
			assertThat(environment.getPropertySources().get(2).getName()).contains("svn-config-repo");
			ConfigServerTestUtils.assertConfigEnabled(environment);
		}

		@Test
		public void resourceEndpointsWork() {
			// This request will get the file from the Git Repo
			String text = new TestRestTemplate().getForObject(
					"http://localhost:" + this.port + "/foo/development/composite/bar.properties", String.class);

			String expected = "foo: barconfig\n";
			assertThat(expected).isEqualTo(text).as("invalid content");

			// This request will get the file from the SVN Repo
			text = new TestRestTemplate().getForObject(
					"http://localhost:" + this.port + "/foo/development/composite/bar.properties", String.class);
			assertThat(expected).isEqualTo(text).as("invalid content");
		}

	}

}
