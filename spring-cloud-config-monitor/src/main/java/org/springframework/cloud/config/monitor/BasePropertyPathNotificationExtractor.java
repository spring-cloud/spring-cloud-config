/*
 * Copyright 2015-present the original author or authors.
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

package org.springframework.cloud.config.monitor;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;

/**
 * @author Dave Syer
 *
 */
public abstract class BasePropertyPathNotificationExtractor implements PropertyPathNotificationExtractor {

	@Override
	public PropertyPathNotification extract(HttpHeaders headers, Map<String, Object> request) {
		if (requestBelongsToGitRepoManager(headers)) {
			if (request.get("commits") instanceof Collection) {
				Set<String> paths = new LinkedHashSet<>();
				@SuppressWarnings("unchecked")
				Collection<Map<String, Object>> commits = (Collection<Map<String, Object>>) request.get("commits");
				addPaths(paths, commits);
				if (!paths.isEmpty()) {
					PropertyPathNotification notification = new PropertyPathNotification(paths.toArray(new String[0]));
					Set<String> repositoryUris = new LinkedHashSet<>();
					addRepositoryUris(repositoryUris, request);
					notification.setRepositoryUris(repositoryUris.toArray(new String[0]));
					return notification;
				}
			}
		}
		return null;
	}

	protected void addPaths(Set<String> paths, Collection<Map<String, Object>> commits) {
		for (Map<String, Object> commit : commits) {
			addAllPaths(paths, commit, "added");
			addAllPaths(paths, commit, "removed");
			addAllPaths(paths, commit, "modified");
		}
	}

	private void addAllPaths(Set<String> paths, Map<String, Object> commit, String name) {
		@SuppressWarnings("unchecked")
		Collection<String> files = (Collection<String>) commit.get(name);
		if (files != null) {
			paths.addAll(files);
		}
	}

	/**
	 * Adds the URIs of the Git repository that sent the push. By default these are the
	 * clone URLs of the {@code repository} and {@code project} objects in the payload.
	 * @param uris the URIs collected so far
	 * @param request the webhook payload
	 */
	protected void addRepositoryUris(Set<String> uris, Map<String, Object> request) {
		PropertyPathNotificationExtractor.addCloneUris(uris, request.get("repository"));
		PropertyPathNotificationExtractor.addCloneUris(uris, request.get("project"));
	}

	protected abstract boolean requestBelongsToGitRepoManager(HttpHeaders headers);

}
