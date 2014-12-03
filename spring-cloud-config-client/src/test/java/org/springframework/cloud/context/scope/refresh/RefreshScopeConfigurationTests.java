/*
 * Copyright 2013-2014 the original author or authors.
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

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.PropertyPlaceholderAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.environment.EnvironmentManager;
import org.springframework.cloud.context.scope.refresh.RefreshScopeConfigurationTests.NestedApp.NestedController;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Dave Syer
 *
 */
public class RefreshScopeConfigurationTests {
	
	private AnnotationConfigApplicationContext context;
	
	@Rule
	public ExpectedException expected = ExpectedException.none();

	@After
	public void init() {
		if (context!=null) {
			context.close();
		}
	}

	private void refresh() {
		EnvironmentManager environmentManager = context.getBean(EnvironmentManager.class);
		environmentManager.setProperty("message", "Hello Dave!");
		org.springframework.cloud.context.scope.refresh.RefreshScope scope = context.getBean(org.springframework.cloud.context.scope.refresh.RefreshScope.class);
		scope.refreshAll();
	}

	/**
	 * See gh-43
	 */
	@Test
	public void configurationWithRefreshScope() throws Exception {
		context = new AnnotationConfigApplicationContext(Application.class,
				PropertyPlaceholderAutoConfiguration.class, RefreshAutoConfiguration.class);
		Application application = context.getBean(Application.class);
		assertEquals("refresh", context.getBeanDefinition("scopedTarget.application").getScope());
		application.hello();
		refresh();
		String message = application.hello();
		assertEquals("Hello Dave!", message);
	}

	@Test
	public void refreshScopeOnBean() throws Exception {
		context = new AnnotationConfigApplicationContext(ClientApp.class,
				PropertyPlaceholderAutoConfiguration.class, RefreshAutoConfiguration.class);
		Controller application = context.getBean(Controller.class);
		application.hello();
		refresh();
		String message = application.hello();
		assertEquals("Hello Dave!", message);
	}

	@Test
	public void refreshScopeOnNested() throws Exception {
		context = new AnnotationConfigApplicationContext(NestedApp.class,
				PropertyPlaceholderAutoConfiguration.class, RefreshAutoConfiguration.class);
		NestedController application = context.getBean(NestedController.class);
		application.hello();
		refresh();
		String message = application.hello();
		assertEquals("Hello Dave!", message);
	}

	// WTF? Maven can't compile without the FQN on this one (not the others).
	@org.springframework.context.annotation.Configuration
	protected static class NestedApp {
		
		@RestController
		@RefreshScope
		protected static class NestedController {

			@Value("${message:Hello World!}")
			String message;

			@RequestMapping("/")
			public String hello() {
				return message;
			}

		}

		public static void main(String[] args) {
			SpringApplication.run(ClientApp.class, args);
		}

	}

	@Configuration("application")
	@RefreshScope
	protected static class Application {

		@Value("${message:Hello World!}")
		String message = "Hello World";

		@RequestMapping("/")
		public String hello() {
			return message;
		}

		public static void main(String[] args) {
			SpringApplication.run(Application.class, args);
		}

	}

	@Configuration
	protected static class ClientApp {
		
		@Bean
		@RefreshScope
		public Controller controller() {
			return new Controller();
		}

		public static void main(String[] args) {
			SpringApplication.run(ClientApp.class, args);
		}

	}

	@RestController
	protected static class Controller {

		@Value("${message:Hello World!}")
		String message;

		@RequestMapping("/")
		public String hello() {
			return message;
		}

	}

}
