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

package org.springframework.cloud.config.server.environment;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.environment.PropertyValueDescriptor;
import org.springframework.cloud.config.server.encryption.EnvironmentEncryptor;

/**
 * A delegating {@link EnvironmentRepository} that can decrypt the properties if an
 * {@link EnvironmentEncryptor} is provided.
 *
 * @author Dave Syer
 *
 */
public class EnvironmentEncryptorEnvironmentRepository implements EnvironmentRepository {

	private EnvironmentRepository delegate;

	private EnvironmentEncryptor environmentEncryptor;

	private Map<String, String> overrides = new LinkedHashMap<>();

	public EnvironmentEncryptorEnvironmentRepository(EnvironmentRepository delegate) {
		this(delegate, null);
	}

	public EnvironmentEncryptorEnvironmentRepository(EnvironmentRepository delegate,
			EnvironmentEncryptor environmentEncryptor) {
		this.delegate = delegate;
		this.environmentEncryptor = environmentEncryptor;
	}

	@Override
	public Environment findOne(String name, String profiles, String label) {
		return findOne(name, profiles, label, false);
	}

	@Override
	public Environment findOne(String name, String profiles, String label,
			boolean includeOrigin) {
		Environment environment = this.delegate.findOne(name, profiles, label,
				includeOrigin);
		if (this.environmentEncryptor != null) {
			environment = this.environmentEncryptor.decrypt(environment);
		}
		if (!this.overrides.isEmpty()) {
			environment.addFirst(
					new PropertySource("overrides", getOverridesMap(includeOrigin)));
		}
		return environment;
	}

	private Map<?, ?> getOverridesMap(boolean includeOrigin) {
		if (!includeOrigin) {
			return this.overrides;
		}
		Map<Object, Object> map = new LinkedHashMap<>();
		for (Map.Entry entry : this.overrides.entrySet()) {
			map.put(entry.getKey(), new PropertyValueDescriptor(entry.getValue(),
					"Config server overrides"));
		}
		return map;
	}

	/**
	 * @param overrides the overrides to set
	 */
	public void setOverrides(Map<String, String> overrides) {
		this.overrides = new HashMap<>(overrides);
		for (String key : overrides.keySet()) {
			if (overrides.get(key).contains("\\{")) {
				this.overrides.put(key, overrides.get(key).replace("\\{", "{"));
			}
			if (overrides.get(key).contains("\\${")) {
				this.overrides.put(key, overrides.get(key).replace("\\${", "${"));
			}
		}
	}

}
