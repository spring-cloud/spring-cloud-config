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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
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

	private final TextEncryptorLocator encryptor;

	private EnvironmentPrefixHelper helper = new EnvironmentPrefixHelper();

	@Autowired
	public CipherEnvironmentEncryptor(TextEncryptorLocator encryptor) {
		this.encryptor = encryptor;
	}

	@Override
	public Environment decrypt(Environment environment) {
		return this.encryptor != null ? decrypt(environment, this.encryptor)
				: environment;
	}

	private Environment decrypt(Environment environment, TextEncryptorLocator encryptor) {
		Environment result = new Environment(environment);
		for (PropertySource source : environment.getPropertySources()) {
			Map<Object, Object> map = new LinkedHashMap<Object, Object>(
					source.getSource());
			for (Map.Entry<Object, Object> entry : new LinkedHashSet<>(map.entrySet())) {
				Object key = entry.getKey();
				String name = key.toString();
				if (entry.getValue() != null
						&& entry.getValue().toString().startsWith("{cipher}")) {
					String value = entry.getValue().toString();
					map.remove(key);
					try {
						value = value.substring("{cipher}".length());
						value = encryptor
								.locate(this.helper.getEncryptorKeys(name,
										StringUtils.arrayToCommaDelimitedString(
												environment.getProfiles()),
										value))
								.decrypt(this.helper.stripPrefix(value));
					}
					catch (Exception e) {
						value = "<n/a>";
						name = "invalid." + name;
						String message = "Cannot decrypt key: " + key + " ("
								+ e.getClass() + ": " + e.getMessage() + ")";
						if (logger.isDebugEnabled()) {
							logger.debug(message, e);
						}
						else if (logger.isWarnEnabled()) {
							logger.warn(message);
						}
					}
					map.put(name, value);
				}
			}
			result.add(new PropertySource(source.getName(), map));
		}
		return result;
	}

}
