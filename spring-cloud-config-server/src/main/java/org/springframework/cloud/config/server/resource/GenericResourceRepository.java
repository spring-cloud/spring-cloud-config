/*
 * Copyright 2013-2019 the original author or authors.
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
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.config.server.environment.SearchPathLocator;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

/**
 * An {@link ResourceRepository} backed by a {@link SearchPathLocator}.
 *
 * @author Dave Syer
 */
public class GenericResourceRepository
		implements ResourceRepository, ResourceLoaderAware {

	private static final Log logger = LogFactory.getLog(GenericResourceRepository.class);

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

		if (StringUtils.hasText(path)) {
			String[] locations = this.service.getLocations(application, profile, label)
					.getLocations();
			try {
				for (int i = locations.length; i-- > 0;) {
					String location = locations[i];
					for (String local : getProfilePaths(profile, path)) {
						if (!isInvalidPath(local) && !isInvalidEncodedPath(local)) {
							Resource file = this.resourceLoader.getResource(location)
									.createRelative(local);
							if (file.exists() && file.isReadable()) {
								return file;
							}
						}
					}
				}
			}
			catch (IOException e) {
				throw new NoSuchResourceException(
						"Error : " + path + ". (" + e.getMessage() + ")");
			}
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

	/**
	 * Check whether the given path contains invalid escape sequences.
	 * @param path the path to validate
	 * @return {@code true} if the path is invalid, {@code false} otherwise
	 */
	private boolean isInvalidEncodedPath(String path) {
		if (path.contains("%")) {
			try {
				// Use URLDecoder (vs UriUtils) to preserve potentially decoded UTF-8
				// chars
				String decodedPath = URLDecoder.decode(path, "UTF-8");
				if (isInvalidPath(decodedPath)) {
					return true;
				}
				decodedPath = processPath(decodedPath);
				if (isInvalidPath(decodedPath)) {
					return true;
				}
			}
			catch (IllegalArgumentException | UnsupportedEncodingException ex) {
				// Should never happen...
			}
		}
		return false;
	}

	/**
	 * Process the given resource path.
	 * <p>
	 * The default implementation replaces:
	 * <ul>
	 * <li>Backslash with forward slash.
	 * <li>Duplicate occurrences of slash with a single slash.
	 * <li>Any combination of leading slash and control characters (00-1F and 7F) with a
	 * single "/" or "". For example {@code "  / // foo/bar"} becomes {@code "/foo/bar"}.
	 * </ul>
	 * @since 3.2.12
	 */
	protected String processPath(String path) {
		path = StringUtils.replace(path, "\\", "/");
		path = cleanDuplicateSlashes(path);
		return cleanLeadingSlash(path);
	}

	private String cleanDuplicateSlashes(String path) {
		StringBuilder sb = null;
		char prev = 0;
		for (int i = 0; i < path.length(); i++) {
			char curr = path.charAt(i);
			try {
				if ((curr == '/') && (prev == '/')) {
					if (sb == null) {
						sb = new StringBuilder(path.substring(0, i));
					}
					continue;
				}
				if (sb != null) {
					sb.append(path.charAt(i));
				}
			}
			finally {
				prev = curr;
			}
		}
		return sb != null ? sb.toString() : path;
	}

	private String cleanLeadingSlash(String path) {
		boolean slash = false;
		for (int i = 0; i < path.length(); i++) {
			if (path.charAt(i) == '/') {
				slash = true;
			}
			else if (path.charAt(i) > ' ' && path.charAt(i) != 127) {
				if (i == 0 || (i == 1 && slash)) {
					return path;
				}
				return (slash ? "/" + path.substring(i) : path.substring(i));
			}
		}
		return (slash ? "/" : "");
	}

	/**
	 * Identifies invalid resource paths. By default rejects:
	 * <ul>
	 * <li>Paths that contain "WEB-INF" or "META-INF"
	 * <li>Paths that contain "../" after a call to
	 * {@link org.springframework.util.StringUtils#cleanPath}.
	 * <li>Paths that represent a {@link org.springframework.util.ResourceUtils#isUrl
	 * valid URL} or would represent one after the leading slash is removed.
	 * </ul>
	 * <p>
	 * <strong>Note:</strong> this method assumes that leading, duplicate '/' or control
	 * characters (e.g. white space) have been trimmed so that the path starts predictably
	 * with a single '/' or does not have one.
	 * @param path the path to validate
	 * @return {@code true} if the path is invalid, {@code false} otherwise
	 * @since 3.0.6
	 */
	protected boolean isInvalidPath(String path) {
		if (path.contains("WEB-INF") || path.contains("META-INF")) {
			if (logger.isWarnEnabled()) {
				logger.warn("Path with \"WEB-INF\" or \"META-INF\": [" + path + "]");
			}
			return true;
		}
		if (path.contains(":/")) {
			String relativePath = (path.charAt(0) == '/' ? path.substring(1) : path);
			if (ResourceUtils.isUrl(relativePath) || relativePath.startsWith("url:")) {
				if (logger.isWarnEnabled()) {
					logger.warn(
							"Path represents URL or has \"url:\" prefix: [" + path + "]");
				}
				return true;
			}
		}
		if (path.contains("..") && StringUtils.cleanPath(path).contains("../")) {
			if (logger.isWarnEnabled()) {
				logger.warn("Path contains \"../\" after call to StringUtils#cleanPath: ["
						+ path + "]");
			}
			return true;
		}
		return false;
	}

}
