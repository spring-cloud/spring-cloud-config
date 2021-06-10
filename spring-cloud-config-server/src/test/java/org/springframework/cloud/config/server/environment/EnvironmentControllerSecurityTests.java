/*
 * Copyright 2014-2019 the original author or authors.
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

package org.springframework.cloud.config.server.environment;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.ConfigServerApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * @author ian
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
		classes = { EnvironmentControllerSecurityTests.ControllerConfiguration.class, ConfigServerApplication.class },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
		properties = { "spring.cloud.config.server.prefix=/", "spring.cloud.config.server.security.enabled=true" })
public class EnvironmentControllerSecurityTests {

	@Autowired
	private WebApplicationContext context;

	@LocalServerPort
	private int port;

	@Autowired
	@Qualifier("mockEnvironmentRepository")
	private EnvironmentRepository repository;

	private Environment environment = new Environment("foo", "default");

	@Before
	public void init() {
		Mockito.reset(this.repository);
		Mockito.when(repository.findOne(anyString(), anyString(), anyString(), anyBoolean()))
				.thenReturn(this.environment);
		this.environment.add(new PropertySource("foo", new HashMap<>()));
	}

	@Test
	public void propertiesLabel() throws Exception {
		ResponseEntity<String> notLogin = new TestRestTemplate()
				.getForEntity("http://localhost:" + port + "/dev/foo-default.properties", String.class);
		assertThat(notLogin).isNotNull();
		// Need login
		assertThat(notLogin.getStatusCode()).isEqualTo(HttpStatus.FOUND);

		// Not authorized
		ResponseEntity<String> notAuthorized = new TestRestTemplate("user", "passwd")
				.getForEntity("http://localhost:" + port + "/dev/foo-default.properties", String.class);
		assertThat(notAuthorized.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		// Authorized by label level
		ResponseEntity<String> authorized = new TestRestTemplate("user1", "passwd")
				.getForEntity("http://localhost:" + port + "/dev/foo-default.properties", String.class);
		assertThat(authorized.getStatusCode()).isEqualTo(HttpStatus.OK);

		// Authorized by label>application level
		authorized = new TestRestTemplate("user2", "passwd")
				.getForEntity("http://localhost:" + port + "/dev/foo-default.properties", String.class);
		assertThat(authorized.getStatusCode()).isEqualTo(HttpStatus.OK);

		// Label prod is not authorized
		notAuthorized = new TestRestTemplate("user1", "passwd")
				.getForEntity("http://localhost:" + port + "/prod/foo-default.properties", String.class);
		assertThat(notAuthorized.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		// Application foo2 is not authorized
		notAuthorized = new TestRestTemplate("user2", "passwd")
				.getForEntity("http://localhost:" + port + "/dev/foo2-default.properties", String.class);
		assertThat(notAuthorized.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Configuration
	@EnableGlobalMethodSecurity(prePostEnabled = true)
	@Import(PropertyPlaceholderAutoConfiguration.class)
	public static class ControllerConfiguration {

		@Bean("mockEnvironmentRepository")
		public EnvironmentRepository environmentRepository() {
			EnvironmentRepository repository = Mockito.mock(EnvironmentRepository.class);
			return repository;
		}

		@Bean
		public UserDetailsService userDetailsService() {
			User notAuthorized = new User("user", "{noop}passwd", Collections.emptyList());
			User labelLevel = new User("user1", "{noop}passwd", make("ENVIRONMENT_DEV"));
			User applicationLevel = new User("user2", "{noop}passwd", make("ENVIRONMENT_DEV_FOO"));
			return new InMemoryUserDetailsManager(notAuthorized, labelLevel, applicationLevel);
		}

		private GrantedAuthority grant(String authority) {
			return new SimpleGrantedAuthority(authority);
		}

		private Collection<? extends GrantedAuthority> make(String... authorities) {
			return Arrays.stream(authorities).map(this::grant).collect(Collectors.toList());
		}

	}

}
