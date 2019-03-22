/*
 * Copyright 2018-2019 the original author or authors.
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

import org.junit.Test;

import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ryan Baxter
 */
public class NativeEnvironmentRepositoryFactoryTest {

	@Test
	public void testDefaultLabel() {
		ConfigServerProperties props = new ConfigServerProperties();
		props.setDefaultLabel("mylabel");
		NativeEnvironmentRepositoryFactory factory = new NativeEnvironmentRepositoryFactory(
				new StandardEnvironment(), props);
		NativeEnvironmentProperties environmentProperties = new NativeEnvironmentProperties();
		NativeEnvironmentRepository repo = factory.build(environmentProperties);
		assertThat(repo.getDefaultLabel()).isEqualTo("mylabel");

		factory = new NativeEnvironmentRepositoryFactory(new StandardEnvironment(),
				props);
		environmentProperties = new NativeEnvironmentProperties();
		environmentProperties.setDefaultLabel("mynewlabel");
		repo = factory.build(environmentProperties);
		assertThat(repo.getDefaultLabel()).isEqualTo("mylabel");

		factory = new NativeEnvironmentRepositoryFactory(new StandardEnvironment(),
				new ConfigServerProperties());
		environmentProperties = new NativeEnvironmentProperties();
		environmentProperties.setDefaultLabel("mynewlabel");
		repo = factory.build(environmentProperties);
		assertThat(repo.getDefaultLabel()).isEqualTo("mynewlabel");
	}

}
