/*
 * Copyright 2024-2026 the original author or authors.
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

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.environment.FileResolvingEnvironmentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Autoconfiguration for {@link FileResolvingEnvironmentRepository}.
 * Wraps the existing EnvironmentRepository to support file content resolution.
 *
 * @author Johny Cho
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(EnvironmentRepositoryConfiguration.class)
public class FileResolvingEnvironmentRepositoryConfiguration {

	@Bean
	@Primary
	@ConditionalOnBean(EnvironmentRepository.class)
	@ConditionalOnProperty(value = "spring.cloud.config.server.file-resolving.enabled", havingValue = "true")
	public FileResolvingEnvironmentRepository fileResolvingEnvironmentRepository(EnvironmentRepository environmentRepository) {
		return new FileResolvingEnvironmentRepository(environmentRepository);
	}

}
