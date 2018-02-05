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

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.MultiValueMap;

import java.util.Collection;
import java.util.Map;

/**
 * @author lly835@163.com
 *
 */
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class GiteePropertyPathNotificationExtractor
		implements PropertyPathNotificationExtractor {

	@Override
	public PropertyPathNotification extract(MultiValueMap<String, String> headers,
			Map<String, Object> request) {
		if ("Push Hook".equals(headers.getFirst("x-git-oschina-event"))) {
            if (request.get("commits") instanceof Collection &&
                    ((Collection<Map<String, Object>>) request.get("commits")).size() > 0) {
                return new PropertyPathNotification("application.yml");
            }
		}
		return null;
	}
}
