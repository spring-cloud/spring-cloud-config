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
package org.springframework.platform.context.scope.refresh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.platform.autoconfigure.RefreshAutoConfiguration;
import org.springframework.platform.context.config.annotation.RefreshScope;
import org.springframework.platform.context.scope.refresh.RefreshScopeIntegrationTests.TestConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@SpringApplicationConfiguration(classes=TestConfiguration.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class RefreshScopeIntegrationTests {

	@Autowired
	private Service service;

	@Autowired
	private TestProperties properties;

	@Autowired
	private org.springframework.platform.context.scope.refresh.RefreshScope scope;

	@Before
	public void init() {
		ExampleService.reset();
	}

	@Test
	@DirtiesContext
	public void testSimpleProperties() throws Exception {
		assertEquals("Hello scope!", service.getMessage());
		assertTrue(service instanceof Advised);
		// Change the dynamic property source...
		properties.setMessage("Foo");
		// ...but don't refresh, so the bean stays the same:
		assertEquals("Hello scope!", service.getMessage());
		assertEquals(1, ExampleService.getInitCount());
		assertEquals(0, ExampleService.getDestroyCount());
	}

	@Test
	@DirtiesContext
	public void testRefresh() throws Exception {
		assertEquals("Hello scope!", service.getMessage());
		String id1 = service.toString();
		// Change the dynamic property source...
		properties.setMessage("Foo");
		// ...and then refresh, so the bean is re-initialized:
		scope.refreshAll();
		String id2 = service.toString();
		assertEquals("Foo", service.getMessage());
		assertEquals(2, ExampleService.getInitCount());
		assertEquals(1, ExampleService.getDestroyCount());
		assertNotSame(id1, id2);
	}

	@Test
	@DirtiesContext
	public void testRefreshBean() throws Exception {
		assertEquals("Hello scope!", service.getMessage());
		String id1 = service.toString();
		// Change the dynamic property source...
		properties.setMessage("Foo");
		// ...and then refresh, so the bean is re-initialized:
		scope.refresh("service");
		String id2 = service.toString();
		assertEquals("Foo", service.getMessage());
		assertEquals(2, ExampleService.getInitCount());
		assertEquals(1, ExampleService.getDestroyCount());
		assertNotSame(id1, id2);
	}

	public static interface Service {

		String getMessage();

	}

	public static class ExampleService implements Service, InitializingBean, DisposableBean {

		private static Log logger = LogFactory.getLog(ExampleService.class);

		private volatile static int initCount = 0;
		private volatile static int destroyCount = 0;

		private String message = null;
		private volatile long delay = 0;

		public void setDelay(long delay) {
			this.delay = delay;
		}

		public void afterPropertiesSet() throws Exception {
			logger.debug("Initializing message: " + message);
			initCount++;
		}

		public void destroy() throws Exception {
			logger.debug("Destroying message: " + message);
			destroyCount++;
			message = null;
		}

		public static void reset() {
			initCount = 0;
			destroyCount = 0;
		}

		public static int getInitCount() {
			return initCount;
		}

		public static int getDestroyCount() {
			return destroyCount;
		}

		public void setMessage(String message) {
			logger.debug("Setting message: " + message);
			this.message = message;
		}

		public String getMessage() {
			logger.debug("Getting message: " + message);
			try {
				Thread.sleep(delay);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			logger.info("Returning message: " + message);
			return message;
		}

	}
	
	@Configuration
	@EnableConfigurationProperties(TestProperties.class)
	@Import({RefreshAutoConfiguration.class, PropertyPlaceholderAutoConfiguration.class})
	protected static class TestConfiguration {
		
		@Autowired
		private TestProperties properties;
		
		@Bean
		@RefreshScope
		public ExampleService service() {
			ExampleService service = new ExampleService();
			service.setMessage(properties.getMessage());
			service.setDelay(properties.getDelay());
			return service;
		}
		
	}

	@ConfigurationProperties
	@ManagedResource
	protected static class TestProperties {
		private String message;
		private int delay;
		@ManagedAttribute
		public String getMessage() {
			return message;
		}
		public void setMessage(String message) {
			this.message = message;
		}
		@ManagedAttribute
		public int getDelay() {
			return delay;
		}
		public void setDelay(int delay) {
			this.delay = delay;
		}
	}
	
}
