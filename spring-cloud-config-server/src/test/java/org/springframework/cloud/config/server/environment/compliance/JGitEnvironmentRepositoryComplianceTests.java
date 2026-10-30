/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.config.server.environment.compliance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.micrometer.observation.ObservationRegistry;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.environment.JGitEnvironmentProperties;
import org.springframework.cloud.config.server.environment.JGitEnvironmentRepository;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.FileSystemUtils;

/**
 * Compliance tests for {@link JGitEnvironmentRepository}. Files without a label are
 * committed to {@code main} and each label is a branch.
 *
 * @author Ryan Baxter
 */
class JGitEnvironmentRepositoryComplianceTests extends EnvironmentRepositoryComplianceTests {

	@TempDir
	Path dir;

	private final Fixture fixture = new Fixture();

	@Override
	protected EnvironmentRepositoryFixture fixture() {
		return this.fixture;
	}

	class Fixture implements EnvironmentRepositoryFixture {

		private final List<ConfigFile> files = new ArrayList<>();

		private boolean committed;

		private int clones;

		@Override
		public void reset() {
			this.files.clear();
			this.committed = false;
		}

		@Override
		public void store(ConfigFile file) {
			this.files.add(file);
			this.committed = false;
		}

		@Override
		public void prepare() throws Exception {
			if (this.committed) {
				return;
			}
			Path remote = remote();
			FileSystemUtils.deleteRecursively(remote);
			try (Git git = Git.init().setDirectory(remote.toFile()).setInitialBranch("main").call()) {
				Files.writeString(remote.resolve("README.md"), "compliance");
				git.add().addFilepattern(".").call();
				git.commit().setSign(false).setMessage("Initial commit").call();
				Set<String> labels = new LinkedHashSet<>();
				this.files.stream().map(ConfigFile::label).filter(Objects::nonNull).forEach(labels::add);
				commit(git, remote, null);
				for (String label : labels) {
					git.checkout().setName(label).setCreateBranch(true).setStartPoint("main").call();
					commit(git, remote, label);
				}
				git.checkout().setName("main").call();
			}
			this.committed = true;
		}

		private void commit(Git git, Path remote, String label) throws Exception {
			for (ConfigFile file : this.files) {
				if (Objects.equals(file.label(), label)) {
					Files.writeString(remote.resolve(file.fileName()), file.content());
				}
			}
			git.add().addFilepattern(".").call();
			git.commit().setSign(false).setMessage("Configuration for " + (label != null ? label : "main")).call();
		}

		@Override
		public EnvironmentRepository repository() throws IOException {
			JGitEnvironmentProperties properties = new JGitEnvironmentProperties();
			properties.setUri(remote().toUri().toString());
			properties.setBasedir(Files.createDirectories(realDir().resolve("clone-" + this.clones++)).toFile());
			properties.setDefaultLabel("main");
			return new JGitEnvironmentRepository(new StandardEnvironment(), properties, ObservationRegistry.NOOP);
		}

		private Path remote() throws IOException {
			return realDir().resolve("remote");
		}

		// The repository rejects working directories below a symbolic link, such as
		// /var on macOS
		private Path realDir() throws IOException {
			return dir.toRealPath();
		}

		@Override
		public Set<Capability> capabilities() {
			return EnumSet.complementOf(EnumSet.of(Capability.DEFAULT_PROFILE_ALWAYS_ACTIVE));
		}

	}

}
