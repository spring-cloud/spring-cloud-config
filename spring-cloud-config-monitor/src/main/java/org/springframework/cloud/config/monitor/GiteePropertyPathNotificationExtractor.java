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

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.MultiValueMap;

/**
 * @author lly 835
 *
 */
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class GiteePropertyPathNotificationExtractor
		extends BasePropertyPathNotificationExtractor {

	private static final String HEADERS_KEY = "x-git-oschina-event";

	private static final String HEADERS_VALUE = "Push Hook";

	@Override
	protected boolean requestBelongsToGitRepoManager(
			MultiValueMap<String, String> headers) {
		return HEADERS_VALUE.equals(headers.getFirst(HEADERS_KEY));
	}

}
