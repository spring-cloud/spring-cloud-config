/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.config.server.resource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.cloud.config.server.environment.SearchPathLocator;
import org.springframework.cloud.config.server.support.PathUtils;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * An {@link ResourceRepository} backed by a {@link SearchPathLocator}.
 *
 * @author Dave Syer
 */
public class GenericResourceRepository implements ResourceRepository, ResourceLoaderAware {

	private ResourceLoader resourceLoader;

	private SearchPathLocator service;

	private ConfigServerProperties properties;

	private final Lock resourceLock = new ReentrantLock();

	public GenericResourceRepository(SearchPathLocator service) {
		this.service = service;
	}

	public GenericResourceRepository(SearchPathLocator service, ConfigServerProperties properties) {
		this(service);
		this.properties = properties;
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	@Override
	public Resource findOne(String application, String profile, String label, String path) {
		try {
			this.resourceLock.lock();
			if (StringUtils.hasText(path)) {
				String[] locations = this.service.getLocations(application, profile, label).getLocations();
				if (!ObjectUtils.isEmpty(properties) && properties.isReverseLocationOrder()) {
					Collections.reverse(Arrays.asList(locations));
				}
				ArrayList<Resource> locationResources = new ArrayList<>();
				for (String location : locations) {
					if (!PathUtils.isInvalidEncodedLocation(location)) {
						locationResources.add(this.resourceLoader.getResource(location.replaceFirst("optional:", "")));
					}
				}

				try {
					for (Resource location : locationResources) {
						for (String local : getProfilePaths(profile, path)) {
							if (!PathUtils.isInvalidPath(local) && !PathUtils.isInvalidEncodedPath(local)) {
								Resource file = location.createRelative(local);
								if (file.exists() && file.isReadable()
										&& PathUtils.checkResource(file, location, locationResources)) {
									return file;
								}
							}
						}
					}
				}
				catch (IOException e) {
					throw new NoSuchResourceException("Error : " + path + ". (" + e.getMessage() + ")");
				}
			}
			throw new NoSuchResourceException("Not found: " + path);
		}
		finally {
			this.resourceLock.unlock();
		}
	}

	private Collection<String> getProfilePaths(String profiles, String path) {
		Set<String> paths = new LinkedHashSet<>();
		for (String profile : StringUtils.commaDelimitedListToSet(profiles)) {
			if (!StringUtils.hasText(profile) || "default".equals(profile)) {
				paths.add(path);
			}
			else {
				String ext = StringUtils.getFilenameExtension(path);
				String file = path;
				if (ext != null) {
					ext = "." + ext;
					file = StringUtils.stripFilenameExtension(path);
				}
				else {
					ext = "";
				}
				paths.add(file + "-" + profile + ext);
			}
		}
		paths.add(path);
		return paths;
	}

}
