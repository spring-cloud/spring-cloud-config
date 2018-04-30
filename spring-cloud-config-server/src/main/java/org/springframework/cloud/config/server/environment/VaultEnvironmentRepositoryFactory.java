/*
 * Copyright 2018 the original author or authors.
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
package org.springframework.cloud.config.server.environment;

import java.util.Optional;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestTemplate;

/**
 * @author Dylan Roberts
 */
public class VaultEnvironmentRepositoryFactory implements EnvironmentRepositoryFactory<VaultEnvironmentRepository,
		VaultEnvironmentProperties> {
	private ObjectProvider<HttpServletRequest> request;
	private EnvironmentWatch watch;
	private Optional<VaultRestTemplateFactory> vaultRestTemplateFactory;

	public VaultEnvironmentRepositoryFactory(ObjectProvider<HttpServletRequest> request, EnvironmentWatch watch,
                                             Optional<VaultRestTemplateFactory> vaultRestTemplateFactory) {
		this.request = request;
		this.watch = watch;
		this.vaultRestTemplateFactory = vaultRestTemplateFactory;
	}

	@Override
	public VaultEnvironmentRepository build(VaultEnvironmentProperties environmentProperties) throws Exception {
		if (vaultRestTemplateFactory.isPresent()) {
			RestTemplate restTemplate = vaultRestTemplateFactory.get().build(environmentProperties);
			return new VaultEnvironmentRepository(request, watch, restTemplate, environmentProperties);
		}
		return new VaultEnvironmentRepository(request, watch, new RestTemplate(), environmentProperties);
	}

	public interface VaultRestTemplateFactory {

		RestTemplate build(VaultEnvironmentProperties environmentProperties) throws Exception;
	}
}
