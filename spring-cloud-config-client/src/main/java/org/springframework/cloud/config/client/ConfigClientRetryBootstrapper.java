/*
 * Copyright 2013-2020 the original author or authors.
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

import org.springframework.boot.BootstrapRegistry;
import org.springframework.boot.Bootstrapper;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.config.client.ConfigServerBootstrapper.LoaderInterceptor;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.ClassUtils;

/**
 * Bootstrapper.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class ConfigClientRetryBootstrapper implements Bootstrapper {

	static final boolean RETRY_IS_PRESENT = ClassUtils.isPresent("org.springframework.retry.annotation.Retryable",
			null);

	@Override
	public void intitialize(BootstrapRegistry registry) {
		if (!RETRY_IS_PRESENT) {
			return;
		}

		registry.registerIfAbsent(RetryProperties.class, context -> context.get(Binder.class)
				.bind(RetryProperties.PREFIX, RetryProperties.class).orElseGet(RetryProperties::new));

		registry.registerIfAbsent(RetryTemplate.class, context -> {
			RetryProperties properties = context.get(RetryProperties.class);
			return RetryTemplate.builder().maxAttempts(properties.getMaxAttempts()).exponentialBackoff(
					properties.getInitialInterval(), properties.getMultiplier(), properties.getMaxInterval()).build();
		});
		registry.registerIfAbsent(LoaderInterceptor.class, context -> {
			Binder binder = context.get(Binder.class);
			boolean failFast = binder.bind(ConfigClientProperties.PREFIX + ".fail-fast", Boolean.class).orElse(false);
			if (failFast) {
				// if (false) {
				RetryTemplate retryTemplate = context.get(RetryTemplate.class);
				return loadContext -> retryTemplate.execute(retryContext -> loadContext.getInvocation()
						.apply(loadContext.getLoaderContext(), loadContext.getResource()));
			}
			return null;
		});

	}

}
