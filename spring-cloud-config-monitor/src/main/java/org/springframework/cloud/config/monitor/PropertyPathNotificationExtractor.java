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

import java.util.Map;

import org.springframework.util.MultiValueMap;

/**
 * Strategy for extracting a {@link PropertyPathNotification} from an incoming,
 * unstructured request. Different providers of notifications have different payloads for
 * their events, and different headers (e.g. HTTP headers for a webhook).
 *
 * @author Dave Syer
 *
 */
public interface PropertyPathNotificationExtractor {

	PropertyPathNotification extract(MultiValueMap<String, String> headers,
			Map<String, Object> payload);

}
