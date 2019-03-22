/*
 * Copyright 2015-2019 the original author or authors.
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
import java.util.Map;

import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.util.StringUtils;

/**
 * Shared helper class for encryption and decryption concerns where the plain text and
 * cipher texts can optionally contain prefixes of <code>{name:value}</code> pairs.
 * Special treatment is given to the "name" and "profiles" keys, which can be provided by
 * the caller, explicitly instead of by the input text strings. This is to support
 * independent decryptions using different cryptographic keys for different applications
 * and profiles, if needed (this class does not have any crypto features, but it can be
 * used by components that do).
 *
 * @author Dave Syer
 *
 */
class EnvironmentPrefixHelper {

	/**
	 * key for the "profiles" prefix pair (usually a Spring profile or comma separated
	 * value).
	 */
	private static final String PROFILES = "profiles";

	/**
	 * key for the "name" (Environment name or application name).
	 */
	private static final String NAME = "name";

	/**
	 * If plain text actually starts with text in the form <code>{name:value}</code>
	 * prefix it with this to signal the start of the plain text.
	 */
	private static final String ESCAPE = "{plain}";

	/**
	 * Extract keys for looking up a {@link TextEncryptor} from the input text in the form
	 * of a prefix of zero or many <code>{name:value}</code> pairs. The name and profiles
	 * properties are always added to the keys (replacing any provided in the inputs).
	 * @param name application name
	 * @param profiles list of profiles
	 * @param text text to cipher
	 * @return encryptor keys
	 */
	public Map<String, String> getEncryptorKeys(String name, String profiles,
			String text) {

		Map<String, String> keys = new LinkedHashMap<String, String>();

		text = removeEnvironmentPrefix(text);
		keys.put(NAME, name);
		keys.put(PROFILES, profiles);

		if (text.contains(ESCAPE)) {
			text = text.substring(0, text.indexOf(ESCAPE));
		}

		String[] tokens = StringUtils.split(text, "}");
		while (tokens != null) {
			String token = tokens[0].trim();
			if (token.startsWith("{")) {
				String key = "";
				String value = "";
				if (token.contains(":") && !token.endsWith(":")) {
					key = token.substring(1, token.indexOf(":"));
					value = token.substring(token.indexOf(":") + 1);
				}
				else {
					key = token.substring(1);
				}
				keys.put(key, value);
			}
			text = tokens[1];
			tokens = StringUtils.split(text, "}");
		}

		return keys;

	}

	/**
	 * Add a prefix to the input text (usually a cipher) consisting of the
	 * <code>{name:value}</code> pairs. The "name" and "profiles" keys are special in that
	 * they are stripped since that information is always available when deriving the keys
	 * in {@link #getEncryptorKeys(String, String, String)}.
	 * @param keys name, value pairs
	 * @param input input to append
	 * @return prefixed input
	 */
	public String addPrefix(Map<String, String> keys, String input) {
		keys.remove(NAME);
		keys.remove(PROFILES);
		StringBuilder builder = new StringBuilder();
		for (String key : keys.keySet()) {
			builder.append("{").append(key).append(":").append(keys.get(key)).append("}");
		}
		builder.append(input);
		return builder.toString();
	}

	public String stripPrefix(String value) {
		if (!value.contains("}")) {
			return value;
		}
		if (value.contains(ESCAPE)) {
			return value.substring(value.indexOf(ESCAPE) + ESCAPE.length());
		}
		return value.replaceFirst("^(\\{.*?:.*?\\})+", "");
	}

	private String removeEnvironmentPrefix(String input) {
		return input.replaceFirst("\\{name:.*\\}", "").replaceFirst("\\{profiles:.*\\}",
				"");
	}

}
