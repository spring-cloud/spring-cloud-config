/*
 * Copyright 2014-2019 the original author or authors.
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

package org.springframework.cloud.config.client;

import java.lang.reflect.Field;

import org.apache.commons.logging.Log;

import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.ReflectionUtils;

public final class RetryTemplateFactory {

	private static final Field field;

	static {
		field = ReflectionUtils.findField(RetryTemplate.class, "logger");
		if (field != null) {
			ReflectionUtils.makeAccessible(field);
		}
	}

	private RetryTemplateFactory() {

	}

	public static RetryTemplate create(RetryProperties properties, Log log) {
		RetryTemplate retryTemplate = RetryTemplate.builder().maxAttempts(properties.getMaxAttempts())
				.exponentialBackoff(properties.getInitialInterval(), properties.getMultiplier(),
						properties.getMaxInterval())
				.build();
		try {
			field.set(retryTemplate, log);
		}
		catch (IllegalAccessException e) {
			if (log.isErrorEnabled()) {
				log.error("error setting retry log", e);
			}
		}
		return retryTemplate;
	}

}
