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

package org.springframework.platform.context.environment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.servlet.ServletException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.platform.context.environment.EnvironmentManagerIntegrationTests.TestConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringApplicationConfiguration(classes = TestConfiguration.class)
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
public class EnvironmentManagerIntegrationTests {

	@Autowired
	private TestProperties properties;

	@Autowired
	private WebApplicationContext context;

	private MockMvc mvc;

	@Before
	public void setUp() {
		this.mvc = MockMvcBuilders.webAppContextSetup(this.context).build();
	}

	@Test
	public void testRefresh() throws Exception {
		assertEquals("Hello scope!", properties.getMessage());
		// Change the dynamic property source...
		this.mvc.perform(post("/env").param("message", "Foo")).andExpect(status().isOk()).andExpect(
				content().string("{\"message\":\"Foo\"}"));
		assertEquals("Foo", properties.getMessage());
	}

	@Test
	public void testRefreshFails() throws Exception {
		try {
			this.mvc.perform(post("/env").param("delay", "foo")).andExpect(
					status().is5xxServerError());
			fail("expected ServletException");
		} catch (ServletException e) {
			// The underlying BindException is not handled by the dispatcher servlet
		}
		assertEquals(0, properties.getDelay());
	}

	public static void main(String[] args) {
		SpringApplication.run(TestConfiguration.class, args);
	}

	@Configuration
	@EnableAutoConfiguration
	protected static class TestConfiguration {

		@Bean
		protected TestProperties properties() {
			return new TestProperties();
		}

	}

	@ConfigurationProperties
	protected static class TestProperties {

		private String message;

		private int delay;

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

		public int getDelay() {
			return delay;
		}

		public void setDelay(int delay) {
			this.delay = delay;
		}
	}

}
