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
package org.springframework.cloud.config.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.config.server.encryption.EnvironmentEncryptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

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
	private ConfigServerProperties server;

	@Autowired(required=false)
	private EnvironmentEncryptor environmentEncryptor;

	@Bean
	public EnvironmentController environmentController() {
		EnvironmentController controller = new EnvironmentController(repository, environmentEncryptor);
		controller.setDefaultLabel(getDefaultLabel());
		controller.setOverrides(server.getOverrides());
		controller.setStripDocumentFromYaml(server.isStripDocumentFromYaml());
		return controller;
	}

	private String getDefaultLabel() {
		if (StringUtils.hasText(server.getDefaultLabel())) {
			return server.getDefaultLabel();
		}
		else {
			return repository.getDefaultLabel();
		}
	}
}