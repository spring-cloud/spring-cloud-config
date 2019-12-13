/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.config.server.environment.vault;

import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.config.server.environment.AbstractVaultEnvironmentRepository;
import org.springframework.cloud.config.server.environment.EnvironmentWatch;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.support.VaultResponse;

/**
 * @author Scott Frederick
 */
@Validated
public class SpringVaultEnvironmentRepository extends AbstractVaultEnvironmentRepository {

	private VaultKeyValueOperations keyValueTemplate;

	private final ObjectMapper objectMapper;

	public SpringVaultEnvironmentRepository(ObjectProvider<HttpServletRequest> request,
			EnvironmentWatch watch, VaultEnvironmentProperties properties,
			VaultKeyValueOperations keyValueTemplate) {
		super(request, watch, properties);
		this.keyValueTemplate = keyValueTemplate;
		this.objectMapper = new ObjectMapper();
	}

	protected String read(String key) {
		VaultResponse response = this.keyValueTemplate.get(key);
		if (response != null) {
			try {
				return objectMapper.writeValueAsString(response.getData());
			}
			catch (JsonProcessingException e) {
				throw new RuntimeException("Error creating Vault response", e);
			}
		}
		return null;
	}

	VaultKeyValueOperations getKeyValueTemplate() {
		return this.keyValueTemplate;
	}

}
