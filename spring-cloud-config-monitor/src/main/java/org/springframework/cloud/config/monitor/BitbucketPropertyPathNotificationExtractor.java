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

package org.springframework.cloud.config.monitor;

import java.util.Collection;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * @author Spencer Gibb
 * @author Dave Syer
 * @author Greg Jacobs
 *
 */
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class BitbucketPropertyPathNotificationExtractor
		implements PropertyPathNotificationExtractor {

	@Override
	public PropertyPathNotification extract(MultiValueMap<String, String> headers,
			Map<String, Object> request) {
		if (("repo:push".equals(headers.getFirst("X-Event-Key"))
				|| "pullrequest:fulfilled".equals(headers.getFirst("X-Event-Key")))
				&& StringUtils.hasText(headers.getFirst("X-Hook-UUID"))) {
			// Bitbucket cloud
			Object push = request.get("push");
			if (push instanceof Map
					&& ((Map<?, ?>) push).get("changes") instanceof Collection) {
				// Bitbucket doesn't tell us the files that changed so this is a
				// broadcast to all apps
				return new PropertyPathNotification("application.yml");
			}
		}
		else if ("repo:refs_changed".equals(headers.getFirst("X-Event-Key"))
				&& StringUtils.hasText(headers.getFirst("X-Request-Id"))) {
			// Bitbucket server
			if (request.get("changes") instanceof Collection) {
				// Bitbucket doesn't tell us the files that changed so this is a
				// broadcast to all apps
				return new PropertyPathNotification("application.yml");
			}
		}
		else if ("pr:merged".equals(headers.getFirst("X-Event-Key"))
				&& StringUtils.hasText(headers.getFirst("X-Request-Id"))) {
			// Bitbucket server
			// Bitbucket doesn't tell us the files that changed so this is a
			// broadcast to all apps
			return new PropertyPathNotification("application.yml");
		}

		return null;
	}

}
