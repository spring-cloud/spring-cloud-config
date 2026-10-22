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

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

/**
 * @author Spencer Gibb
 * @author Dave Syer
 * @author Greg Jacobs
 *
 */
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class BitbucketPropertyPathNotificationExtractor implements PropertyPathNotificationExtractor {

	public static boolean isBitbucketRequest(HttpHeaders headers) {
		String eventKey = headers.getFirst("X-Event-Key");
		if (eventKey == null) {
			return false;
		}
		if (StringUtils.hasText(headers.getFirst("X-Hook-UUID"))) {
			return "repo:push".equals(eventKey) || "pullrequest:fulfilled".equals(eventKey);
		}
		if (StringUtils.hasText(headers.getFirst("X-Request-Id"))) {
			return "repo:refs_changed".equals(eventKey) || "pr:merged".equals(eventKey);
		}
		return false;
	}

	@Override
	public PropertyPathNotification extract(HttpHeaders headers, Map<String, Object> request) {
		if (("repo:push".equals(headers.getFirst("X-Event-Key"))
				|| "pullrequest:fulfilled".equals(headers.getFirst("X-Event-Key")))
				&& StringUtils.hasText(headers.getFirst("X-Hook-UUID"))) {
			// Bitbucket cloud
			Object push = request.get("push");
			if (push instanceof Map<?, ?> map && map.get("changes") instanceof Collection) {
				// Bitbucket doesn't tell us the files that changed so this is a
				// broadcast to all apps
				Set<String> uris = new LinkedHashSet<>();
				if (request.get("repository") instanceof Map<?, ?> repository
						&& repository.get("links") instanceof Map<?, ?> links
						&& links.get("html") instanceof Map<?, ?> html) {
					// Bitbucket Cloud advertises the repository's HTTPS location in its
					// HTML link.
					WebhookRepositoryUris.add(uris, html.get("href"));
				}
				WebhookRepositoryUris.addRepository(uris, request.get("repository"));
				return notification(uris);
			}
		}
		else if ("repo:refs_changed".equals(headers.getFirst("X-Event-Key"))
				&& StringUtils.hasText(headers.getFirst("X-Request-Id"))) {
			// Bitbucket server
			if (request.get("changes") instanceof Collection) {
				// Bitbucket doesn't tell us the files that changed so this is a
				// broadcast to all apps
				Set<String> uris = new LinkedHashSet<>();
				WebhookRepositoryUris.addRepository(uris, request.get("repository"));
				return notification(uris);
			}
		}
		else if ("pr:merged".equals(headers.getFirst("X-Event-Key"))
				&& StringUtils.hasText(headers.getFirst("X-Request-Id"))) {
			// Bitbucket server
			// Bitbucket doesn't tell us the files that changed so this is a
			// broadcast to all apps
			Set<String> uris = new LinkedHashSet<>();
			if (request.get("pullRequest") instanceof Map<?, ?> pullRequest
					&& pullRequest.get("toRef") instanceof Map<?, ?> toRef) {
				WebhookRepositoryUris.addRepository(uris, toRef.get("repository"));
			}
			return notification(uris);
		}

		return null;
	}

	private static PropertyPathNotification notification(Set<String> repositoryUris) {
		PropertyPathNotification notification = new PropertyPathNotification("application.yml");
		notification.setRepositoryUris(repositoryUris.toArray(new String[0]));
		return notification;
	}

}
