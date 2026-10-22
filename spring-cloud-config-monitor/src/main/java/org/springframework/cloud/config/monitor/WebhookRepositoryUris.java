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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.util.StringUtils;

/**
 * Collects the repository URIs that a webhook payload advertises, so that
 * {@link PropertyPathNotificationExtractor} implementations can report which Git
 * repository a notification came from.
 */
final class WebhookRepositoryUris {

	private static final List<String> URL_FIELDS = List.of("clone_url", "ssh_url", "git_url", "git_http_url",
			"git_ssh_url");

	private WebhookRepositoryUris() {
	}

	/**
	 * Adds the clone URLs of a repository object, as found in the {@code repository} or
	 * {@code project} object of a push payload.
	 * @param uris the URIs collected so far
	 * @param repository the repository object, ignored if it is not a map
	 */
	static void addRepository(Set<String> uris, Object repository) {
		if (!(repository instanceof Map<?, ?> map)) {
			return;
		}
		for (String field : URL_FIELDS) {
			add(uris, map.get(field));
		}
		if (map.get("links") instanceof Map<?, ?> links && links.get("clone") instanceof Collection<?> clones) {
			for (Object clone : clones) {
				if (clone instanceof Map<?, ?> link) {
					add(uris, link.get("href"));
				}
			}
		}
	}

	/**
	 * Adds a single URI.
	 * @param uris the URIs collected so far
	 * @param value the URI, ignored if it is not a non-blank string
	 */
	static void add(Set<String> uris, Object value) {
		if (value instanceof String uri && StringUtils.hasText(uri)) {
			uris.add(uri);
		}
	}

}
