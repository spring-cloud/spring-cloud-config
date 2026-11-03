/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.config.client;

import org.springframework.core.io.Resource;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Provides access to the binary files served by a Spring Cloud Config Server.
 *
 * @author Craig Walls
 * @author Anshul Mehra
 * @author Sharang Gupta
 * @since 5.1.0
 * @see ConfigResourceClient
 */
public interface BinaryResourceConfigClient {

	/**
	 * Retrieves a binary file using the configured profile and label.
	 * @param path the path of the file, relative to the application's folder
	 * @return the file served by the config server
	 * @throws IllegalArgumentException when the application name or the config server URI
	 * is undefined
	 * @throws HttpClientErrorException when the file is not found
	 */
	Resource getBinaryResource(String path);

	/**
	 * Retrieves a binary file.
	 * @param profile the profile, or {@code null} for the configured one; may be a
	 * comma-separated list
	 * @param label the label, or {@code null} for the configured one
	 * @param path the path of the file, relative to the application's folder
	 * @return the file served by the config server
	 * @throws IllegalArgumentException when the application name or the config server URI
	 * is undefined
	 * @throws HttpClientErrorException when the file is not found
	 */
	Resource getBinaryResource(String profile, String label, String path);

}
