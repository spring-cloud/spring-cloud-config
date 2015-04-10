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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * EnvironmentEncryptor that can decrypt property values prefixed with {cipher} marker.
 *
 * @author Dave Syer
 * @author Bartosz Wojtkiewicz
 * @author Rafal Zukowski
 *
 */
@Component
public class CipherEnvironmentEncryptor implements EnvironmentEncryptor {

	private static Log logger = LogFactory.getLog(CipherEnvironmentEncryptor.class);

	private final TextEncryptorLocator encryptorLocator;

	@Autowired
	public CipherEnvironmentEncryptor(TextEncryptorLocator encryptorLocator) {
		this.encryptorLocator = encryptorLocator;
	}

	@Override
	public Environment decrypt(Environment environment) {
		TextEncryptor encryptor = encryptorLocator.locate(environment.getName(),
				StringUtils.arrayToCommaDelimitedString(environment.getProfiles()));
		return encryptor != null ? decrypt(environment, encryptor) : environment;
	}

	private Environment decrypt(Environment environment, TextEncryptor encryptor) {
		Environment result = new Environment(environment.getName(),
		environment.getProfiles(), environment.getLabel());
		for (PropertySource source : environment.getPropertySources()) {
			Map<Object, Object> map = new LinkedHashMap<Object, Object>(source.getSource());
			for (Map.Entry<Object, Object> entry : new LinkedHashSet<>(map.entrySet())) {
				Object key = entry.getKey();
				String name = key.toString();
				String value = entry.getValue().toString();
				if (value.startsWith("{cipher}")) {
					map.remove(key);
					try {
						value = value == null ? null : encryptor.decrypt(value.substring(
																	"{cipher}".length()));
					} catch (Exception e) {
						value = "<n/a>";
						name = "invalid." + name;
						logger.warn("Cannot decrypt key: " + key
								+ " (" + e.getClass() + ": " + e.getMessage() + ")");
					}
					map.put(name, value);
				}
			}
			result.add(new PropertySource(source.getName(), map));
		}
		return result;
	}

}
