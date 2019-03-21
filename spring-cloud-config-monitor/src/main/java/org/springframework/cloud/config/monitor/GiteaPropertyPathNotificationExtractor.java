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
import java.util.Set;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.MultiValueMap;

/**
 * @author Juan Pablo Santos Rodríguez
 *
 */
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class GiteaPropertyPathNotificationExtractor
		extends BasePropertyPathNotificationExtractor {

	private static final String HEADERS_KEY = "X-Gitea-Event";

	private static final String HEADERS_VALUE = "push";

	/**
	 * gitea doesn't return which files have been added/modified/deleted yet.
	 *
	 * related issue: https://github.com/go-gitea/gitea/issues/4313
	 */
	@Override
	protected void addPaths(Set<String> paths, Collection<Map<String, Object>> commits) {
		paths.add("application.yml");
	}

	@Override
	protected boolean requestBelongsToGitRepoManager(
			MultiValueMap<String, String> headers) {
		return HEADERS_VALUE.equals(headers.getFirst(HEADERS_KEY));
	}

}
