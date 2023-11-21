/*
 * Copyright 2018-2023 the original author or authors.
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

package org.springframework.cloud.config.server.config;

import java.util.Set;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.cloud.config.server.ssh.HostKeyAlgoSupportedValidator;
import org.springframework.cloud.config.server.ssh.HostKeyAndAlgoBothExistValidator;
import org.springframework.cloud.config.server.ssh.KnownHostsFileValidator;
import org.springframework.cloud.config.server.ssh.PrivateKeyValidator;
import org.springframework.cloud.config.server.ssh.SshPropertyValidator;
import org.springframework.util.ClassUtils;

/**
 * A {@link RuntimeHintsRegistrar} implementation that makes types required by Config
 * Server available in constrained environments.
 *
 * @author Olga Maciaszek-Sharma
 * @since 4.1.0
 */
class ConfigServerRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		if (!ClassUtils.isPresent("org.springframework.cloud.config.server.config.ConfigServerConfiguration",
				classLoader)) {
			return;
		}
		hints.reflection().registerTypes(Set.of(TypeReference.of(HostKeyAndAlgoBothExistValidator.class),
				TypeReference.of(KnownHostsFileValidator.class), TypeReference.of(HostKeyAlgoSupportedValidator.class),
				TypeReference.of(PrivateKeyValidator.class), TypeReference.of(SshPropertyValidator.class)),
				// TypeReference.of(PropertyValueDescriptor.class)),
				hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
	}

}
