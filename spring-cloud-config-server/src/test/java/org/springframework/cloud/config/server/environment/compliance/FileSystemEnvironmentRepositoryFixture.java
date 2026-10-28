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
import java.util.EnumSet;
import java.util.Set;

import org.springframework.util.FileSystemUtils;

/**
 * Base for fixtures of file based backends. Files are written to a local directory, one
 * sub-directory per label.
 *
 * @author Ryan Baxter
 */
public abstract class FileSystemEnvironmentRepositoryFixture implements EnvironmentRepositoryFixture {

	private final Path root;

	protected FileSystemEnvironmentRepositoryFixture(Path root) {
		this.root = root;
	}

	protected Path root() {
		return this.root;
	}

	@Override
	public void reset() throws IOException {
		FileSystemUtils.deleteRecursively(this.root);
		Files.createDirectories(this.root);
	}

	@Override
	public void store(ConfigFile file) throws IOException {
		Path path = this.root.resolve(file.path());
		Files.createDirectories(path.getParent());
		Files.writeString(path, file.content());
	}

	@Override
	public Set<Capability> capabilities() {
		return EnumSet.allOf(Capability.class);
	}

}
