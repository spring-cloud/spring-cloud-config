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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.cloud.config.Environment;

import com.jcraft.jsch.Session;

/**
 * @author Dave Syer
 *
 */
public class JGitEnvironmentRepositoryTests {

	private StandardEnvironment environment = new StandardEnvironment();
	private JGitEnvironmentRepository repository = new JGitEnvironmentRepository(
			environment);

	private File basedir = new File("target/config-repo");

	@Before
	public void init() throws Exception {
		SshSessionFactory.setInstance(new JschConfigSessionFactory() {
			@Override
			protected void configure(Host hc, Session session) {
				session.setConfig("StrictHostKeyChecking", "no");
			}
		});
		File dotGit = new File("target/test-classes/config-repo/.git");
		File git = new File("target/test-classes/config-repo/git");
		if (git.exists()) {
			if (dotGit.exists()) {
				FileUtils.delete(dotGit, FileUtils.RECURSIVE);
			}
		}
		git.renameTo(dotGit);
		repository
				.setUri(environment
						.resolvePlaceholders("${user.name}@localhost:${user.dir}/target/test-classes/config-repo"));
		if (basedir.exists()) {
			FileUtils.delete(basedir, FileUtils.RECURSIVE);
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

}
