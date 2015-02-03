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

import org.eclipse.jgit.util.FileUtils;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;

/**
 * @author Dave Syer
 */
public class ConfigServerTestUtils {

	public static String prepareLocalRepo() throws IOException {
		return prepareLocalRepo("./", "target/repos", "config-repo", "target/config");
	}

	public static String prepareLocalRepo(String repoPath) throws IOException {
		return prepareLocalRepo("./", "target/repos", repoPath, "target/config");
	}

	public static String prepareLocalRepo(String baseDir, String buildDir, String repoPath,
			String checkoutDir) throws IOException {
		buildDir = baseDir + buildDir;
		new File(buildDir).mkdirs();
		if (!repoPath.startsWith("/")) {
			repoPath = "/" + repoPath;
		}
		if (!repoPath.endsWith("/")) {
			repoPath = repoPath + "/";
		}
		File source = new File(baseDir + "src/test/resources" + repoPath);
		FileSystemUtils.copyRecursively(source, new File(buildDir + repoPath));
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

	public static String prepareLocalSvnRepo(String sourceDir, String checkoutDir) throws Exception {
		File sourceDirFile = new File(sourceDir);
		sourceDirFile.mkdirs();
		File local = new File(checkoutDir);
		if (local.exists()) {
			FileUtils.delete(local, FileUtils.RECURSIVE);
		}
		local.mkdirs();
		FileSystemUtils.copyRecursively(sourceDirFile, local);
		return StringUtils.cleanPath("file:///" + local.getAbsolutePath());

	}

	public static String getBaseDirectory(String potentialRoot) {
		return new File(potentialRoot).exists() ? potentialRoot + "/" : "./";
	}

	public static String copyLocalRepo(String path) throws IOException {
		File dest = new File("target/repos/" + path);
		FileSystemUtils.deleteRecursively(dest);
		FileSystemUtils.copyRecursively(new File("target/repos/config-repo"), dest);
		return "file:./target/repos/" + path;
	}

	public static boolean deleteLocalRepo(String path) throws IOException {
		File dest = new File("target/repos/" + path);
		return FileSystemUtils.deleteRecursively(dest);
	}

}
