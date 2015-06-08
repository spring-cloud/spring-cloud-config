/*
 * Copyright 2013-2015 the original author or authors.
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

import java.io.File;

import org.eclipse.jgit.util.FileUtils;
import org.junit.Before;
import org.junit.Test;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Michael Prankl
 * @author Roy Clarkson
 */
public class SVNKitEnvironmentRepositoryTests {

	private StandardEnvironment environment = new StandardEnvironment();
	private SvnKitEnvironmentRepository repository = new SvnKitEnvironmentRepository(
			environment);

	private File basedir = new File("target/config");

	@Before
	public void init() throws Exception {
		String uri = ConfigServerTestUtils.prepareLocalSvnRepo(
				"src/test/resources/svn-config-repo", "target/repos/svn-config-repo");
		repository.setUri(uri);
		if (basedir.exists()) {
			FileUtils.delete(basedir, FileUtils.RECURSIVE | FileUtils.RETRY);
		}
	}

	@Test
	public void vanilla() {
		Environment environment = repository.findOne("bar", "staging", "trunk");
		assertEquals(2, environment.getPropertySources().size());
		assertTrue(environment.getPropertySources().get(0).getName()
				.contains("bar.properties"));
		assertTrue(environment.getPropertySources().get(1).getName()
				.contains("application.yml"));
	}

	@Test
	public void basedir() {
		repository.setBasedir(basedir);
		Environment environment = repository.findOne("bar", "staging", "trunk");
		assertEquals(2, environment.getPropertySources().size());
		assertTrue(environment.getPropertySources().get(0).getName()
				.contains("bar.properties"));
		assertTrue(environment.getPropertySources().get(1).getName()
				.contains("application.yml"));
	}

	@Test
	public void branch() {
		Environment environment = repository.findOne("bar", "staging",
				"branches/demobranch");
		assertEquals(1, environment.getPropertySources().size());
		assertTrue(environment.getPropertySources().get(0).getName()
				.contains("bar.properties"));
	}

	@Test
	public void vanilla_with_update() {
		repository.findOne("bar", "staging", "trunk");
		Environment environment = repository.findOne("bar", "staging", "trunk");
		assertEquals(2, environment.getPropertySources().size());
		assertTrue(environment.getPropertySources().get(0).getName()
				.contains("bar.properties"));
		assertTrue(environment.getPropertySources().get(1).getName()
				.contains("application.yml"));
	}

	@Test(expected=NoSuchLabelException.class)
	public void invalidLabel() {
		Environment environment = repository.findOne("bar", "staging", "unknownlabel");
		assertEquals(0, environment.getPropertySources().size());
	}

}
