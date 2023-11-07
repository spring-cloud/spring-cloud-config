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

package org.springframework.cloud.config.server.config;

import java.util.Set;

import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.CoreConfig;
import org.eclipse.jgit.util.sha1.SHA1;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.config.environment.PropertyValueDescriptor;
import org.springframework.cloud.config.server.ssh.HostKeyAlgoSupportedValidator;
import org.springframework.cloud.config.server.ssh.HostKeyAndAlgoBothExistValidator;
import org.springframework.cloud.config.server.ssh.KnownHostsFileValidator;
import org.springframework.cloud.config.server.ssh.PrivateKeyValidator;
import org.springframework.cloud.config.server.ssh.SshPropertyValidator;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.util.ClassUtils;

/**
 * @author Spencer Gibb
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(ConfigServerConfiguration.Marker.class)
@ConditionalOnProperty(name = ConfigServerProperties.PREFIX + ".enabled", matchIfMissing = true)
@EnableConfigurationProperties(ConfigServerProperties.class)
@Import({ EnvironmentRepositoryConfiguration.class, CompositeConfiguration.class, ResourceRepositoryConfiguration.class,
		ConfigServerEncryptionConfiguration.class, ConfigServerMvcConfiguration.class,
		ResourceEncryptorConfiguration.class })
public class ConfigServerAutoConfiguration {

}

class ConfigServerHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		if (!ClassUtils.isPresent("org.springframework.cloud.config.server.config.ConfigServerConfiguration",
				classLoader)) {
			return;
		}
		hints.reflection().registerTypes(Set.of(TypeReference.of(HostKeyAndAlgoBothExistValidator.class),
				TypeReference.of(KnownHostsFileValidator.class), TypeReference.of(HostKeyAlgoSupportedValidator.class),
				TypeReference.of(PrivateKeyValidator.class), TypeReference.of(SshPropertyValidator.class)),
				hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS))
				.registerType(TypeReference.of(PropertyValueDescriptor.class),
						hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.DECLARED_FIELDS,
								MemberCategory.INTROSPECT_DECLARED_CONSTRUCTORS))
				// TODO: move over to GraalVM metadata
				.registerTypes(Set.of(TypeReference.of(CheckoutCommand.class),
						TypeReference.of(MergeCommand.FastForwardMode.Merge.class),
						TypeReference.of(MergeCommand.ConflictStyle.class),
						TypeReference.of(MergeCommand.FastForwardMode.class), TypeReference.of(JGitText.class),
						TypeReference.of(CoreConfig.AutoCRLF.class), TypeReference.of(CoreConfig.EOL.class),
						TypeReference.of(CoreConfig.CheckStat.class), TypeReference.of(CoreConfig.HideDotFiles.class),
						TypeReference.of(CoreConfig.LogRefUpdates.class), TypeReference.of(CoreConfig.SymLinks.class),
						TypeReference.of(CoreConfig.TrustPackedRefsStat.class), TypeReference.of(CoreConfig.class),
						TypeReference.of(SHA1.class), TypeReference.of(SHA1.Sha1Implementation.class)),
						hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.PUBLIC_FIELDS));
	}

}
