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

import static org.junit.Assert.assertNotNull;

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
public class GenericResourceRepositoryTests {

	private GenericResourceRepository repository;
	private ConfigurableApplicationContext context;
	private NativeEnvironmentRepository nativeRepository;

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
		this.nativeRepository = new NativeEnvironmentRepository(this.context.getEnvironment());
		this.repository = new GenericResourceRepository(
				this.nativeRepository);
		this.repository.setResourceLoader(this.context);
		this.context.close();
	}

	@Test
	public void locateResource() {
		assertNotNull(this.repository.findOne("blah", "default", "master", "foo.properties"));
	}

	@Test
	public void locateProfiledResource() {
		assertNotNull(this.repository.findOne("blah", "local", "master", "foo.txt"));
	}

	@Test
	public void locateProfiledResourceWithPlaceholder() {
		this.nativeRepository.setSearchLocations("classpath:/test/{profile}");
		assertNotNull(this.repository.findOne("blah", "local", "master", "foo.txt"));
	}

	@Test(expected=NoSuchResourceException.class)
	public void locateMissingResource() {
		assertNotNull(this.repository.findOne("blah", "default", "master", "foo.txt"));
	}

}
