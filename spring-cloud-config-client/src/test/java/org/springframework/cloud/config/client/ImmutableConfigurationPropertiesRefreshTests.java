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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.aop.framework.Advised;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImmutableConfigurationPropertiesRefreshTests {

	@Test
	void refreshReplacesImmutableTargetWithoutChangingPreviousInstance() throws Exception {
		Map<String, Object> source = new HashMap<>();
		source.put("config.app.map.message", "before");
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestConfiguration.class)
			.web(WebApplicationType.NONE)
			.properties("spring.cloud.config.enabled=false", "spring.cloud.bootstrap.enabled=false")
			.initializers(applicationContext -> applicationContext.getEnvironment()
				.getPropertySources()
				.addFirst(new MapPropertySource("refreshTest", source)))
			.run()) {
			ImmutableProperties properties = context.getBean(ImmutableProperties.class);
			ImmutableProperties original = (ImmutableProperties) ((Advised) properties).getTargetSource().getTarget();
			Map<String, String> originalMap = properties.getMap();
			assertThat(originalMap).containsExactlyEntriesOf(Map.of("message", "before"));
			assertThatThrownBy(() -> originalMap.put("message", "mutated"))
				.isInstanceOf(UnsupportedOperationException.class);

			source.put("config.app.map.message", "after");
			context.getBean(ContextRefresher.class).refresh();

			assertThat(context.getBean(ImmutableProperties.class)).isSameAs(properties);
			assertThat(properties.getMap()).containsExactlyEntriesOf(Map.of("message", "after"));
			assertThat(((Advised) properties).getTargetSource().getTarget()).isNotSameAs(original);
			assertThat(original.getMap()).containsExactlyEntriesOf(Map.of("message", "before"));
			assertThatThrownBy(() -> properties.getMap().clear()).isInstanceOf(UnsupportedOperationException.class);
		}
	}

	@Test
	void immutablePropertiesWithoutRefreshScopeKeepTheirOriginalValues() {
		Map<String, Object> source = new HashMap<>();
		source.put("config.fixed.message", "before");
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestConfiguration.class)
			.web(WebApplicationType.NONE)
			.properties("spring.cloud.config.enabled=false", "spring.cloud.bootstrap.enabled=false")
			.initializers(applicationContext -> applicationContext.getEnvironment()
				.getPropertySources()
				.addFirst(new MapPropertySource("refreshTest", source)))
			.run()) {
			FixedProperties properties = context.getBean(FixedProperties.class);
			assertThat(properties.message()).isEqualTo("before");

			source.put("config.fixed.message", "after");
			context.getBean(ContextRefresher.class).refresh();

			assertThat(context.getEnvironment().getProperty("config.fixed.message")).isEqualTo("after");
			assertThat(context.getBean(FixedProperties.class)).isSameAs(properties);
			assertThat(properties.message()).isEqualTo("before");
		}
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	@EnableConfigurationProperties({ ImmutableProperties.class, FixedProperties.class })
	static class TestConfiguration {

	}

	@RefreshScope
	@ConfigurationProperties("config.app")
	public static class ImmutableProperties {

		private final Map<String, String> map;

		@ConstructorBinding
		ImmutableProperties(Map<String, String> map) {
			this.map = map != null ? Map.copyOf(map) : Map.of();
		}

		public Map<String, String> getMap() {
			return this.map;
		}

	}

	@ConfigurationProperties("config.fixed")
	record FixedProperties(String message) {

	}

}
