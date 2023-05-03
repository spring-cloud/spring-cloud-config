/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.config.server.encryption.vault;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.encryption.CipherEnvironmentEncryptor;
import org.springframework.cloud.config.server.encryption.EnvironmentEncryptor;
import org.springframework.util.ObjectUtils;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.support.VaultResponse;

/**
 * VaultEnvironmentEncryptor that can decrypt property values prefixed with {vault}
 * marker.
 *
 * @author Alexey Zhokhov
 */
public class VaultEnvironmentEncryptor implements EnvironmentEncryptor {

	private static final Log logger = LogFactory.getLog(CipherEnvironmentEncryptor.class);

	private final VaultKeyValueOperations keyValueTemplate;

	public VaultEnvironmentEncryptor(VaultKeyValueOperations keyValueTemplate) {
		this.keyValueTemplate = keyValueTemplate;
	}

	@Override
	public Environment decrypt(Environment environment) {
		Map<String, VaultResponse> loadedVaultKeys = new HashMap<>();

		Environment result = new Environment(environment);
		for (PropertySource source : environment.getPropertySources()) {
			Map<Object, Object> map = new LinkedHashMap<>(source.getSource());
			for (Map.Entry<Object, Object> entry : new LinkedHashSet<>(map.entrySet())) {
				Object key = entry.getKey();
				String name = key.toString();
				if (entry.getValue() != null && entry.getValue().toString().startsWith("{vault}")) {
					String value = entry.getValue().toString();
					map.remove(key);
					try {
						value = value.substring("{vault}".length());

						if (!value.startsWith(":")) {
							throw new RuntimeException("Wrong format");
						}

						value = value.substring(1);

						if (!value.contains("#")) {
							throw new RuntimeException("Wrong format");
						}

						String[] parts = value.split("#");

						if (parts.length == 1) {
							throw new RuntimeException("Wrong format");
						}

						if (ObjectUtils.isEmpty(parts[0]) || ObjectUtils.isEmpty(parts[1])) {
							throw new RuntimeException("Wrong format");
						}

						String vaultKey = parts[0];
						String vaultParamName = parts[1];

						if (!loadedVaultKeys.containsKey(vaultKey)) {
							loadedVaultKeys.put(vaultKey, keyValueTemplate.get(vaultKey));
						}

						VaultResponse vaultResponse = loadedVaultKeys.get(vaultKey);

						if (vaultResponse == null || (vaultResponse.getData() == null
								|| !vaultResponse.getData().containsKey(vaultParamName))) {
							value = null;
						}
						else {
							value = vaultResponse.getData().get(vaultParamName).toString();
						}
					}
					catch (Exception e) {
						value = "<n/a>";
						name = "invalid." + name;
						String message = "Cannot resolve key: " + key + " (" + e.getClass() + ": " + e.getMessage()
								+ ")";
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
