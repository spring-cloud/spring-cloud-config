/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.config.monitor;

import static org.junit.Assert.assertEquals;

import java.util.Collection;
import java.util.Map;

import org.junit.Test;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.web.EmbeddedServletContainerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.ServerPropertiesAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;

/**
 * @author Dave Syer
 *
 */
public class EnvironmentMonitorAutoConfigurationTests {

	@Test
	public void test() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(
				EnvironmentMonitorAutoConfiguration.class,
				EmbeddedServletContainerAutoConfiguration.class, ServerPropertiesAutoConfiguration.class,
				PropertyPlaceholderAutoConfiguration.class).properties("server.port=-1")
						.run();
		PropertyPathEndpoint endpoint = context.getBean(PropertyPathEndpoint.class);
		assertEquals(4,
				((Collection<?>) ReflectionTestUtils.getField(
						ReflectionTestUtils.getField(endpoint, "extractor"),
						"extractors")).size());
		context.close();
	}
        
        @Test
	public void testCanAddCustomPropertyPathNotificationExtractor() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(
                                CustomPropertyPathNotificationExtractorConfig.class,
				EnvironmentMonitorAutoConfiguration.class,
				EmbeddedServletContainerAutoConfiguration.class, ServerPropertiesAutoConfiguration.class,
				PropertyPlaceholderAutoConfiguration.class).properties("server.port=-1")
						.run();
		PropertyPathEndpoint endpoint = context.getBean(PropertyPathEndpoint.class);
		assertEquals(5,
				((Collection<?>) ReflectionTestUtils.getField(
						ReflectionTestUtils.getField(endpoint, "extractor"),
						"extractors")).size());
		context.close();
	}
        
        @Configuration
        static class CustomPropertyPathNotificationExtractorConfig {
                @Bean
                public PropertyPathNotificationExtractor customNotificationExtractor() {
                        return new PropertyPathNotificationExtractor() {
                                @Override
                                public PropertyPathNotification extract(MultiValueMap<String, String> headers, Map<String, Object> payload) {
                                    throw new UnsupportedOperationException("doesn't do anything");
                                }
                        };
                }
        }

}
