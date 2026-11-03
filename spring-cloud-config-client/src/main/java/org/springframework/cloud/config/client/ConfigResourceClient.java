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

/**
 * Provides access to the plain text and binary files served by a Spring Cloud Config
 * Server. When the configuration is imported with {@code spring.config.import}, a
 * {@link ConfigResourceClient} that uses the same connection settings as the import is
 * available as a bean.
 *
 * @author Daniel Lavoie
 * @author Sharang Gupta
 * @since 5.1.0
 * @see ConfigResourceClientAutoConfiguration
 */
public interface ConfigResourceClient extends PlainTextConfigClient, BinaryResourceConfigClient {

}
