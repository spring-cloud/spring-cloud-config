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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * <p>
 * This class is responsible for decrypting properties in the environment that are
 * prefixed with "{vault}". The vault key-value pairs are retrieved from a Vault server
 * using the provided {@link VaultKeyValueOperations} template. Properties that start with
 * "{vault}" are expected to follow a specific format: "{vault}:key#path" where:
 * <ul>
 * <li>"key" is the Vault secret path</li>
 * <li>"path" is the key within the Vault secret</li>
 * </ul>
 *
 * <p>
 * Before retrieving values from Vault, any environment variable placeholders in the
 * format ${VAR_NAME} found in the vault reference are replaced with actual environment
 * variable values. If an environment variable is not found, the placeholder remains
 * unchanged.
 * </p>
 *
 * @author Alexey Zhokhov
 * @author Pavel Andrusov
 */
public class VaultEnvironmentEncryptor implements EnvironmentEncryptor {

	private static final Log logger = LogFactory.getLog(CipherEnvironmentEncryptor.class);

	private final VaultKeyValueOperations keyValueTemplate;

	private boolean prefixInvalidProperties = true;

	public VaultEnvironmentEncryptor(VaultKeyValueOperations keyValueTemplate) {
		this.keyValueTemplate = keyValueTemplate;
	}

	/**
	 * Decrypts property values in the provided environment that are prefixed with
	 * "{vault}". For each such property, this method:
	 * <ol>
	 * <li>Extracts the Vault key and path from the property value</li>
	 * <li>Replaces any environment variable placeholders (${VAR_NAME}) in the vault
	 * reference with actual environment values</li>
	 * <li>Retrieves the secret value from Vault using the provided key-value
	 * template</li>
	 * <li>Replaces the property value with the decrypted value from Vault</li>
	 * </ol>
	 * @param environment the environment containing properties to decrypt
	 * @return a new Environment object with decrypted values where applicable
	 */
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

						String vaultKey = replaceEnvironmentPlaceholders(parts[0]);
						String vaultParamName = replaceEnvironmentPlaceholders(parts[1]);

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
						if (this.prefixInvalidProperties) {
							value = "<n/a>";
							name = "invalid." + name;
						}
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

	/**
	 * Replace environment variable placeholders like ${VAR_NAME} with actual values from
	 * system environment variables.
	 *
	 * <p>
	 * If an environment variable is not found, the placeholder remains unchanged.
	 * </p>
	 * @param value the string value that may contain environment variable placeholders
	 * @return the string with environment variable placeholders replaced by their values,
	 * or the original string if no placeholders are found
	 */
	private String replaceEnvironmentPlaceholders(final String value) {
		if (value == null) {
			logger.debug("Input value is null, returning null");
			return null;
		}

		logger.debug("Processing placeholder replacement for input: %s".formatted(value));

		// Pattern to match ${VAR_NAME} format
		Pattern pattern = Pattern.compile("\\$\\{([^}]+)}");
		Matcher matcher = pattern.matcher(value);

		if (!matcher.find()) {
			logger.debug("No placeholders found in input string");
			return value;
		}

		// Reset matcher for replacement process
		matcher.reset();

		StringBuilder result = new StringBuilder();
		int lastEnd = 0;
		int processedCount = 0;

		while (matcher.find()) {
			processedCount++;
			String variableName = matcher.group(1);
			logger.debug("Found placeholder with variable name: %s".formatted(variableName));

			// Append the text before the placeholder
			result.append(value, lastEnd, matcher.start());

			String replacement = System.getenv(variableName);
			if (replacement != null) {
				// Replace with environment variable value
				result.append(replacement);
				logger.info(
						"Successfully resolved '%s' placeholder from system environment variables. Placeholder replaced."
							.formatted(variableName));
			}
			else {
				// If environment variable not found, keep original placeholder
				result.append(matcher.group(0));
				logger
					.warn("Environment variable '%s' not found. Keeping original placeholder.".formatted(variableName));
			}

			lastEnd = matcher.end();
		}

		// Append the remaining text after the last placeholder
		result.append(value, lastEnd, value.length());

		logger.debug("Placeholder replacement completed. Processed %d placeholders.".formatted(processedCount));

		return result.toString();
	}

	/**
	 * Set whether to prefix invalid properties with "invalid.".
	 * @param prefixInvalidProperties whether to prefix invalid properties
	 */
	public void setPrefixInvalidProperties(final boolean prefixInvalidProperties) {
		this.prefixInvalidProperties = prefixInvalidProperties;
	}

}
