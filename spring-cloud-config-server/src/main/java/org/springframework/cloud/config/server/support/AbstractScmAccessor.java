/*
 * Copyright 2015-2019 the original author or authors.
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

package org.springframework.cloud.config.server.support;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;

/**
 * Base class for components that want to access a source control management system.
 *
 * @author Dave Syer
 */
public abstract class AbstractScmAccessor implements ResourceLoaderAware {

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
	private boolean strictHostKeyChecking;

	/**
	 * Search paths to use within local working copy. By default searches only the root.
	 */
	private String[] searchPaths;

	private ResourceLoader resourceLoader = new DefaultResourceLoader();

	public AbstractScmAccessor(ConfigurableEnvironment environment) {
		this.environment = environment;
		this.basedir = createBaseDir();
	}

	public AbstractScmAccessor(ConfigurableEnvironment environment,
			AbstractScmAccessorProperties properties) {
		this.environment = environment;
		this.basedir = properties.getBasedir() == null ? createBaseDir()
				: properties.getBasedir();
		this.passphrase = properties.getPassphrase();
		this.password = properties.getPassword();
		this.searchPaths = properties.getSearchPaths();
		this.strictHostKeyChecking = properties.isStrictHostKeyChecking();
		this.uri = properties.getUri();
		this.username = properties.getUsername();
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	protected File createBaseDir() {
		try {
			final Path basedir = Files.createTempDirectory("config-repo-");
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					try {
						FileSystemUtils.deleteRecursively(basedir);
					}
					catch (IOException e) {
						AbstractScmAccessor.this.logger.warn(
								"Failed to delete temporary directory on exit: " + e);
					}
				}
			});
			return basedir.toFile();
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

	public String getUri() {
		return this.uri;
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

	public File getBasedir() {
		return this.basedir;
	}

	public void setBasedir(File basedir) {
		this.basedir = basedir.getAbsoluteFile();
	}

	public String[] getSearchPaths() {
		return this.searchPaths;
	}

	public void setSearchPaths(String... searchPaths) {
		this.searchPaths = searchPaths;
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
		return this.passphrase;
	}

	public void setPassphrase(String passphrase) {
		this.passphrase = passphrase;
	}

	public boolean isStrictHostKeyChecking() {
		return this.strictHostKeyChecking;
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
			locations = AbstractScmAccessorProperties.DEFAULT_LOCATIONS;
		}
		else if (locations != AbstractScmAccessorProperties.DEFAULT_LOCATIONS) {
			locations = StringUtils.concatenateStringArrays(
					AbstractScmAccessorProperties.DEFAULT_LOCATIONS, locations);
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
