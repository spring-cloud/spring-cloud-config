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

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.util.FileUtils;

/**
 * @author Dave Syer
 *
 */
public class ConfigServerTestUtils {

	public static String prepareLocalRepo() throws IOException {
		return prepareLocalRepo("target/test-classes", "config-repo", "target/config");
	}

	public static String prepareLocalRepo(String repoPath) throws IOException {
		return prepareLocalRepo("target/test-classes", repoPath, "target/config");
	}

	public static String prepareLocalRepo(String buildDir, String repoPath,
			String checkoutDir) throws IOException {
		if (!repoPath.startsWith("/")) {
			repoPath = "/" + repoPath;
		}
		if (!repoPath.endsWith("/")) {
			repoPath = repoPath + "/";
		}
		File dotGit = new File(buildDir + repoPath + ".git");
		File git = new File(buildDir + repoPath + "git");
		if (git.exists()) {
			if (dotGit.exists()) {
				FileUtils.delete(dotGit, FileUtils.RECURSIVE);
			}
		}
		git.renameTo(dotGit);
		File local = new File(checkoutDir);
		if (local.exists()) {
			FileUtils.delete(local, FileUtils.RECURSIVE);
		}
		if (!buildDir.startsWith("/")) {
			buildDir = "./" + buildDir;
		}
		return "file:" + buildDir + repoPath;
	}

}
