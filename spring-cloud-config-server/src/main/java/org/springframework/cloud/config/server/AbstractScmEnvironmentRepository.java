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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jgit.util.FileUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.UrlResource;
import org.springframework.util.PatternMatchUtils;
import org.springframework.util.StringUtils;

/**
 * @author Dave Syer
 * @author Michael Prankl
 */
public abstract class AbstractScmEnvironmentRepository implements EnvironmentRepository,
		InitializingBean {
	private static Log logger = LogFactory.getLog(AbstractScmEnvironmentRepository.class);

	private File basedir;
	private String uri;
	private ConfigurableEnvironment environment;
	private String username;
	private String password;
	private String[] searchPaths = new String[0];

	public AbstractScmEnvironmentRepository(ConfigurableEnvironment environment) {
		this.environment = environment;
		this.basedir = createBaseDir();
	}

	private File createBaseDir() {
		try {
			final File basedir = Files.createTempDirectory("config-repo-").toFile();
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					try {
						FileUtils.delete(basedir, FileUtils.RECURSIVE);
					}
					catch (IOException e) {
						logger.warn("Failed to delete temporary directory on exit: " + e);
					}
				}
			});
			return basedir;
		}
		catch (IOException e) {
			throw new IllegalStateException("Cannot create temp dir", e);
		}
	}
	
	protected ConfigurableEnvironment getEnvironment() {
		return environment;
	}

	protected void setEnvironment(ConfigurableEnvironment environment) {
		this.environment = environment;
	}

	public void setUri(String uri) {
		while (uri.endsWith("/")) {
			uri = uri.substring(0, uri.length() - 1);
		}
		int index = uri.indexOf("://");
		if (index>0 && !uri.substring(index+"://".length()).contains("/")) {
			// If there's no context path add one
			uri = uri + "/";
		}
		this.uri = uri;
	}

	public String getUri() {
		return uri;
	}

	public void setBasedir(File basedir) {
		this.basedir = basedir.getAbsoluteFile();
	}

	public File getBasedir() {
		return basedir;
	}

	public void setSearchPaths(String... searchPaths) {
		this.searchPaths = searchPaths;
	}

	public String[] getSearchPaths() {
		return searchPaths;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	protected File getWorkingDirectory() {
		if (uri.startsWith("file:")) {
			try {
				return new UrlResource(StringUtils.cleanPath(uri)).getFile();
			}
			catch (Exception e) {
				throw new IllegalStateException("Cannot convert uri to file: " + uri);
			}
		}
		return basedir;
	}

	protected String[] getSearchLocations(File dir) {
		List<String> locations = new ArrayList<String>();
		locations.add(dir.toURI().toString());
		String[] list = dir.list();
		if (list!=null) {
			for (String path : list) {
				File file = new File(dir, path);
				if (file.isDirectory() && PatternMatchUtils.simpleMatch(searchPaths, path)) {
					locations.add(file.toURI().toString());
				}
			}
		}
		return locations.toArray(new String[0]);
	}

	protected Environment clean(Environment value) {
		Environment result = new Environment(value.getName(), value.getProfiles(), value.getLabel());
		for (PropertySource source : value.getPropertySources()) {
			String name = source.getName().replace(
					getWorkingDirectory().toURI().toString(), "");
			name = name.replace("applicationConfig: [", "");
			name = uri + "/" + name.replace("]", "");
			result.add(new PropertySource(name, source.getSource()));
		}
		return result;
	}

}