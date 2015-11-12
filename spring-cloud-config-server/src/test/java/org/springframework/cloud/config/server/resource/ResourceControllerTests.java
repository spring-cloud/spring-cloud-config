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

package org.springframework.cloud.config.server.resource;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.config.server.environment.NativeEnvironmentRepository;
import org.springframework.cloud.config.server.environment.NativeEnvironmentRepositoryTests;
import org.springframework.context.ConfigurableApplicationContext;

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
		String resource = this.controller.resolve("foo", "bar", "dev", "template.json");
		assertEquals("{\n  \"foo\": \"dev_bar\"\n}", resource);
	}

	@Test
	public void templateReplacementNotForBinary() throws Exception {
		this.environmentRepository.setSearchLocations("classpath:/test");
		String resource = new String(this.controller.binary("foo", "bar", "dev", "template.json"));
		assertEquals("{\n  \"foo\": \"${foo}\"\n}", resource);
	}

	@Test
	public void escapedPlaceholder() throws Exception {
		this.environmentRepository.setSearchLocations("classpath:/test");
		String resource = this.controller.resolve("foo", "bar", "dev", "placeholder.txt");
		assertEquals("foo: ${foo}", resource);
	}

	@Test
	public void labelWithSlash() throws Exception {
		this.environmentRepository.setSearchLocations("classpath:/test");
		String resource = this.controller.resolve("foo", "bar", "dev(_)spam", "foo.txt");
		assertEquals("foo: dev_bar/spam", resource);
	}

	@Test
	public void labelWithSlashForBinary() throws Exception {
		this.environmentRepository.setSearchLocations("classpath:/test");
		byte[] resource = this.controller.binary("foo", "bar", "dev(_)spam", "foo.txt");
		assertEquals("foo: dev_bar/spam", new String(resource));
	}

}
