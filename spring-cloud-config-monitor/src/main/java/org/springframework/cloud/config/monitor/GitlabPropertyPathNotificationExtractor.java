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

package org.springframework.cloud.config.monitor;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.MultiValueMap;

/**
 * @author Dave Syer
 *
 */
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class GitlabPropertyPathNotificationExtractor
		implements PropertyPathNotificationExtractor {

	@Override
	public PropertyPathNotification extract(MultiValueMap<String, String> headers,
			Map<String, Object> request) {
		if ("Push Hook".equals(headers.getFirst("X-Gitlab-Event"))) {
			if (request.get("commits") instanceof Collection) {
				Set<String> paths = new HashSet<>();
				@SuppressWarnings("unchecked")
				Collection<Map<String, Object>> commits = (Collection<Map<String, Object>>) request
						.get("commits");
				for (Map<String, Object> commit : commits) {
					addAllPaths(paths, commit, "added");
					addAllPaths(paths, commit, "removed");
					addAllPaths(paths, commit, "modified");
				}
				if (!paths.isEmpty()) {
					return new PropertyPathNotification(paths.toArray(new String[0]));
				}
			}
		}
		return null;
	}

	private void addAllPaths(Set<String> paths, Map<String, Object> commit, String name) {
		@SuppressWarnings("unchecked")
		Collection<String> files = (Collection<String>) commit.get(name);
		if (files != null) {
			paths.addAll(files);
		}
	}
}
