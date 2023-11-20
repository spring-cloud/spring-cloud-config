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

import net.i2p.crypto.eddsa.EdDSASecurityProvider;
import org.apache.sshd.client.ClientBuilder;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.kex.AbstractDHClientKeyExchange;
import org.apache.sshd.client.kex.DHGClient;
import org.apache.sshd.client.kex.DHGEXClient;
import org.apache.sshd.common.AlgorithmNameProvider;
import org.apache.sshd.common.BaseBuilder;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.OptionalFeature;
import org.apache.sshd.common.config.NamedResourceListParseResult;
import org.apache.sshd.common.digest.BuiltinDigests;
import org.apache.sshd.common.digest.Digest;
import org.apache.sshd.common.digest.DigestFactory;
import org.apache.sshd.common.digest.DigestInformation;
import org.apache.sshd.common.digest.DigestUtils;
import org.apache.sshd.common.io.DefaultIoServiceFactoryFactory;
import org.apache.sshd.common.io.IoServiceFactoryFactory;
import org.apache.sshd.common.io.nio2.Nio2ServiceFactoryFactory;
import org.apache.sshd.common.kex.AbstractDH;
import org.apache.sshd.common.kex.BuiltinDHFactories;
import org.apache.sshd.common.kex.DHFactory;
import org.apache.sshd.common.kex.DHG;
import org.apache.sshd.common.kex.DHGroupData;
import org.apache.sshd.common.kex.KeyExchangeFactory;
import org.apache.sshd.common.kex.MontgomeryCurve;
import org.apache.sshd.common.keyprovider.KeySizeIndicator;
import org.apache.sshd.common.util.security.bouncycastle.BouncyCastleSecurityProviderRegistrar;
import org.apache.sshd.common.util.security.eddsa.EdDSASecurityProviderRegistrar;
import org.apache.sshd.common.util.threads.SshdThreadFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.transport.sshd.SshdText;
import org.eclipse.jgit.lib.CoreConfig;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportGitSsh;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.util.sha1.SHA1;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.config.environment.PropertyValueDescriptor;
import org.springframework.cloud.config.server.environment.AbstractScmEnvironmentRepository;
import org.springframework.cloud.config.server.environment.JGitEnvironmentRepository;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentRepository;
import org.springframework.cloud.config.server.ssh.HostKeyAlgoSupportedValidator;
import org.springframework.cloud.config.server.ssh.HostKeyAndAlgoBothExistValidator;
import org.springframework.cloud.config.server.ssh.KnownHostsFileValidator;
import org.springframework.cloud.config.server.ssh.PrivateKeyValidator;
import org.springframework.cloud.config.server.ssh.SshPropertyValidator;
import org.springframework.cloud.config.server.support.GitCredentialsProviderFactory;
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
				TypeReference.of(PrivateKeyValidator.class), TypeReference.of(SshPropertyValidator.class),
				TypeReference.of(GitCredentialsProviderFactory.class),
				TypeReference.of(JGitEnvironmentRepository.class),
				TypeReference.of(MultipleJGitEnvironmentRepository.class),
				TypeReference.of(PropertyValueDescriptor.class),
				TypeReference.of(AbstractScmEnvironmentRepository.class)),
				hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.DECLARED_FIELDS,
						MemberCategory.INTROSPECT_DECLARED_CONSTRUCTORS, MemberCategory.DECLARED_CLASSES))
				// TODO: move over to GraalVM metadata
				.registerTypes(Set.of(TypeReference.of(CheckoutCommand.class),
						TypeReference.of(MergeCommand.FastForwardMode.Merge.class),
						TypeReference.of(MergeCommand.ConflictStyle.class),
						TypeReference.of(MergeCommand.FastForwardMode.class), TypeReference.of(JGitText.class),
						TypeReference.of(CoreConfig.AutoCRLF.class), TypeReference.of(CoreConfig.EOL.class),
						TypeReference.of(CoreConfig.CheckStat.class), TypeReference.of(CoreConfig.HideDotFiles.class),
						TypeReference.of(CoreConfig.LogRefUpdates.class), TypeReference.of(CoreConfig.SymLinks.class),
						TypeReference.of(CoreConfig.TrustPackedRefsStat.class), TypeReference.of(CoreConfig.class),
						TypeReference.of(SHA1.class), TypeReference.of(SHA1.Sha1Implementation.class),
						TypeReference.of(FetchCommand.class), TypeReference.of(CloneCommand.class),
						TypeReference.of("org.eclipse.jgit.transport.TransportGitSsh$SshFetchConnection"),
						TypeReference.of("org.eclipse.jgit.transport.FetchProcess"), TypeReference.of(Transport.class),
						TypeReference.of(SshdText.class), TypeReference.of(SshdSessionFactory.class),
						TypeReference.of(TransportGitSsh.class), TypeReference.of(SshTransport.class)),
						hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.DECLARED_FIELDS,
								MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.DECLARED_CLASSES))
				.registerTypes(Set.of(TypeReference.of(ClientBuilder.class), TypeReference.of(MontgomeryCurve.class),
						TypeReference.of(BuiltinDHFactories.class), TypeReference.of(BaseBuilder.class),
						TypeReference.of(NamedFactory.class), TypeReference.of(DHFactory.class),
						TypeReference.of(BuiltinDHFactories.Constants.class),
						TypeReference.of(BuiltinDHFactories.ParseResult.class),
						TypeReference.of(NamedResourceListParseResult.class), TypeReference.of(DHG.class),
						TypeReference.of(AbstractDH.class), TypeReference.of(BuiltinDigests.Constants.class),
						TypeReference.of(BuiltinDigests.class), TypeReference.of(DHGroupData.class),
						TypeReference.of(NamedResource.class),
						// TypeReference.of(NamedResource.NAME_EXTRACTOR.getClass()),
						// TypeReference.of(NamedResource.BY_NAME_COMPARATOR.getClass()),
						// TypeReference.of(ClientBuilder.DH2KEX.getClass()),
						TypeReference.of(DHGEXClient.class), TypeReference.of(AbstractDHClientKeyExchange.class),
						TypeReference.of(SshClient.class), TypeReference.of(DHGClient.class),
						TypeReference.of(KeyExchangeFactory.class), TypeReference.of(KeySizeIndicator.class),
						TypeReference.of(OptionalFeature.class), TypeReference.of(DigestFactory.class),
						TypeReference.of(DigestInformation.class), TypeReference.of(AlgorithmNameProvider.class),
						TypeReference.of(Digest.class), TypeReference.of(DigestUtils.class),
						TypeReference.of(BouncyCastleSecurityProviderRegistrar.class),
						TypeReference.of(BouncyCastleProvider.class),
						TypeReference.of(EdDSASecurityProviderRegistrar.class),
						TypeReference.of(EdDSASecurityProvider.class),
						TypeReference.of(DefaultIoServiceFactoryFactory.class),
						TypeReference.of(IoServiceFactoryFactory.class),
						TypeReference.of(Nio2ServiceFactoryFactory.class), TypeReference.of(SshdThreadFactory.class)),
						hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
								MemberCategory.DECLARED_FIELDS, MemberCategory.INVOKE_DECLARED_METHODS,
								MemberCategory.DECLARED_CLASSES));

	}

}
