/*
 * Copyright 2013-present the original author or authors.
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

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;

import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigDataLoaderContext;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.config.client.ConfigServerBootstrapper.LoadContext;
import org.springframework.cloud.config.client.ConfigServerBootstrapper.LoaderInterceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * @author Mahammad Eminov
 */
public class ConfigClientRetryBootstrapperTests {

	private final AtomicInteger invocations = new AtomicInteger();

	@Test
	public void retriesUntilMaxAttemptsIsReachedWhenFailFast() {
		assertThatThrownBy(() -> load(true, true, 3)).isInstanceOf(ConfigClientFailFastException.class)
			.hasMessage("fail fast");

		assertThat(this.invocations).hasValue(3);
	}

	@Test
	public void doesNotRetryWhenFailFastIsNotSet() {
		assertThatThrownBy(() -> load(false, true, 3)).isInstanceOf(ConfigClientFailFastException.class)
			.hasMessage("fail fast");

		assertThat(this.invocations).hasValue(1);
	}

	@Test
	public void doesNotRetryWhenRetryIsDisabled() {
		assertThatThrownBy(() -> load(true, false, 3)).isInstanceOf(ConfigClientFailFastException.class)
			.hasMessage("fail fast");

		assertThat(this.invocations).hasValue(1);
	}

	@Test
	public void returnsTheResultOfASuccessfulRetry() {
		ConfigData configData = load(true, true, 3, () -> {
			if (this.invocations.incrementAndGet() < 3) {
				throw new ConfigClientFailFastException("fail fast", null);
			}
			return new ConfigData(Collections.emptyList());
		});

		assertThat(configData).isNotNull();
		assertThat(this.invocations).hasValue(3);
	}

	private ConfigData load(boolean failFast, boolean retryEnabled, int maxAttempts) {
		return load(failFast, retryEnabled, maxAttempts, () -> {
			this.invocations.incrementAndGet();
			throw new ConfigClientFailFastException("fail fast", null);
		});
	}

	private ConfigData load(boolean failFast, boolean retryEnabled, int maxAttempts, Supplier<ConfigData> invocation) {
		ConfigClientProperties properties = new ConfigClientProperties();
		properties.setFailFast(failFast);
		RetryProperties retryProperties = new RetryProperties();
		retryProperties.setEnabled(retryEnabled);
		retryProperties.setMaxAttempts(maxAttempts);
		retryProperties.setInitialInterval(10);

		ConfigServerConfigDataResource resource = new ConfigServerConfigDataResource(properties, true, null);
		resource.setLog(LogFactory.getLog(ConfigClientRetryBootstrapperTests.class));
		resource.setRetryProperties(retryProperties);

		DefaultBootstrapContext bootstrapContext = new DefaultBootstrapContext();
		new ConfigClientRetryBootstrapper().initialize(bootstrapContext);
		LoaderInterceptor interceptor = bootstrapContext.get(LoaderInterceptor.class);

		return interceptor.apply(new LoadContext(mock(ConfigDataLoaderContext.class), resource, mock(Binder.class),
				(loaderContext, loaderResource) -> invocation.get()));
	}

}
