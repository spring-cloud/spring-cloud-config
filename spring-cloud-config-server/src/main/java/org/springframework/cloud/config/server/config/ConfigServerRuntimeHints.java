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

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Set;

import javax.crypto.KeyAgreement;
import javax.crypto.Mac;

import org.apache.sshd.common.channel.ChannelListener;
import org.apache.sshd.common.forward.PortForwardingEventListener;
import org.apache.sshd.common.io.nio2.Nio2ServiceFactory;
import org.apache.sshd.common.io.nio2.Nio2ServiceFactoryFactory;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.common.util.security.bouncycastle.BouncyCastleSecurityProviderRegistrar;
import org.apache.sshd.common.util.security.eddsa.EdDSASecurityProviderRegistrar;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.internal.transport.sshd.SshdText;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.cloud.config.environment.PropertyValueDescriptor;
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
				TypeReference.of(PrivateKeyValidator.class), TypeReference.of(SshPropertyValidator.class),
				TypeReference.of(PropertyValueDescriptor.class)),
				hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
		hints.reflection().registerTypes(
				Set.of(TypeReference.of(PropertyValueDescriptor.class), TypeReference.of(Mac.class),
						TypeReference.of(KeyAgreement.class), TypeReference.of(KeyPairGenerator.class),
						TypeReference.of(KeyFactory.class), TypeReference.of(Signature.class),
						TypeReference.of(MessageDigest.class)),
				hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_METHODS));

		// TODO: move over to GraalVM reachability metadata
		if (ClassUtils.isPresent("org.apache.sshd.common.SshConstants", classLoader)) {
			hints.reflection().registerTypes(Set.of(TypeReference.of(BouncyCastleSecurityProviderRegistrar.class),
					TypeReference.of(EdDSASecurityProviderRegistrar.class), TypeReference.of(Nio2ServiceFactory.class),
					TypeReference.of(Nio2ServiceFactoryFactory.class)),
					hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
			hints.reflection().registerTypes(Set.of(TypeReference.of(PortForwardingEventListener.class)),
					hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
							MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.DECLARED_FIELDS));
			hints.proxies().registerJdkProxy(TypeReference.of(ChannelListener.class),
					TypeReference.of(PortForwardingEventListener.class), TypeReference.of(SessionListener.class));
		}

		// TODO: move over to GraalVM reachability metadata
		if (ClassUtils.isPresent("org.eclipse.jgit.api.Git", classLoader)) {
			hints.reflection()
					.registerTypes(Set.of(TypeReference.of(MergeCommand.FastForwardMode.Merge.class),
							TypeReference.of(MergeCommand.ConflictStyle.class),
							TypeReference.of(MergeCommand.FastForwardMode.class), TypeReference.of(FetchCommand.class)),
							hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_METHODS));
			hints.reflection().registerTypes(Set.of(TypeReference.of(SshdText.class)), hint -> hint
					.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.DECLARED_FIELDS));
		}
	}

}
