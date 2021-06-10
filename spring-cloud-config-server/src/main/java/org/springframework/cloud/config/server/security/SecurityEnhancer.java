/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.config.server.security;

import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.resource.ResourceRepository;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * Security enhancer for config server. Used to wrap a service used in controller as a
 * secured object (which will be intercept by
 * {@link org.springframework.security.access.intercept.aopalliance.MethodSecurityInterceptor}
 * ).
 *
 * @author ian
 */
public interface SecurityEnhancer {

	/**
	 * Dummy implement.
	 */
	SecurityEnhancer DUMMY = new SecurityEnhancer() {
	};

	/**
	 * Secure EnvironmentRepository.
	 */
	default EnvironmentRepository secure(EnvironmentRepository repository) {
		return repository;
	}

	/**
	 * Secure ResourceRepository.
	 */
	default ResourceRepository secure(ResourceRepository repository) {
		return repository;
	}

	/**
	 * Secure TextEncryptor.
	 */
	default TextEncryptor secure(TextEncryptor encryptor, String application, String profiles) {
		return encryptor;
	}

}
