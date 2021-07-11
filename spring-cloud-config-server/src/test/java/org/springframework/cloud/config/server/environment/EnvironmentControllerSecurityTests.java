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

import java.nio.charset.StandardCharsets;
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
import org.springframework.cloud.config.server.encryption.SingleTextEncryptorLocator;
import org.springframework.cloud.config.server.resource.ResourceRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.encrypt.Encryptors;
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

	@Autowired
	private ResourceRepository resources;

	private Environment environment = new Environment("foo", "default");

	@Before
	public void init() {
		Mockito.reset(this.repository, this.resources);
		Mockito.when(repository.findOne(anyString(), anyString(), anyString(), anyBoolean()))
				.thenReturn(this.environment);
		this.environment.add(new PropertySource("foo", new HashMap<>()));

		Mockito.when(resources.findOne(anyString(), anyString(), anyString(), anyString()))
				.thenReturn(new ByteArrayResource("Hello World!".getBytes(StandardCharsets.UTF_8), "a.txt"));
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

	@Test
	public void resourceLabel() {
		// Not authorized
		ResponseEntity<String> notAuthorized = new TestRestTemplate("user", "passwd")
				.getForEntity("http://localhost:" + port + "/foo/default/dev/a.txt", String.class);
		assertThat(notAuthorized.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

		// Authorized by label level
		ResponseEntity<String> authorized = new TestRestTemplate("user1", "passwd")
				.getForEntity("http://localhost:" + port + "/foo/default/dev/a.txt", String.class);
		assertThat(authorized.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(authorized.getBody()).isEqualTo("Hello World!");
	}

	@Test
	public void encryptDecrypt() {
		// Authorized by application and method level
		ResponseEntity<String> authorized = new TestRestTemplate("user1", "passwd").postForEntity(
				"http://localhost:" + port + "/decrypt/foo/default",
				RequestEntity.post("").contentType(MediaType.TEXT_PLAIN)
						.body("933f9cfe62b0eb9b34c6f96dbdb8190976d5d73322e29c56f809de8d79a1cb4d"),
				String.class);
		assertThat(authorized.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(authorized.getBody()).isEqualTo("secret");

		authorized = new TestRestTemplate("user1", "passwd").postForEntity(
				"http://localhost:" + port + "/encrypt/foo/default",
				RequestEntity.post("").contentType(MediaType.TEXT_PLAIN).body("secret"), String.class);
		assertThat(authorized.getStatusCode()).isEqualTo(HttpStatus.OK);
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
			User labelLevel = new User("user1", "{noop}passwd", make("ENVIRONMENT_DEV", "RESOURCE_DEV",
					"ENCRYPTOR_FOO_NULL_ENCRYPT", "ENCRYPTOR_FOO_NULL_DECRYPT"));
			User applicationLevel = new User("user2", "{noop}passwd", make("ENVIRONMENT_DEV_FOO", "RESOURCE_DEV_FOO"));
			return new InMemoryUserDetailsManager(notAuthorized, labelLevel, applicationLevel);
		}

		@Bean
		public ResourceRepository resourceRepository() {
			ResourceRepository repository = Mockito.mock(ResourceRepository.class);
			return repository;
		}

		@Bean
		public WebSecurityConfigurerAdapter webSecurityConfigurerAdapter() {
			return new WebSecurityConfigurerAdapter() {
				@Override
				protected void configure(HttpSecurity http) throws Exception {
					http.csrf().disable();
					http.httpBasic();
					http.formLogin();
					http.antMatcher("/**").authorizeRequests().anyRequest().authenticated();
				}
			};
		}

		@Bean
		public SingleTextEncryptorLocator singleTextEncryptorLocator() {
			return new SingleTextEncryptorLocator(Encryptors.text("pwd", "6173746c"));
		}

		private GrantedAuthority grant(String authority) {
			return new SimpleGrantedAuthority(authority);
		}

		private Collection<? extends GrantedAuthority> make(String... authorities) {
			return Arrays.stream(authorities).map(this::grant).collect(Collectors.toList());
		}

	}

}
