/*
 * Copyright 2013-2016 the original author or authors.
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
package org.springframework.cloud.config.server;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * @author Ryan Baxter
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ConfigServerApplication.class,
		properties = { "spring.config.name:compositeconfigserver",
				"spring.cloud.config.server.svn.uri:file:///./target/repos/svn-config-repo",
				"spring.cloud.config.server.svn.order:2",
				"spring.cloud.config.server.git.uri:file:./target/repos/config-repo",
				"spring.cloud.config.server.git.order:1"},
		webEnvironment = RANDOM_PORT)
@ActiveProfiles({ "test", "git", "subversion" })
public class CompositeConfigServerIntegrationTests {

	@LocalServerPort
	private int port;




	@BeforeClass
	public static void init() throws Exception {
		ConfigServerTestUtils.prepareLocalRepo();
		ConfigServerTestUtils.prepareLocalSvnRepo("src/test/resources/svn-config-repo",
				"target/repos/svn-config-repo");
	}

	@Test
	public void contextLoads() {
		Environment environment = new TestRestTemplate().getForObject("http://localhost:"
				+ port + "/foo/development/", Environment.class);
		assertEquals(3, environment.getPropertySources().size());
		assertEquals("overrides", environment.getPropertySources().get(0).getName());
		assertTrue(environment.getPropertySources().get(1).getName().contains("config-repo") &&
				!environment.getPropertySources().get(1).getName().contains("svn-config-repo"));
		assertTrue(environment.getPropertySources().get(2).getName().contains("svn-config-repo"));
		assertEquals("{spring.cloud.config.enabled=true}", environment
				.getPropertySources().get(0).getSource().toString());
	}

	@Test
	public void resourseEndpointsWork() {
		//This request will get the file from the Git Repo
		String text = new TestRestTemplate().getForObject("http://localhost:"
				+ port + "/foo/development/composite/bar.properties", String.class);

		String expected = "foo: bar";
		assertEquals("invalid content", expected, text);

		//This request will get the file from the SVN Repo
		text = new TestRestTemplate().getForObject("http://localhost:"
				+ port + "/foo/development/composite/bar.properties", String.class);
		assertEquals("invalid content", expected, text);
	}
}
