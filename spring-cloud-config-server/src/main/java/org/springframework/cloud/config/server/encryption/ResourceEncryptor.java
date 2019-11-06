/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.cloud.config.server.encryption;

import java.io.IOException;
import java.util.List;

import org.springframework.cloud.config.environment.Environment;

/**
 * Interface for decrypting values in plain text files served through
 * {@link org.springframework.cloud.config.server.resource.ResourceController}.
 *
 * @author Sean Stiglitz
 */
public interface ResourceEncryptor {

	List<String> getSupportedExtensions();

	String decrypt(String text, Environment environment) throws IOException;

}
