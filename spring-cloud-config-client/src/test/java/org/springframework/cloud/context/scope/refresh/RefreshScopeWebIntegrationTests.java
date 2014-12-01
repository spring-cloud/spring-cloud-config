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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.environment.EnvironmentManager;
import org.springframework.cloud.context.scope.refresh.RefreshScopeWebIntegrationTests.Application;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Dave Syer
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
public class RefreshScopeWebIntegrationTests {

	@Autowired
	private org.springframework.cloud.context.scope.refresh.RefreshScope scope;
	
	@Autowired
	private EnvironmentManager environmentManager;
	
	@Autowired
	private Client application;
	
	@Autowired
	private ConfigurableListableBeanFactory beanFactory;
	
	@Test
	public void scopeOnBeanDefinition() throws Exception {
		assertEquals("refresh", beanFactory.getBeanDefinition("scopedTarget.application").getScope());
	}

	@Test
	public void beanAccess() throws Exception {
		application.hello();
		environmentManager.setProperty("message", "Hello Dave!");
		scope.refreshAll();
		String message = application.hello();
		assertEquals("Hello Dave!", message);
	}
	
	@Configuration
	@EnableAutoConfiguration
	protected static class Application {
		
		@Bean
		@RefreshScope
		public Client application() {
			return new Client();
		}
		
		public static void main(String[] args) {
			SpringApplication.run(Application.class, args);
		}

	}

	@RestController
	protected static class Client {

		@Value("${message:Hello World!}")
		String message;

		@RequestMapping("/")
		public String hello() {
			return message;
		}

	}

}
