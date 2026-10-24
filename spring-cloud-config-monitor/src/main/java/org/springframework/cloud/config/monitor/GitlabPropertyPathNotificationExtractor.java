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

import java.util.Map;
import java.util.Set;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;

import static org.springframework.cloud.config.monitor.PropertyPathNotificationExtractor.addUri;

/**
 * @author Dave Syer
 *
 */
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class GitlabPropertyPathNotificationExtractor extends BasePropertyPathNotificationExtractor {

	@Override
	protected void addRepositoryUris(Set<String> uris, Map<String, Object> request) {
		super.addRepositoryUris(uris, request);
		if (request.get("repository") instanceof Map<?, ?> repository) {
			// Legacy GitLab payloads use url for the clone URL (GitHub uses it for an API
			// URL, which is why only GitLab reads it).
			addUri(uris, repository.get("url"));
		}
	}

	@Override
	protected boolean requestBelongsToGitRepoManager(HttpHeaders headers) {
		return isGitlabRequest(headers);
	}

	public static boolean isGitlabRequest(HttpHeaders headers) {
		return "Push Hook".equals(headers.getFirst("X-Gitlab-Event"));
	}

}
