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

package org.springframework.cloud.config.server;

import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import org.springframework.cloud.config.server.config.ConfigServerHealthIndicatorTests;
import org.springframework.cloud.config.server.config.CustomCompositeEnvironmentRepositoryTests;
import org.springframework.cloud.config.server.config.CustomEnvironmentRepositoryTests;
import org.springframework.cloud.config.server.credentials.AwsCodeCommitCredentialsProviderTests;
import org.springframework.cloud.config.server.credentials.GitCredentialsProviderFactoryTests;
import org.springframework.cloud.config.server.encryption.CipherEnvironmentEncryptorTests;
import org.springframework.cloud.config.server.encryption.EncryptionControllerMultiTextEncryptorTests;
import org.springframework.cloud.config.server.encryption.EncryptionControllerTests;
import org.springframework.cloud.config.server.encryption.EncryptionIntegrationTests;
import org.springframework.cloud.config.server.encryption.EnvironmentPrefixHelperTests;
import org.springframework.cloud.config.server.encryption.KeyStoreTextEncryptorLocatorTests;
import org.springframework.cloud.config.server.environment.CompositeEnvironmentRepositoryTests;
import org.springframework.cloud.config.server.environment.EnvironmentControllerIntegrationTests;
import org.springframework.cloud.config.server.environment.EnvironmentControllerTests;
import org.springframework.cloud.config.server.environment.EnvironmentEncryptorEnvironmentRepositoryTests;
import org.springframework.cloud.config.server.environment.JGitEnvironmentRepositoryConcurrencyTests;
import org.springframework.cloud.config.server.environment.JGitEnvironmentRepositoryIntegrationTests;
import org.springframework.cloud.config.server.environment.JGitEnvironmentRepositoryTests;
import org.springframework.cloud.config.server.environment.JdbcEnvironmentRepositoryTests;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentApplicationPlaceholderRepositoryTests;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentLabelPlaceholderRepositoryTests;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentProfilePlaceholderRepositoryTests;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentRepositoryIntegrationTests;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentRepositoryTests;
import org.springframework.cloud.config.server.environment.NativeEnvironmentRepositoryTests;
import org.springframework.cloud.config.server.environment.SVNKitEnvironmentRepositoryIntegrationTests;
import org.springframework.cloud.config.server.environment.SVNKitEnvironmentRepositoryTests;
import org.springframework.cloud.config.server.environment.VaultEnvironmentRepositoryTests;
import org.springframework.cloud.config.server.resource.GenericResourceRepositoryTests;
import org.springframework.cloud.config.server.resource.ResourceControllerIntegrationTests;
import org.springframework.cloud.config.server.resource.ResourceControllerTests;
import org.springframework.cloud.config.server.ssh.PropertyBasedSshSessionFactoryTest;
import org.springframework.cloud.config.server.ssh.SshPropertyValidatorTest;
import org.springframework.cloud.config.server.ssh.SshUriPropertyProcessorTest;

/**
 * A test suite for probing weird ordering problems in the tests.
 *
 * @author Dave Syer
 */
@RunWith(Suite.class)
@SuiteClasses({ NativeConfigServerIntegrationTests.class,
		GenericResourceRepositoryTests.class, ResourceControllerTests.class,
		ResourceControllerIntegrationTests.class, ConfigClientOnIntegrationTests.class,
		ConfigServerApplicationTests.class, VanillaConfigServerIntegrationTests.class,
		EnvironmentControllerIntegrationTests.class,
		MultipleJGitEnvironmentRepositoryIntegrationTests.class,
		EnvironmentEncryptorEnvironmentRepositoryTests.class,
		EnvironmentControllerTests.class,
		SVNKitEnvironmentRepositoryIntegrationTests.class,
		MultipleJGitEnvironmentApplicationPlaceholderRepositoryTests.class,
		JdbcEnvironmentRepositoryTests.class, CompositeEnvironmentRepositoryTests.class,
		JGitEnvironmentRepositoryConcurrencyTests.class,
		NativeEnvironmentRepositoryTests.class, JGitEnvironmentRepositoryTests.class,
		SVNKitEnvironmentRepositoryTests.class, VaultEnvironmentRepositoryTests.class,
		MultipleJGitEnvironmentRepositoryTests.class,
		JGitEnvironmentRepositoryIntegrationTests.class,
		MultipleJGitEnvironmentProfilePlaceholderRepositoryTests.class,
		MultipleJGitEnvironmentLabelPlaceholderRepositoryTests.class,
		GitCredentialsProviderFactoryTests.class,
		AwsCodeCommitCredentialsProviderTests.class,
		TransportConfigurationIntegrationTests.FileBasedCallbackTest.class,
		ConfigClientOffIntegrationTests.class,
		TransportConfigurationIntegrationTests.PropertyBasedCallbackTest.class,
		EncryptionControllerMultiTextEncryptorTests.class,
		CipherEnvironmentEncryptorTests.class,
		EncryptionIntegrationTests.BootstrapConfigSymmetricEncryptionIntegrationTests.class,
		EncryptionControllerTests.class,
		EncryptionIntegrationTests.ConfigSymmetricEncryptionIntegrationTests.class,
		EncryptionIntegrationTests.KeystoreConfigurationIntegrationTests.class,
		KeyStoreTextEncryptorLocatorTests.class, EnvironmentPrefixHelperTests.class,
		SshUriPropertyProcessorTest.class, PropertyBasedSshSessionFactoryTest.class,
		SshPropertyValidatorTest.class, CompositeIntegrationTests.class,
		SubversionConfigServerIntegrationTests.class,
		ConfigServerHealthIndicatorTests.class,
		CustomCompositeEnvironmentRepositoryTests.class,
		CustomEnvironmentRepositoryTests.class,
		BootstrapConfigServerIntegrationTests.class })
@Ignore
public class AdhocTestSuite {

}
