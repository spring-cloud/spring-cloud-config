/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.config.server.config;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.config.server.encryption.EnvironmentEncryptor;
import org.springframework.cloud.config.server.encryption.ResourceEncryptor;
import org.springframework.cloud.config.server.environment.EnvironmentController;
import org.springframework.cloud.config.server.environment.EnvironmentEncryptorEnvironmentRepository;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.resource.ResourceController;
import org.springframework.cloud.config.server.resource.ResourceRepository;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @author Dave Syer
 * @author Roy Clarkson
 * @author Tim Ysewyn
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
public class ConfigServerMvcConfiguration implements WebMvcConfigurer {

	@Autowired(required = false)
	private EnvironmentEncryptor environmentEncryptor;

	@Autowired(required = false)
	private ObjectMapper objectMapper = new ObjectMapper();

	@Autowired(required = false)
	private Map<String, ResourceEncryptor> resourceEncryptorMap = new HashMap<>();

	@Override
	public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
		configurer.mediaType("properties", MediaType.valueOf("text/plain"));
		configurer.mediaType("yml", MediaType.valueOf("text/yaml"));
		configurer.mediaType("yaml", MediaType.valueOf("text/yaml"));
	}

	@Bean
	@RefreshScope
	public EnvironmentController environmentController(
			EnvironmentRepository envRepository, ConfigServerProperties server) {
		EnvironmentController controller = new EnvironmentController(
				encrypted(envRepository, server), this.objectMapper);
		controller.setStripDocumentFromYaml(server.isStripDocumentFromYaml());
		controller.setAcceptEmpty(server.isAcceptEmpty());
		return controller;
	}

	@Bean
	@ConditionalOnBean(ResourceRepository.class)
	public ResourceController resourceController(ResourceRepository repository,
			EnvironmentRepository envRepository, ConfigServerProperties server) {
		ResourceController controller = new ResourceController(repository,
				encrypted(envRepository, server), this.resourceEncryptorMap);
		controller.setEncryptEnabled(server.getEncrypt().isEnabled());
		controller.setPlainTextEncryptEnabled(server.getEncrypt().isPlainTextEncrypt());
		return controller;
	}

	private EnvironmentRepository encrypted(EnvironmentRepository envRepository,
			ConfigServerProperties server) {
		EnvironmentEncryptorEnvironmentRepository encrypted = new EnvironmentEncryptorEnvironmentRepository(
				envRepository, environmentEncryptor);
		encrypted.setOverrides(server.getOverrides());
		return encrypted;
	}

}
