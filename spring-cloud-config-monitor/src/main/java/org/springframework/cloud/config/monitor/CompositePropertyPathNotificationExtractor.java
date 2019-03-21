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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;
import org.springframework.util.MultiValueMap;

/**
 * A {@link PropertyPathNotificationExtractor} that cycles through a set of (ordered)
 * delegates, looking for the first non-null outcome.
 *
 * @author Dave Syer
 *
 */
public class CompositePropertyPathNotificationExtractor
		implements PropertyPathNotificationExtractor {

	private List<PropertyPathNotificationExtractor> extractors;

	public CompositePropertyPathNotificationExtractor(
			List<PropertyPathNotificationExtractor> extractors) {
		this.extractors = new ArrayList<>();
		if (extractors != null) {
			this.extractors.addAll(extractors);
		}
		this.extractors.add(new SimplePropertyPathNotificationExtractor());
		AnnotationAwareOrderComparator.sort(this.extractors);
	}

	@Override
	public PropertyPathNotification extract(MultiValueMap<String, String> headers,
			Map<String, Object> request) {
		for (PropertyPathNotificationExtractor extractor : this.extractors) {
			PropertyPathNotification result = extractor.extract(headers, request);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	@Order(Ordered.LOWEST_PRECEDENCE - 200)
	private static class SimplePropertyPathNotificationExtractor
			implements PropertyPathNotificationExtractor {

		@Override
		public PropertyPathNotification extract(MultiValueMap<String, String> headers,
				Map<String, Object> request) {
			Object object = request.get("path");
			if (object instanceof String) {
				return new PropertyPathNotification((String) object);
			}
			if (object instanceof Collection) {
				@SuppressWarnings("unchecked")
				Collection<String> collection = (Collection<String>) object;
				return new PropertyPathNotification(collection.toArray(new String[0]));
			}
			return null;
		}

	}

}
