package org.springframework.cloud.config.server;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import org.springframework.cloud.config.server.config.ConfigServerHealthIndicatorTests;
import org.springframework.cloud.config.server.config.CustomCompositeEnvironmentRepositoryTests;
import org.springframework.cloud.config.server.config.CustomEnvironmentRepositoryTests;
import org.springframework.cloud.config.server.config.TransportConfigurationTest;
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
@SuiteClasses({ TransportConfigurationIntegrationTests.PropertyBasedCallbackTest.class,
	ConfigClientOnIntegrationTests.class,
	BootstrapConfigServerIntegrationTests.class,
	ResourceControllerIntegrationTests.class,
	GenericResourceRepositoryTests.class,
	ResourceControllerTests.class,
	SubversionConfigServerIntegrationTests.class,
	TransportConfigurationIntegrationTests.FileBasedCallbackTest.class,
	TransportConfigurationTest.class,
	ConfigServerHealthIndicatorTests.class,
	CustomCompositeEnvironmentRepositoryTests.class,
	CustomEnvironmentRepositoryTests.class,
	ConfigClientOffIntegrationTests.class,
	AwsCodeCommitCredentialsProviderTests.class,
	GitCredentialsProviderFactoryTests.class,
	PropertyBasedSshSessionFactoryTest.class,
	SshUriPropertyProcessorTest.class,
	SshPropertyValidatorTest.class,
	NativeConfigServerIntegrationTests.class,
	EncryptionIntegrationTests.ConfigSymmetricEncryptionIntegrationTests.class,
	EnvironmentPrefixHelperTests.class,
	EncryptionControllerTests.class,
	CipherEnvironmentEncryptorTests.class,
	EncryptionIntegrationTests.KeystoreConfigurationIntegrationTests.class,
	KeyStoreTextEncryptorLocatorTests.class,
	EncryptionIntegrationTests.BootstrapConfigSymmetricEncryptionIntegrationTests.class,
	EncryptionControllerMultiTextEncryptorTests.class,
	CompositeConfigServerIntegrationTests.class,
	VanillaConfigServerIntegrationTests.class,
	VaultEnvironmentRepositoryTests.class,
	MultipleJGitEnvironmentLabelPlaceholderRepositoryTests.class,
	EnvironmentControllerTests.class,
	JGitEnvironmentRepositoryIntegrationTests.class,
	CompositeEnvironmentRepositoryTests.class,
	MultipleJGitEnvironmentApplicationPlaceholderRepositoryTests.class,
	EnvironmentControllerIntegrationTests.class,
	JGitEnvironmentRepositoryTests.class,
	EnvironmentEncryptorEnvironmentRepositoryTests.class,
	JdbcEnvironmentRepositoryTests.class,
	SVNKitEnvironmentRepositoryTests.class,
	MultipleJGitEnvironmentRepositoryTests.class,
	NativeEnvironmentRepositoryTests.class,
	JGitEnvironmentRepositoryConcurrencyTests.class,
	SVNKitEnvironmentRepositoryIntegrationTests.class,
	MultipleJGitEnvironmentRepositoryIntegrationTests.class,
	MultipleJGitEnvironmentProfilePlaceholderRepositoryTests.class
 })
// @Ignore
public class AdhocTestSuite {

}
