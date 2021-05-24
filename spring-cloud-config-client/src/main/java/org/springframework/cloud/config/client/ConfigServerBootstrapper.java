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

import java.util.function.BiFunction;
import java.util.function.Function;

import org.springframework.boot.BootstrapContext;
import org.springframework.boot.BootstrapRegistry;
import org.springframework.boot.BootstrapRegistry.InstanceSupplier;
import org.springframework.boot.BootstrapRegistryInitializer;
import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigDataLoaderContext;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

public class ConfigServerBootstrapper implements BootstrapRegistryInitializer {

	private Function<BootstrapContext, RestTemplate> restTemplateFactory;

	private LoaderInterceptor loaderInterceptor;

	static ConfigServerBootstrapper create() {
		return new ConfigServerBootstrapper();
	}

	// TODO: document there will be a ConfigClientProperties in BootstrapContext
	public ConfigServerBootstrapper withRestTemplateFactory(
			Function<BootstrapContext, RestTemplate> restTemplateFactory) {
		this.restTemplateFactory = restTemplateFactory;
		return this;
	}

	public ConfigServerBootstrapper withLoaderInterceptor(LoaderInterceptor loaderInterceptor) {
		this.loaderInterceptor = loaderInterceptor;
		return this;
	}

	@Override
	public void initialize(BootstrapRegistry registry) {
		if (restTemplateFactory != null) {
			registry.register(RestTemplate.class, restTemplateFactory::apply);
		}
		if (loaderInterceptor != null) {
			registry.register(LoaderInterceptor.class, InstanceSupplier.of(loaderInterceptor));
		}
	}

	public interface LoaderInterceptor extends Function<LoadContext, ConfigData> {

	}

	@FunctionalInterface
	public interface LoaderInvocation
			extends BiFunction<ConfigDataLoaderContext, ConfigServerConfigDataResource, ConfigData> {

	}

	public static class LoadContext {

		private final ConfigDataLoaderContext loaderContext;

		private final ConfigServerConfigDataResource resource;

		private final Binder binder;

		private final LoaderInvocation invocation;

		LoadContext(ConfigDataLoaderContext loaderContext, ConfigServerConfigDataResource resource, Binder binder,
				LoaderInvocation invocation) {
			Assert.notNull(loaderContext, "loaderContext may not be null");
			Assert.notNull(resource, "resource may not be null");
			Assert.notNull(binder, "binder may not be null");
			Assert.notNull(invocation, "invocation may not be null");
			this.loaderContext = loaderContext;
			this.resource = resource;
			this.binder = binder;
			this.invocation = invocation;
		}

		public ConfigDataLoaderContext getLoaderContext() {
			return this.loaderContext;
		}

		public ConfigServerConfigDataResource getResource() {
			return this.resource;
		}

		public Binder getBinder() {
			return this.binder;
		}

		public LoaderInvocation getInvocation() {
			return this.invocation;
		}

	}

}
