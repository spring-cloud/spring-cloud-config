/*
 * Copyright 2026-present the original author or authors.
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

import org.springframework.boot.restclient.observation.ObservationRestTemplateCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;
import org.springframework.web.client.RestTemplate;

public class ObservationRestTemplateConfigurer {

	void instrument(ApplicationContext applicationContext, RestTemplate restTemplate) {
		if (!ClassUtils.isPresent("org.springframework.boot.restclient.observation.ObservationRestTemplateCustomizer",
				null)) {
			return;
		}
		// use getBeansOfType with the class loaded by ClassUtils
		applicationContext.getBeanProvider(ObservationRestTemplateCustomizer.class)
			.ifAvailable(customizer -> customizer.customize(restTemplate));
	}

}
