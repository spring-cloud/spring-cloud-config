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

package org.springframework.cloud.config.server;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.util.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.ConfigServerTestUtils;
import org.springframework.cloud.config.server.JGitEnvironmentRepository;
import org.springframework.core.env.StandardEnvironment;

/**
 * @author Dave Syer
 *
 */
public class JGitEnvironmentRepositoryTests {

	private StandardEnvironment environment = new StandardEnvironment();
	private JGitEnvironmentRepository repository = new JGitEnvironmentRepository(
			environment);

	private File basedir = new File("target/config");

	@Before
	public void init() throws Exception {
		String uri = ConfigServerTestUtils.prepareLocalRepo();
		repository.setUri(uri);
		if (basedir.exists()) {
			FileUtils.delete(basedir, FileUtils.RECURSIVE | FileUtils.RETRY);
		}
	}

	@Test
	public void vanilla() {
		repository.findOne("bar", "staging", "master");
		Environment environment = repository.findOne("bar", "staging", "master");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals(repository.getUri() + "/bar.properties", environment
				.getPropertySources().get(0).getName());
	}

	@Test
	public void nested() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo("another-config-repo");
		repository.setUri(uri);
		repository.setSearchPaths(new String[] {"sub"});
		repository.findOne("bar", "staging", "master");
		Environment environment = repository.findOne("bar", "staging", "master");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals(repository.getUri() + "/sub/application.yml", environment
				.getPropertySources().get(0).getName());
	}

	@Test
	public void nestedPattern() throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo("another-config-repo");
		repository.setUri(uri);
		repository.setSearchPaths(new String[] {"sub*"});
		repository.findOne("bar", "staging", "master");
		Environment environment = repository.findOne("bar", "staging", "master");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals(repository.getUri() + "/sub/application.yml", environment
				.getPropertySources().get(0).getName());
	}

	@Test
	public void branch() {
		repository.setBasedir(basedir);
		Environment environment = repository.findOne("bar", "staging", "raw");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals(repository.getUri() + "/bar.properties", environment
				.getPropertySources().get(0).getName());
	}

	@Test
	public void tag() {
		repository.setBasedir(basedir);
		Environment environment = repository.findOne("bar", "staging", "foo");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals(repository.getUri() + "/bar.properties", environment
				.getPropertySources().get(0).getName());
	}

	@Test
	public void basedir() {
		repository.setBasedir(basedir);
		repository.findOne("bar", "staging", "master");
		Environment environment = repository.findOne("bar", "staging", "master");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals(repository.getUri() + "/bar.properties", environment
				.getPropertySources().get(0).getName());
	}

	@Test
	public void basedirExists() throws Exception {
		assertTrue(basedir.mkdirs());
		assertTrue(new File(basedir, ".nothing").createNewFile());
		repository.setBasedir(basedir);
		repository.findOne("bar", "staging", "master");
		Environment environment = repository.findOne("bar", "staging", "master");
		assertEquals(2, environment.getPropertySources().size());
		assertEquals(repository.getUri() + "/bar.properties", environment
				.getPropertySources().get(0).getName());
	}
	
	@Test
	public void uriWithHostOnly() throws Exception {
		repository.setUri("git://localhost");
		assertEquals("git://localhost/", repository.getUri());
	}

	@Test
	public void uriWithHostAndPath() throws Exception {
		repository.setUri("git://localhost/foo/");
		assertEquals("git://localhost/foo", repository.getUri());
	}

}
