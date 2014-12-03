/*
 * Copyright 2006-2007 the original author or authors.
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
package org.springframework.cloud.context.scope.refresh;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.scope.refresh.ImportRefreshScopeIntegrationTests.TestConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@SpringApplicationConfiguration(classes = TestConfiguration.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class ImportRefreshScopeIntegrationTests {
	
	@Autowired
	private ConfigurableListableBeanFactory beanFactory;

	@Autowired
	private ExampleService service;

	@Autowired
	private org.springframework.cloud.context.scope.refresh.RefreshScope scope;

	@Test
	public void testSimpleProperties() throws Exception {
		assertEquals("Hello scope!", service.getMessage());
		assertEquals("refresh", beanFactory.getBeanDefinition("scopedTarget.service").getScope());
		assertEquals("Hello scope!", service.getMessage());
	}

	@Configuration("service")
	@RefreshScope
	public static class ExampleService {

		public String getMessage() {
			return "Hello scope!";
		}

	}

	@Configuration
	@Import({ RefreshAutoConfiguration.class, ExampleService.class })
	protected static class TestConfiguration {
	}

}
