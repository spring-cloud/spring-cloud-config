/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.cloud.config.server.support;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jgit.util.FileUtils;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StringUtils;

/**
 * Base class for components that want to access a source control management system.
 *
 * @author Dave Syer
 *
 */
public class AbstractScmAccessor implements ResourceLoaderAware {

	private static final String[] DEFAULT_LOCATIONS = new String[] { "/" };

	protected Log logger = LogFactory.getLog(getClass());
	/**
	 * Base directory for local working copy of repository.
	 */
	private File basedir;
	/**
	 * URI of remote repository.
	 */
	private String uri;
	private ConfigurableEnvironment environment;
	/**
	 * Username for authentication with remote repository.
	 */
	private String username;
	/**
	 * Password for authentication with remote repository.
	 */
	private String password;
	/**
	 * Passphrase for unlocking your ssh private key.
	 */
	private String passphrase;
 	/**
  	 * Reject incoming SSH host keys from remote servers not in the known host list.
  	 */
	private boolean strictHostKeyChecking = true;
	/**
	 * Search paths to use within local working copy. By default searches only the root.
	 */
	private String[] searchPaths = DEFAULT_LOCATIONS.clone();

	private ResourceLoader resourceLoader = new DefaultResourceLoader();

	public AbstractScmAccessor(ConfigurableEnvironment environment) {
		this.environment = environment;
		this.basedir = createBaseDir();
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	protected File createBaseDir() {
		try {
			final File basedir = Files.createTempDirectory("config-repo-").toFile();
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					try {
						FileUtils.delete(basedir, FileUtils.RECURSIVE);
					}
					catch (IOException e) {
						AbstractScmAccessor.this.logger.warn(
								"Failed to delete temporary directory on exit: " + e);
					}
				}
			});
			return basedir;
		}
		catch (IOException e) {
			throw new IllegalStateException("Cannot create temp dir", e);
		}
	}

	public ConfigurableEnvironment getEnvironment() {
		return this.environment;
	}

	public void setEnvironment(ConfigurableEnvironment environment) {
		this.environment = environment;
	}

	public void setUri(String uri) {
		while (uri.endsWith("/")) {
			uri = uri.substring(0, uri.length() - 1);
		}
		int index = uri.indexOf("://");
		if (index > 0 && !uri.substring(index + "://".length()).contains("/")) {
			// If there's no context path add one
			uri = uri + "/";
		}
		this.uri = uri;
	}

	public String getUri() {
		return this.uri;
	}

	public void setBasedir(File basedir) {
		this.basedir = basedir.getAbsoluteFile();
	}

	public File getBasedir() {
		return this.basedir;
	}

	public void setSearchPaths(String... searchPaths) {
		this.searchPaths = searchPaths;
	}

	public String[] getSearchPaths() {
		return this.searchPaths;
	}

	public String getUsername() {
		return this.username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return this.password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getPassphrase() {
		return passphrase;
	}

	public void setPassphrase(String passphrase) {
		this.passphrase = passphrase;
	}

	public boolean isStrictHostKeyChecking() {
		return strictHostKeyChecking;
	}

	public void setStrictHostKeyChecking(boolean strictHostKeyChecking) {
		this.strictHostKeyChecking = strictHostKeyChecking;
	}

	protected File getWorkingDirectory() {
		if (this.uri.startsWith("file:")) {
			try {
				return new UrlResource(StringUtils.cleanPath(this.uri)).getFile();
			}
			catch (Exception e) {
				throw new IllegalStateException(
						"Cannot convert uri to file: " + this.uri);
			}
		}
		return this.basedir;
	}

	protected String[] getSearchLocations(File dir, String application, String profile,
			String label) {
		String[] locations = this.searchPaths;
		if (locations == null || locations.length == 0) {
			locations = DEFAULT_LOCATIONS;
		}
		else if (locations != DEFAULT_LOCATIONS) {
			locations = StringUtils.concatenateStringArrays(DEFAULT_LOCATIONS, locations);
		}
		Collection<String> output = new LinkedHashSet<String>();
		for (String location : locations) {
			String[] profiles = new String[] { profile };
			if (profile != null) {
				profiles = StringUtils.commaDelimitedListToStringArray(profile);
			}
			String[] apps = new String[] { application };
			if (application != null) {
				apps = StringUtils.commaDelimitedListToStringArray(application);
			}
			for (String prof : profiles) {
				for (String app : apps) {
					String value = location;
					if (app != null) {
						value = value.replace("{application}", app);
					}
					if (prof != null) {
						value = value.replace("{profile}", prof);
					}
					if (label != null) {
						value = value.replace("{label}", label);
					}
					if (!value.endsWith("/")) {
						value = value + "/";
					}
					output.addAll(matchingDirectories(dir, value));
				}
			}
		}
		return output.toArray(new String[0]);
	}

	private List<String> matchingDirectories(File dir, String value) {
		List<String> output = new ArrayList<String>();
		try {
			PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(
					this.resourceLoader);
			String path = new File(dir, value).toURI().toString();
			for (Resource resource : resolver.getResources(path)) {
				if (resource.getFile().isDirectory()) {
					output.add(resource.getURI().toString());
				}
			}
		}
		catch (IOException e) {
		}
		return output;
	}

}
