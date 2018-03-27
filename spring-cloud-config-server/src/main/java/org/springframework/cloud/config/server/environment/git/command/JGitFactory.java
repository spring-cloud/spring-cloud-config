/*
 * Copyright 2013-2018 the original author or authors.
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
package org.springframework.cloud.config.server.environment.git.command;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;

/**
 * Wraps the static method calls to {@link org.eclipse.jgit.api.Git} and
 * {@link org.eclipse.jgit.api.CloneCommand} allowing for easier unit testing.
 */
public class JGitFactory {

	public Git getGitByOpen(File file) throws IOException {
		return Git.open(file);
	}

	public CloneCommand getCloneCommandByCloneRepository() {
		return Git.cloneRepository();
	}
}
