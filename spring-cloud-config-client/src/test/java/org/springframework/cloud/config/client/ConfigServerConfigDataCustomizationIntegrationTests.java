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

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.BootstrapContext;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.config.client.ConfigServerBootstrapper.LoaderInterceptor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigServerConfigDataCustomizationIntegrationTests {

	@Test
	void customizableRestTemplate() {
		ConfigurableApplicationContext context = null;
		try {
			context = new SpringApplicationBuilder(TestConfig.class)
					.addBootstrapper(ConfigServerBootstrapper.create().withLoaderInterceptor(new Interceptor())
							.withRestTemplateFactory(this::restTemplate))
					.addBootstrapper(registry -> registry.addCloseListener(event -> {
						BootstrapContext bootstrapContext = event.getBootstrapContext();
						ConfigurableListableBeanFactory beanFactory = event.getApplicationContext().getBeanFactory();

						RestTemplate restTemplate = bootstrapContext.get(RestTemplate.class);
						beanFactory.registerSingleton("holder", new RestTemplateHolder(restTemplate));
						beanFactory.registerSingleton("interceptor", bootstrapContext.get(LoaderInterceptor.class));
					})).run("--spring.config.import=optional:configserver:", "--custom.prop=customval");

			RestTemplateHolder holder = context.getBean(RestTemplateHolder.class);
			assertThat(holder).isNotNull();
			assertThat(holder.restTemplate).isInstanceOf(CustomRestTemplate.class);
			CustomRestTemplate custom = (CustomRestTemplate) holder.restTemplate;
			assertThat(custom.customProp).isEqualTo("customval");

			LoaderInterceptor loaderInterceptor = context.getBean(LoaderInterceptor.class);
			assertThat(loaderInterceptor).isNotNull().isInstanceOf(Interceptor.class);
			Interceptor interceptor = (Interceptor) loaderInterceptor;
			assertThat(interceptor.applied).isTrue();
			assertThat(interceptor.hasBinder).isTrue();
		}
		finally {
			if (context != null) {
				context.close();
			}
		}
	}

	CustomRestTemplate restTemplate(BootstrapContext context) {
		ConfigClientProperties properties = context.get(ConfigClientProperties.class);
		String custom = context.get(Binder.class).bind("custom.prop", String.class).orElse("default-custom-prop");
		return new CustomRestTemplate(custom);
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class TestConfig {

	}

	static class Interceptor implements LoaderInterceptor {

		boolean applied;

		boolean hasBinder;

		@Override
		public ConfigData apply(ConfigServerBootstrapper.LoadContext context) {
			applied = true;
			hasBinder = context.getBinder() != null;
			return context.getInvocation().apply(context.getLoaderContext(), context.getLocation());
		}

	}

	static class RestTemplateHolder {

		final RestTemplate restTemplate;

		RestTemplateHolder(RestTemplate restTemplate) {
			this.restTemplate = restTemplate;
		}

	}

	static class CustomRestTemplate extends RestTemplate {

		private final String customProp;

		CustomRestTemplate(String customProp) {
			this.customProp = customProp;
		}

	}

}
