/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.cloud.config.server.encryption;

import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * Service interface for locating proper TextEncryptor to be used for particular application.
 * It can be used to provide config server with application and environment specific encryption.
 *
 * @author Bartosz Wojtkiewicz
 * @author Rafal Zukowski
 *
 */
public interface TextEncryptorLocator {
	/**
	 * Returns TextEncryptor to be used for given application and profiles.
	 *
	 * @param applicationName application name
	 * @param profiles comma separated list of profiles
	 */
	TextEncryptor locate(String applicationName, String profiles);
}
