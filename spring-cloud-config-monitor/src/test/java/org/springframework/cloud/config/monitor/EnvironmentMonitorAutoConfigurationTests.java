/*
 * Copyright 2015-present the original author or authors.
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

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.cloud.bus.BusProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class EnvironmentMonitorAutoConfigurationTests {

	@Test
	public void testExtractorsCount() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(BusConfig.class,
				EnvironmentMonitorAutoConfiguration.class, TomcatServletWebServerAutoConfiguration.class,
				ServerProperties.class, PropertyPlaceholderAutoConfiguration.class)
			.properties("server.port=-1")
			.run();
		PropertyPathEndpoint endpoint = context.getBean(PropertyPathEndpoint.class);
		assertThat(((Collection<?>) ReflectionTestUtils.getField(ReflectionTestUtils.getField(endpoint, "extractor"),
				"extractors")))
			.hasSize(7);
		context.close();
	}

	@Test
	public void testCanAddCustomPropertyPathNotificationExtractor() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(BusConfig.class,
				CustomPropertyPathNotificationExtractorConfig.class, EnvironmentMonitorAutoConfiguration.class,
				TomcatServletWebServerAutoConfiguration.class, ServerProperties.class,
				PropertyPlaceholderAutoConfiguration.class)
			.properties("server.port=-1")
			.run();
		PropertyPathEndpoint endpoint = context.getBean(PropertyPathEndpoint.class);
		assertThat(((Collection<?>) ReflectionTestUtils.getField(ReflectionTestUtils.getField(endpoint, "extractor"),
				"extractors")))
			.hasSize(8);
		context.close();
	}

	@Test
	public void testHttpPropertyPathNotifier() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(BusConfig.class,
				DiscoveryClientConfig.class, EnvironmentMonitorAutoConfiguration.class,
				TomcatServletWebServerAutoConfiguration.class, ServerProperties.class,
				PropertyPlaceholderAutoConfiguration.class)
			.properties("server.port=-1", "spring.cloud.config.server.monitor.http.enabled=true")
			.run();

		assertThat(context.getBeansOfType(HttpPropertyPathNotifier.class)).hasSize(1);

		context.close();
	}

	@Configuration(proxyBeanMethods = false)
	static class BusConfig {

		@Bean
		public BusProperties busProperties() {
			return new BusProperties();
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomPropertyPathNotificationExtractorConfig {

		@Bean
		public PropertyPathNotificationExtractor customNotificationExtractor() {
			return (headers, payload) -> {
				throw new UnsupportedOperationException("doesn't do anything");
			};
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class DiscoveryClientConfig {

		@Bean
		public DiscoveryClient discoveryClient() {
			return new DiscoveryClient() {

				@Override
				public String description() {
					return "test";
				}

				@Override
				public List<ServiceInstance> getInstances(String serviceId) {
					return Collections.emptyList();
				}

				@Override
				public List<String> getServices() {
					return Collections.emptyList();
				}

			};

		}

		@Bean
		public RestClient.Builder restClientBuilder() {
			return RestClient.builder();
		}

	}

}
