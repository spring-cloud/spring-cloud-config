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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.TestRestTemplate;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.scope.refresh.RefreshEndpointIntegrationTests.ClientApp;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Dave Syer
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ClientApp.class)
@IntegrationTest("server.port:0")
@WebAppConfiguration
public class RefreshEndpointIntegrationTests {

	@Value("${local.server.port}")
	private int port;

	@Test
	public void webAccess() throws Exception {
		TestRestTemplate template = new TestRestTemplate();
		template.exchange(
				getUrlEncodedEntity("http://localhost:" + port + "/env", "message",
						"Hello Dave!"), String.class);
		template.postForObject("http://localhost:" + port + "/refresh", "", String.class);
		String message = template.getForObject("http://localhost:" + port + "/",
				String.class);
		assertEquals("Hello Dave!", message);
	}

	private RequestEntity<?> getUrlEncodedEntity(String uri, String key, String value)
			throws URISyntaxException {
		MultiValueMap<String, String> env = new LinkedMultiValueMap<String, String>(
				Collections.singletonMap("message", Arrays.asList("Hello Dave!")));
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		RequestEntity<MultiValueMap<String, String>> entity = new RequestEntity<MultiValueMap<String, String>>(
				env, headers, HttpMethod.POST, new URI(uri));
		return entity;
	}

	@Configuration
	@EnableAutoConfiguration
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
