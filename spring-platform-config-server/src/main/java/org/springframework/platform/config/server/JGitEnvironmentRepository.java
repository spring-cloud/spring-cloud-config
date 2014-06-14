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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.eclipse.jgit.api.Git;
import org.springframework.platform.bootstrap.config.Environment;
import org.springframework.platform.bootstrap.config.PropertySource;

/**
 * @author Dave Syer
 *
 */
public class JGitEnvironmentRepository implements EnvironmentRepository {

	public static final String DEFAULT_URI = "https://github.com/scratches/config-repo";

	private File basedir;

	private String uri = DEFAULT_URI;

	public JGitEnvironmentRepository() {
		try {
			basedir = Files.createTempDirectory("config-repo-").toFile();
			basedir.deleteOnExit();
		} catch (IOException e) {
			throw new IllegalStateException("Cannot create temp dir", e);
		}
	}

	public void setUri(String uri) {
		while (uri.endsWith("/")) {
			uri = uri.substring(0, uri.length() - 1);
		}
		this.uri = uri;
	}

	@Override
	public Environment findOne(String application, String name, String label) {
		try {
			Git git;
			if (new File(basedir, ".git").exists()) {
				git = Git.open(basedir);
			} else {
				git = Git.cloneRepository().setURI(uri).setDirectory(basedir).call();
			}
			Environment result;
			synchronized (this) {
				SpringApplicationEnvironmentRepository environment = new SpringApplicationEnvironmentRepository();
				git.checkout().setName(label).call();
				String search = git.getRepository().getDirectory().getParent();
				environment.setSearchLocations(search);
				result = clean(environment.findOne(application, name, label));
			}
			return result;
		} catch (Exception e) {
			throw new IllegalStateException("Cannot clone repository", e);
		}
	}

	private Environment clean(Environment value) {
		Environment result = new Environment(value.getName(), value.getLabel());
		for (PropertySource source : value.getPropertySources()) {
			String name = source.getName().replace(basedir.toURI().toString(), "");
			name = name.replace("applicationConfig: [", "");
			name = uri + "/"
					+ name.substring(0, name.contains("]") ? name.lastIndexOf("]") : name.length());
			result.add(new PropertySource(name, source.getSource()));
		}
		return result;
	}
}
