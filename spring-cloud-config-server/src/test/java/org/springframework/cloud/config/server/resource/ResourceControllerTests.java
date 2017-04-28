/*
 * Copyright 2015-2016 the original author or authors.
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

package org.springframework.cloud.config.server.resource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.config.server.environment.NativeEnvironmentRepository;
import org.springframework.cloud.config.server.environment.NativeEnvironmentRepositoryTests;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * @author Dave Syer
 *
 */
public class ResourceControllerTests {

	private ResourceController controller;
	private GenericResourceRepository repository;
	private ConfigurableApplicationContext context;
	private NativeEnvironmentRepository environmentRepository;

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Before
	public void init() {
		this.context = new SpringApplicationBuilder(
				NativeEnvironmentRepositoryTests.class).web(false).run();
		this.environmentRepository = new NativeEnvironmentRepository(
				this.context.getEnvironment());
		this.repository = new GenericResourceRepository(this.environmentRepository);
		this.repository.setResourceLoader(this.context);
		this.controller = new ResourceController(this.repository,
				this.environmentRepository);
		this.context.close();
	}

	@Test
	public void templateReplacement() throws Exception {
		this.environmentRepository.setSearchLocations("classpath:/test");
		String resource = this.controller.retrieve("foo", "bar", "dev", "template.json", true);
		assertTrue("Wrong content: " + resource, resource.matches("\\{\\s*\"foo\": \"dev_bar\"\\s*\\}"));
	}
	
	@Test
	public void templateReplacementNotForResolvePlaceholdersFalse() throws Exception {
		this.environmentRepository.setSearchLocations("classpath:/test");
		String resource = this.controller.retrieve("foo", "bar", "dev", "template.json", false);
		assertTrue("Wrong content: " + resource, resource.matches("\\{\\s*\"foo\": \"\\$\\{foo\\}\"\\s*\\}"));
	}

	@Test
	public void templateReplacementNotForBinary() throws Exception {
		this.environmentRepository.setSearchLocations("classpath:/test");
		String resource = new String(this.controller.binary("foo", "bar", "dev", "template.json"));
		assertTrue("Wrong content: " + resource, resource.matches("\\{\\s*\"foo\": \"\\$\\{foo\\}\"\\s*\\}"));
	}

	@Test
	public void escapedPlaceholder() throws Exception {
		this.environmentRepository.setSearchLocations("classpath:/test");
		String resource = this.controller.retrieve("foo", "bar", "dev", "placeholder.txt", true);
		assertEquals("foo: ${foo}", resource);
	}

	@Test
	public void labelWithSlash() throws Exception {
		this.environmentRepository.setSearchLocations("classpath:/test");
		String resource = this.controller.retrieve("foo", "bar", "dev(_)spam", "foo.txt", true);
		assertEquals("foo: dev_bar/spam", resource);
	}

	@Test
	public void resourceWithSlash() throws Exception {
		this.environmentRepository.setSearchLocations("classpath:/test");
		String resource = this.controller.retrieve("foo", "bar", "dev", "spam/foo.txt", true);
		assertEquals("foo: dev_bar/spam", resource);
	}

	@Test
	public void resourceWithSlashRequest() throws Exception {
		this.environmentRepository.setSearchLocations("classpath:/test");
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRequestURI("/foo/bar/dev/" + "spam/foo.txt");
		String resource = this.controller.retrieve("foo", "bar", "dev", request, true);
		assertEquals("foo: dev_bar/spam", resource);
	}

	@Test
	public void resourceWithSlashRequestAndServletPath() throws Exception {
		this.environmentRepository.setSearchLocations("classpath:/test");
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setServletPath("/spring");
		request.setRequestURI("/foo/bar/dev/" + "spam/foo.txt");
		String resource = this.controller.retrieve("foo", "bar", "dev", request, true);
		assertEquals("foo: dev_bar/spam", resource);
	}

	@Test
	public void labelWithSlashForResolvePlaceholdersFalse() throws Exception {
		this.environmentRepository.setSearchLocations("classpath:/test");
		String resource = this.controller.retrieve("foo", "bar", "dev(_)spam", "foo.txt", false);
		assertEquals("foo: dev_bar/spam", resource);
	}

	@Test
	public void resourceWithSlashForResolvePlaceholdersFalse() throws Exception {
		this.environmentRepository.setSearchLocations("classpath:/test");
		String resource = this.controller.retrieve("foo", "bar", "dev", "spam/foo.txt", false);
		assertEquals("foo: dev_bar/spam", resource);
	}

	@Test
	public void resourceWithSlashForResolvePlaceholdersFalseRequest() throws Exception {
		this.environmentRepository.setSearchLocations("classpath:/test");
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRequestURI("/foo/bar/dev/" + "spam/foo.txt");
		String resource = this.controller.retrieve("foo", "bar", "dev", request, false);
		assertEquals("foo: dev_bar/spam", resource);
	}
	
	@Test
	public void labelWithSlashForBinary() throws Exception {
		this.environmentRepository.setSearchLocations("classpath:/test");
		byte[] resource = this.controller.binary("foo", "bar", "dev(_)spam", "foo.txt");
		assertEquals("foo: dev_bar/spam", new String(resource));
	}

	@Test
	public void resourceWithSlashForBinary() throws Exception {
		this.environmentRepository.setSearchLocations("classpath:/test");
		byte[] resource = this.controller.binary("foo", "bar", "dev", "spam/foo.txt");
		assertEquals("foo: dev_bar/spam", new String(resource));
	}

	@Test
	public void resourceWithSlashForBinaryRequest() throws Exception {
		this.environmentRepository.setSearchLocations("classpath:/test");
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRequestURI("/foo/bar/dev/" + "spam/foo.txt");
		byte[] resource = this.controller.binary("foo", "bar", "dev", request );
		assertEquals("foo: dev_bar/spam", new String(resource));
	}

}
