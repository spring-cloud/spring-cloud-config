/*
 * Copyright 2013-2015 the original author or authors.
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
package org.springframework.cloud.config.server.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.config.server.ConfigServerProperties;
import org.springframework.cloud.config.server.EnvironmentController;
import org.springframework.cloud.config.server.EnvironmentEncryptorEnvironmentRepository;
import org.springframework.cloud.config.server.EnvironmentRepository;
import org.springframework.cloud.config.server.ResourceController;
import org.springframework.cloud.config.server.ResourceRepository;
import org.springframework.cloud.config.server.encryption.EnvironmentEncryptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Dave Syer
 * @author Roy Clarkson
 */
@Configuration
@ConditionalOnWebApplication
public class ConfigServerMvcConfiguration {
	@Autowired
	private EnvironmentRepository repository;

	@Autowired
	private ResourceRepository resources;

	@Autowired
	private ConfigServerProperties server;

	@Autowired(required=false)
	private EnvironmentEncryptor environmentEncryptor;

	@Bean
	public EnvironmentController environmentController() {
		EnvironmentController controller = new EnvironmentController(encrypted());
		controller.setStripDocumentFromYaml(this.server.isStripDocumentFromYaml());
		return controller;
	}

	@Bean
	public ResourceController resourceController() {
		ResourceController controller = new ResourceController(this.resources, encrypted());
		return controller;
	}

	private EnvironmentEncryptorEnvironmentRepository encrypted() {
		EnvironmentEncryptorEnvironmentRepository encrypted = new EnvironmentEncryptorEnvironmentRepository(this.repository, this.environmentEncryptor);
		encrypted.setOverrides(this.server.getOverrides());
		return encrypted;
	}
}