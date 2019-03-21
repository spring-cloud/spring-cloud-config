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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.jgit.api.Git;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.ResourceUtils;

/**
 * Class that holds objects that can be used for testing.
 *
 * @author Ryan Lynch
 */
public class JGitConfigServerTestData {

	private LocalGit serverGit;

	private LocalGit clonedGit;

	private JGitEnvironmentRepository repository;

	private ConfigurableApplicationContext context;

	public JGitConfigServerTestData(LocalGit serverGit, LocalGit clonedGit,
			JGitEnvironmentRepository repository,
			ConfigurableApplicationContext context) {
		this.serverGit = serverGit;
		this.clonedGit = clonedGit;
		this.repository = repository;
		this.context = context;
	}

	public static JGitConfigServerTestData prepareClonedGitRepository(Class... sources)
			throws Exception {
		return prepareClonedGitRepository(Collections.emptySet(), sources);
	}

	public static JGitConfigServerTestData prepareClonedGitRepository(
			Collection<String> additionalProperties, Class... sources) throws Exception {
		// setup remote repository
		String remoteUri = ConfigServerTestUtils.prepareLocalRepo();
		File remoteRepoDir = ResourceUtils.getFile(remoteUri);
		Git remoteGit = Git.open(remoteRepoDir.getAbsoluteFile());
		remoteGit.checkout().setName("master").call();

		// setup local repository
		File clonedRepoDir = new File("target/repos/cloned");
		if (clonedRepoDir.exists()) {
			FileSystemUtils.deleteRecursively(clonedRepoDir);
		}
		else {
			clonedRepoDir.mkdirs();
		}
		Git clonedGit = Git.cloneRepository()
				.setURI("file://" + remoteRepoDir.getAbsolutePath())
				.setDirectory(clonedRepoDir).setBranch("master").setCloneAllBranches(true)
				.call();

		// setup our test spring application pointing to the local repo
		Collection<String> properties = new ArrayList<>(additionalProperties);
		properties.add("spring.cloud.config.server.git.uri:" + "file://"
				+ clonedRepoDir.getAbsolutePath());
		ConfigurableApplicationContext context = new SpringApplicationBuilder(sources)
				.web(WebApplicationType.NONE)
				.properties(properties.toArray(new String[0])).run();
		JGitEnvironmentRepository repository = context
				.getBean(JGitEnvironmentRepository.class);

		return new JGitConfigServerTestData(
				new JGitConfigServerTestData.LocalGit(remoteGit, remoteRepoDir),
				new JGitConfigServerTestData.LocalGit(clonedGit, clonedRepoDir),
				repository, context);
	}

	public LocalGit getServerGit() {
		return this.serverGit;
	}

	public LocalGit getClonedGit() {
		return this.clonedGit;
	}

	public JGitEnvironmentRepository getRepository() {
		return this.repository;
	}

	public ConfigurableApplicationContext getContext() {
		return this.context;
	}

	public static class LocalGit {

		private Git git;

		private File gitWorkingDirectory;

		public LocalGit(Git git, File gitWorkingDirectory) {
			this.git = git;
			this.gitWorkingDirectory = gitWorkingDirectory;
		}

		public Git getGit() {
			return this.git;
		}

		public File getGitWorkingDirectory() {
			return this.gitWorkingDirectory;
		}

	}

}
