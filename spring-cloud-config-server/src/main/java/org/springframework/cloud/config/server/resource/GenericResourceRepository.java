/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.config.server.resource;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.cloud.config.server.environment.SearchPathLocator;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

/**
 * An {@link ResourceRepository} backed by a {@link SearchPathLocator}.
 *
 * @author Dave Syer
 */
public class GenericResourceRepository
		implements ResourceRepository, ResourceLoaderAware {

	private ResourceLoader resourceLoader;

	private SearchPathLocator service;

	public GenericResourceRepository(SearchPathLocator service) {
		this.service = service;
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	@Override
	public synchronized Resource findOne(String application, String profile, String label,
			String path) {
		String[] locations = this.service.getLocations(application, profile, label).getLocations();
		try {
			for (int i = locations.length; i-- > 0;) {
				String location = locations[i];
				for (String local : getProfilePaths(profile, path)) {
					Resource file = this.resourceLoader.getResource(location)
							.createRelative(local);
					if (file.exists() && file.isReadable()) {
						return file;
					}
				}
			}
		}
		catch (IOException e) {
			throw new NoSuchResourceException(
					"Error : " + path + ". (" + e.getMessage() + ")");
		}
		throw new NoSuchResourceException("Not found: " + path);
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
