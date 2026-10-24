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

import org.springframework.http.HttpHeaders;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * Strategy for extracting a {@link PropertyPathNotification} from an incoming,
 * unstructured request. Different providers of notifications have different payloads for
 * their events, and different headers (e.g. HTTP headers for a webhook).
 *
 * @author Dave Syer
 *
 */
public interface PropertyPathNotificationExtractor {

	default PropertyPathNotification extract(MultiValueMap<String, String> headers, Map<String, Object> payload) {
		return extract(new HttpHeaders(headers), payload);
	}

	PropertyPathNotification extract(HttpHeaders headers, Map<String, Object> payload);

	/**
	 * Adds the clone URLs of a repository object in a webhook payload, such as the
	 * {@code repository} or {@code project} object of a push event, to the URIs that
	 * identify the repository a notification came from.
	 * @param uris the URIs collected so far
	 * @param repository the repository object, ignored if it is not a map
	 */
	static void addCloneUris(Set<String> uris, Object repository) {
		if (!(repository instanceof Map<?, ?> map)) {
			return;
		}
		for (String field : List.of("clone_url", "ssh_url", "git_url", "git_http_url", "git_ssh_url")) {
			addUri(uris, map.get(field));
		}
		if (map.get("links") instanceof Map<?, ?> links && links.get("clone") instanceof Collection<?> clones) {
			for (Object clone : clones) {
				if (clone instanceof Map<?, ?> link) {
					addUri(uris, link.get("href"));
				}
			}
		}
	}

	/**
	 * Adds a single URI to the URIs that identify the repository a notification came
	 * from.
	 * @param uris the URIs collected so far
	 * @param uri the URI, ignored if it is not a non-blank string
	 */
	static void addUri(Set<String> uris, Object uri) {
		if (uri instanceof String value && StringUtils.hasText(value)) {
			uris.add(value);
		}
	}

}
