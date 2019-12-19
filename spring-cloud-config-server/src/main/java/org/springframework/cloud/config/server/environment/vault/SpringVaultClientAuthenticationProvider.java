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

package org.springframework.cloud.config.server.environment.vault;

import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.web.client.RestOperations;

public abstract class SpringVaultClientAuthenticationProvider {

	protected static final String VAULT_PROPERTIES_PREFIX = "spring.cloud.config.server.vault.";

	protected final AuthenticationMethod supportedAuthenticationMethod;

	protected SpringVaultClientAuthenticationProvider(
			AuthenticationMethod supportedAuthenticationMethod) {
		this.supportedAuthenticationMethod = supportedAuthenticationMethod;
	}

	public boolean supports(VaultEnvironmentProperties properties) {
		return properties.getAuthentication().equals(this.supportedAuthenticationMethod);
	}

	public abstract ClientAuthentication getClientAuthentication(
			VaultEnvironmentProperties vaultProperties,
			RestOperations vaultRestOperations, RestOperations externalRestOperations);

	protected String missingPropertyForAuthMethod(String propertyName,
			AuthenticationMethod authenticationMethod) {
		return "The '" + VAULT_PROPERTIES_PREFIX + propertyName
				+ "' property must be provided " + "when the " + authenticationMethod
				+ " authentication method is specified.";
	}

	protected String missingClassForAuthMethod(String className, String classArtifact,
			AuthenticationMethod authenticationMethod) {
		return className + "(" + classArtifact + ")"
				+ " must be on the classpath when the " + authenticationMethod
				+ " authentication method is specified";
	}

	protected void assertClassPresent(String className, String message) {
		Assert.isTrue(ClassUtils.isPresent(className, getClass().getClassLoader()),
				message);
	}

}
