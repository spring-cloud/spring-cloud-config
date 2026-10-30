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
import java.util.List;
import java.util.Set;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.io.TempDir;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNClientManager;

import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.environment.SvnKitEnvironmentProperties;
import org.springframework.cloud.config.server.environment.SvnKitEnvironmentRepository;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.FileSystemUtils;

/**
 * Compliance tests for {@link SvnKitEnvironmentRepository}. Files without a label are
 * imported into {@code trunk} and each label is a directory under {@code branches}.
 *
 * @author Ryan Baxter
 */
class SvnKitEnvironmentRepositoryComplianceTests extends EnvironmentRepositoryComplianceTests {

	@TempDir
	Path dir;

	private final Fixture fixture = new Fixture();

	@Override
	protected EnvironmentRepositoryFixture fixture() {
		return this.fixture;
	}

	class Fixture implements EnvironmentRepositoryFixture {

		private final List<ConfigFile> files = new ArrayList<>();

		private boolean imported;

		private int checkouts;

		@Override
		public void reset() {
			this.files.clear();
			this.imported = false;
		}

		@Override
		public void store(ConfigFile file) {
			this.files.add(file);
			this.imported = false;
		}

		@Override
		public void prepare() throws Exception {
			if (this.imported) {
				return;
			}
			Path staging = realDir().resolve("staging");
			FileSystemUtils.deleteRecursively(staging);
			Files.createDirectories(staging.resolve("trunk"));
			for (ConfigFile file : this.files) {
				Path path = staging.resolve(file.label() == null ? "trunk" : "branches/" + file.label())
					.resolve(file.fileName());
				Files.createDirectories(path.getParent());
				Files.writeString(path, file.content());
			}
			Path repository = realDir().resolve("repository");
			FileSystemUtils.deleteRecursively(repository);
			SVNURL url = SVNRepositoryFactory.createLocalRepository(repository.toFile(), true, false);
			SVNClientManager clientManager = SVNClientManager.newInstance();
			try {
				clientManager.getCommitClient()
					.doImport(staging.toFile(), url, "Configuration", null, false, true, SVNDepth.INFINITY);
			}
			finally {
				clientManager.dispose();
			}
			this.imported = true;
		}

		@Override
		public EnvironmentRepository repository() throws IOException {
			SvnKitEnvironmentProperties properties = new SvnKitEnvironmentProperties();
			properties.setUri(realDir().resolve("repository").toUri().toString());
			properties.setBasedir(Files.createDirectories(realDir().resolve("checkout-" + this.checkouts++)).toFile());
			return new SvnKitEnvironmentRepository(new StandardEnvironment(), properties, ObservationRegistry.NOOP);
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
