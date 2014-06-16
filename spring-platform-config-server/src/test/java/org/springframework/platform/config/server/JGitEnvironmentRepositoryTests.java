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

package org.springframework.platform.config.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.eclipse.jgit.util.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.platform.config.Environment;

/**
 * @author Dave Syer
 *
 */
public class JGitEnvironmentRepositoryTests {

	private JGitEnvironmentRepository repository = new JGitEnvironmentRepository();

	private File basedir = new File("target/config-repo");

	@Before
	public void init() throws Exception {
		if (basedir.exists()) {
			FileUtils.delete(basedir, FileUtils.RECURSIVE);
		}
	}

	@Test
	public void vanilla() {
		repository.findOne("bar", "staging", "master");
		Environment environment = repository.findOne("bar", "staging", "master");
		assertEquals(1, environment.getPropertySources().size());
		assertEquals(JGitEnvironmentRepository.DEFAULT_URI + "/bar.properties",
				environment.getPropertySources().get(0).getName());
	}

	@Test
	public void basedir() {
		repository.setBasedir(basedir);
		repository.findOne("bar", "staging", "master");
		Environment environment = repository.findOne("bar", "staging", "master");
		assertEquals(1, environment.getPropertySources().size());
		assertEquals(JGitEnvironmentRepository.DEFAULT_URI + "/bar.properties",
				environment.getPropertySources().get(0).getName());
	}

	@Test
	public void basedirExists() throws Exception {
		assertTrue(basedir.mkdirs());
		assertTrue(new File(basedir, ".nothing").createNewFile());
		repository.setBasedir(basedir);
		repository.findOne("bar", "staging", "master");
		Environment environment = repository.findOne("bar", "staging", "master");
		assertEquals(1, environment.getPropertySources().size());
		assertEquals(JGitEnvironmentRepository.DEFAULT_URI + "/bar.properties",
				environment.getPropertySources().get(0).getName());
	}

}
