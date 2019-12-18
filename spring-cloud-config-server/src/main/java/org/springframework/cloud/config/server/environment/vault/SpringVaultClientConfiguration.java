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

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.config.server.environment.ConfigTokenProvider;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AppRoleProperties;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AwsEc2Properties;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AwsIamProperties;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AzureMsiProperties;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.GcpCredentials;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.GcpIamProperties;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.KubernetesProperties;
import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.PcfProperties;
import org.springframework.core.io.Resource;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.AppRoleAuthenticationOptionsBuilder;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.RoleId;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.SecretId;
import org.springframework.vault.authentication.AwsEc2Authentication;
import org.springframework.vault.authentication.AwsEc2AuthenticationOptions;
import org.springframework.vault.authentication.AwsEc2AuthenticationOptions.Nonce;
import org.springframework.vault.authentication.AwsIamAuthentication;
import org.springframework.vault.authentication.AwsIamAuthenticationOptions;
import org.springframework.vault.authentication.AwsIamAuthenticationOptions.AwsIamAuthenticationOptionsBuilder;
import org.springframework.vault.authentication.AzureMsiAuthentication;
import org.springframework.vault.authentication.AzureMsiAuthenticationOptions;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.ClientCertificateAuthentication;
import org.springframework.vault.authentication.CubbyholeAuthentication;
import org.springframework.vault.authentication.CubbyholeAuthenticationOptions;
import org.springframework.vault.authentication.GcpComputeAuthentication;
import org.springframework.vault.authentication.GcpComputeAuthenticationOptions;
import org.springframework.vault.authentication.GcpComputeAuthenticationOptions.GcpComputeAuthenticationOptionsBuilder;
import org.springframework.vault.authentication.GcpCredentialSupplier;
import org.springframework.vault.authentication.GcpIamAuthentication;
import org.springframework.vault.authentication.GcpIamAuthenticationOptions;
import org.springframework.vault.authentication.GcpIamAuthenticationOptions.GcpIamAuthenticationOptionsBuilder;
import org.springframework.vault.authentication.KubernetesAuthentication;
import org.springframework.vault.authentication.KubernetesAuthenticationOptions;
import org.springframework.vault.authentication.KubernetesServiceAccountTokenFile;
import org.springframework.vault.authentication.PcfAuthentication;
import org.springframework.vault.authentication.PcfAuthenticationOptions;
import org.springframework.vault.authentication.PcfAuthenticationOptions.PcfAuthenticationOptionsBuilder;
import org.springframework.vault.authentication.ResourceCredentialSupplier;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.RestTemplateBuilder;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.client.VaultEndpointProvider;
import org.springframework.vault.config.AbstractVaultConfiguration;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod.APPROLE;
import static org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod.AWS_IAM;
import static org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod.AZURE_MSI;
import static org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod.CUBBYHOLE;
import static org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod.GCP_GCE;
import static org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod.GCP_IAM;
import static org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod.KUBERNETES;
import static org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod.PCF;
import static org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod.TOKEN;

/**
 * This class is adapted from
 * {@link org.springframework.vault.config.EnvironmentVaultConfiguration} and <a href=
 * https://github.com/spring-cloud/spring-cloud-vault/blob/master/spring-cloud-vault-config/src/main/java/org/springframework/cloud/vault/config/ClientAuthenticationFactory.java>
 * org.springframework.cloud.vault.config.ClientAuthenticationFactory</a> in order to
 * provide configuration consistent with Spring Cloud Vault's property-based
 * configuration.
 *
 * @author Scott Frederick
 */
public class SpringVaultClientConfiguration extends AbstractVaultConfiguration {

	private static final String VAULT_PROPERTIES_PREFIX = "spring.cloud.config.server.vault.";

	private final VaultEnvironmentProperties vaultProperties;

	private final ConfigTokenProvider configTokenProvider;

	private final RestOperations externalRestOperations;

	private final Log log = LogFactory.getLog(getClass());

	public SpringVaultClientConfiguration(VaultEnvironmentProperties vaultProperties,
			ConfigTokenProvider configTokenProvider) {
		this.vaultProperties = vaultProperties;
		this.configTokenProvider = configTokenProvider;
		this.externalRestOperations = new RestTemplate(
				clientHttpRequestFactoryWrapper().getClientHttpRequestFactory());
	}

	@Override
	public VaultEndpoint vaultEndpoint() {

		URI baseUrl = UriComponentsBuilder.newInstance()
				.scheme(vaultProperties.getScheme()).host(vaultProperties.getHost())
				.port(vaultProperties.getPort()).build().toUri();

		return VaultEndpoint.from(baseUrl);
	}

	@Override
	protected RestTemplateBuilder restTemplateBuilder(
			VaultEndpointProvider endpointProvider,
			ClientHttpRequestFactory requestFactory) {

		RestTemplateBuilder restTemplateBuilder = super.restTemplateBuilder(
				endpointProvider, requestFactory);

		if (vaultProperties.getNamespace() != null) {
			restTemplateBuilder.customizers(
					restTemplate -> restTemplate.getInterceptors().add(VaultClients
							.createNamespaceInterceptor(vaultProperties.getNamespace())));
		}

		return restTemplateBuilder;
	}

	@Override
	public SslConfiguration sslConfiguration() {
		if (vaultProperties.isSkipSslValidation()) {
			log.warn("The '" + VAULT_PROPERTIES_PREFIX + "skipSslValidation' property "
					+ "is not supported by this Vault environment repository implementation. "
					+ "Use the '" + VAULT_PROPERTIES_PREFIX
					+ "ssl` properties to provide "
					+ "custom keyStore and trustStore material instead.");
		}

		VaultEnvironmentProperties.Ssl ssl = vaultProperties.getSsl();

		SslConfiguration.KeyStoreConfiguration keyStoreConfiguration = getKeyStoreConfiguration(
				ssl.getKeyStore(), ssl.getKeyStorePassword());

		SslConfiguration.KeyStoreConfiguration trustStoreConfiguration = getKeyStoreConfiguration(
				ssl.getTrustStore(), ssl.getTrustStorePassword());

		return new SslConfiguration(keyStoreConfiguration, trustStoreConfiguration);
	}

	private SslConfiguration.KeyStoreConfiguration getKeyStoreConfiguration(
			Resource resourceProperty, String passwordProperty) {

		if (resourceProperty == null) {
			return SslConfiguration.KeyStoreConfiguration.unconfigured();
		}

		if (StringUtils.hasText(passwordProperty)) {
			return SslConfiguration.KeyStoreConfiguration.of(resourceProperty,
					passwordProperty.toCharArray());
		}

		return SslConfiguration.KeyStoreConfiguration.of(resourceProperty);
	}

	/**
	 * @return a new {@link ClientAuthentication}.
	 */
	public ClientAuthentication clientAuthentication() {

		AuthenticationMethod authentication = this.vaultProperties.getAuthentication();

		if (authentication == null) {
			return new ConfigTokenProviderAuthentication(configTokenProvider);
		}

		switch (authentication) {
		case APPROLE:
			return appRoleAuthentication(this.vaultProperties);
		case AWS_EC2:
			return awsEc2Authentication(this.vaultProperties);
		case AWS_IAM:
			return awsIamAuthentication(this.vaultProperties);
		case AZURE_MSI:
			return azureMsiAuthentication(this.vaultProperties);
		case CERT:
			return new ClientCertificateAuthentication(restOperations());
		case CUBBYHOLE:
			return cubbyholeAuthentication();
		case GCP_GCE:
			return gcpGceAuthentication(this.vaultProperties);
		case GCP_IAM:
			return gcpIamAuthentication(this.vaultProperties);
		case KUBERNETES:
			return kubernetesAuthentication(this.vaultProperties);
		case PCF:
			return pcfAuthentication(this.vaultProperties);
		case TOKEN:
			Assert.hasText(this.vaultProperties.getToken(),
					missingPropertyForAuthMethod("token", TOKEN));
			return new TokenAuthentication(this.vaultProperties.getToken());
		}

		throw new UnsupportedOperationException(
				String.format("Client authentication %s not supported", authentication));
	}

	private ClientAuthentication appRoleAuthentication(
			VaultEnvironmentProperties vaultProperties) {

		AppRoleAuthenticationOptions options = getAppRoleAuthenticationOptions(
				vaultProperties);

		return new AppRoleAuthentication(options, restOperations());
	}

	static AppRoleAuthenticationOptions getAppRoleAuthenticationOptions(
			VaultEnvironmentProperties vaultProperties) {

		AppRoleProperties appRole = vaultProperties.getAppRole();

		AppRoleAuthenticationOptionsBuilder builder = AppRoleAuthenticationOptions
				.builder().path(appRole.getAppRolePath());

		if (StringUtils.hasText(appRole.getRole())) {
			builder.appRole(appRole.getRole());
		}

		RoleId roleId = getRoleId(vaultProperties, appRole);
		SecretId secretId = getSecretId(vaultProperties, appRole);

		builder.roleId(roleId).secretId(secretId);

		return builder.build();
	}

	private static RoleId getRoleId(VaultEnvironmentProperties vaultProperties,
			AppRoleProperties appRole) {

		if (StringUtils.hasText(appRole.getRoleId())) {
			return RoleId.provided(appRole.getRoleId());
		}

		if (StringUtils.hasText(vaultProperties.getToken())
				&& StringUtils.hasText(appRole.getRole())) {
			return RoleId.pull(VaultToken.of(vaultProperties.getToken()));
		}

		if (StringUtils.hasText(vaultProperties.getToken())) {
			return RoleId.wrapped(VaultToken.of(vaultProperties.getToken()));
		}

		throw new IllegalArgumentException(
				"Any of '" + VAULT_PROPERTIES_PREFIX + "app-role.role-id', '.token', "
						+ "or '.app-role.role' and '.token' must be provided if the "
						+ APPROLE + " authentication method is specified.");
	}

	private static SecretId getSecretId(VaultEnvironmentProperties vaultProperties,
			AppRoleProperties appRole) {

		if (StringUtils.hasText(appRole.getSecretId())) {
			return SecretId.provided(appRole.getSecretId());
		}

		if (StringUtils.hasText(vaultProperties.getToken())
				&& StringUtils.hasText(appRole.getRole())) {
			return SecretId.pull(VaultToken.of(vaultProperties.getToken()));
		}

		if (StringUtils.hasText(vaultProperties.getToken())) {
			return SecretId.wrapped(VaultToken.of(vaultProperties.getToken()));
		}

		return SecretId.absent();
	}

	private ClientAuthentication awsEc2Authentication(
			VaultEnvironmentProperties vaultProperties) {

		AwsEc2Properties awsEc2 = vaultProperties.getAwsEc2();

		Nonce nonce = StringUtils.hasText(awsEc2.getNonce())
				? Nonce.provided(awsEc2.getNonce().toCharArray()) : Nonce.generated();

		AwsEc2AuthenticationOptions authenticationOptions = AwsEc2AuthenticationOptions
				.builder().role(awsEc2.getRole()) //
				.path(awsEc2.getAwsEc2Path()) //
				.nonce(nonce) //
				.identityDocumentUri(URI.create(awsEc2.getIdentityDocument())) //
				.build();

		return new AwsEc2Authentication(authenticationOptions, restOperations(),
				this.externalRestOperations);
	}

	private ClientAuthentication awsIamAuthentication(
			VaultEnvironmentProperties vaultProperties) {

		assertClassPresent("com.amazonaws.auth.AWSCredentials", missingClassForAuthMethod(
				"AWSCredentials", "aws-java-sdk-core", AWS_IAM));

		AwsIamProperties awsIam = vaultProperties.getAwsIam();

		AWSCredentialsProvider credentialsProvider = AwsCredentialProvider
				.getAwsCredentialsProvider();

		AwsIamAuthenticationOptionsBuilder builder = AwsIamAuthenticationOptions
				.builder();

		if (StringUtils.hasText(awsIam.getRole())) {
			builder.role(awsIam.getRole());
		}

		if (StringUtils.hasText(awsIam.getServerName())) {
			builder.serverName(awsIam.getServerName());
		}

		if (awsIam.getEndpointUri() != null) {
			builder.endpointUri(awsIam.getEndpointUri());
		}

		builder.path(awsIam.getAwsPath()) //
				.credentialsProvider(credentialsProvider);

		AwsIamAuthenticationOptions options = builder
				.credentialsProvider(credentialsProvider).build();

		return new AwsIamAuthentication(options, restOperations());
	}

	private ClientAuthentication azureMsiAuthentication(
			VaultEnvironmentProperties vaultProperties) {

		AzureMsiProperties azureMsi = vaultProperties.getAzureMsi();

		Assert.hasText(azureMsi.getRole(),
				missingPropertyForAuthMethod("azure-msi.role", AZURE_MSI));

		AzureMsiAuthenticationOptions options = AzureMsiAuthenticationOptions.builder()
				.role(azureMsi.getRole()).build();

		return new AzureMsiAuthentication(options, restOperations(),
				this.externalRestOperations);
	}

	private ClientAuthentication cubbyholeAuthentication() {

		String token = this.vaultProperties.getToken();

		Assert.hasText(token, missingPropertyForAuthMethod("token", CUBBYHOLE));

		CubbyholeAuthenticationOptions options = CubbyholeAuthenticationOptions.builder() //
				.wrapped() //
				.initialToken(VaultToken.of(token)) //
				.build();

		return new CubbyholeAuthentication(options, restOperations());
	}

	private ClientAuthentication gcpGceAuthentication(
			VaultEnvironmentProperties vaultProperties) {

		VaultEnvironmentProperties.GcpGceProperties gcp = vaultProperties.getGcpGce();

		Assert.hasText(gcp.getRole(),
				missingPropertyForAuthMethod("gcp-iam.role", GCP_GCE));

		GcpComputeAuthenticationOptionsBuilder builder = GcpComputeAuthenticationOptions
				.builder().path(gcp.getGcpPath()).role(gcp.getRole());

		if (StringUtils.hasText(gcp.getServiceAccount())) {
			builder.serviceAccount(gcp.getServiceAccount());
		}

		return new GcpComputeAuthentication(builder.build(), restOperations(),
				this.externalRestOperations);
	}

	private ClientAuthentication gcpIamAuthentication(
			VaultEnvironmentProperties vaultProperties) {

		assertClassPresent(
				"com.google.api.client.googleapis.auth.oauth2.GoogleCredential",
				missingClassForAuthMethod("GoogleCredential", "google-api-client",
						GCP_IAM));

		VaultEnvironmentProperties.GcpIamProperties gcp = vaultProperties.getGcpIam();

		Assert.hasText(gcp.getRole(),
				missingPropertyForAuthMethod("gcp-iam.role", GCP_IAM));

		GcpIamAuthenticationOptionsBuilder builder = GcpIamAuthenticationOptions.builder()
				.path(gcp.getGcpPath()).role(gcp.getRole())
				.jwtValidity(gcp.getJwtValidity());

		if (StringUtils.hasText(gcp.getProjectId())) {
			builder.projectId(gcp.getProjectId());
		}

		if (StringUtils.hasText(gcp.getServiceAccountId())) {
			builder.serviceAccountId(gcp.getServiceAccountId());
		}

		GcpCredentialSupplier supplier = GcpCredentialProvider.getGoogleCredential(gcp);
		builder.credential(supplier.get());

		GcpIamAuthenticationOptions options = builder.build();

		return new GcpIamAuthentication(options, restOperations());
	}

	private ClientAuthentication kubernetesAuthentication(
			VaultEnvironmentProperties vaultProperties) {

		KubernetesProperties kubernetes = vaultProperties.getKubernetes();

		Assert.hasText(kubernetes.getRole(),
				missingPropertyForAuthMethod("kubernetes.role", KUBERNETES));
		Assert.hasText(kubernetes.getServiceAccountTokenFile(),
				missingPropertyForAuthMethod("kubernetes.service-account-token-file",
						KUBERNETES));

		KubernetesAuthenticationOptions options = KubernetesAuthenticationOptions
				.builder().path(kubernetes.getKubernetesPath()).role(kubernetes.getRole())
				.jwtSupplier(new KubernetesServiceAccountTokenFile(
						kubernetes.getServiceAccountTokenFile()))
				.build();

		return new KubernetesAuthentication(options, restOperations());
	}

	private ClientAuthentication pcfAuthentication(
			VaultEnvironmentProperties vaultProperties) {

		PcfProperties pcfProperties = vaultProperties.getPcf();

		assertClassPresent("org.bouncycastle.crypto.signers.PSSSigner",
				missingClassForAuthMethod("BouncyCastle", "bcpkix-jdk15on", PCF));
		Assert.hasText(pcfProperties.getRole(),
				missingPropertyForAuthMethod("pcf.role", PCF));

		PcfAuthenticationOptionsBuilder builder = PcfAuthenticationOptions.builder()
				.role(pcfProperties.getRole()).path(pcfProperties.getPcfPath());

		if (pcfProperties.getInstanceCertificate() != null) {
			builder.instanceCertificate(new ResourceCredentialSupplier(
					pcfProperties.getInstanceCertificate()));
		}

		if (pcfProperties.getInstanceKey() != null) {
			builder.instanceKey(
					new ResourceCredentialSupplier(pcfProperties.getInstanceKey()));
		}

		return new PcfAuthentication(builder.build(), restOperations());
	}

	private String missingPropertyForAuthMethod(String propertyName,
			AuthenticationMethod authenticationMethod) {
		return "The '" + VAULT_PROPERTIES_PREFIX + propertyName
				+ "' property must be provided " + "when the " + authenticationMethod
				+ " authentication method is specified.";
	}

	private String missingClassForAuthMethod(String className, String classArtifact,
			AuthenticationMethod authenticationMethod) {
		return className + "(" + classArtifact + ")"
				+ " must be on the classpath when the " + authenticationMethod
				+ " authentication method is specified";
	}

	private void assertClassPresent(String className, String message) {
		Assert.isTrue(ClassUtils.isPresent(className, getClass().getClassLoader()),
				message);
	}

	private static class AwsCredentialProvider {

		private static AWSCredentialsProvider getAwsCredentialsProvider() {

			DefaultAWSCredentialsProviderChain backingCredentialsProvider = DefaultAWSCredentialsProviderChain
					.getInstance();

			// Eagerly fetch credentials preventing lag during the first, actual login.
			AWSCredentials firstAccess = backingCredentialsProvider.getCredentials();

			AtomicReference<AWSCredentials> once = new AtomicReference<>(firstAccess);

			return new AWSCredentialsProvider() {

				@Override
				public AWSCredentials getCredentials() {

					if (once.compareAndSet(firstAccess, null)) {
						return firstAccess;
					}

					return backingCredentialsProvider.getCredentials();
				}

				@Override
				public void refresh() {
					backingCredentialsProvider.refresh();
				}
			};
		}

	}

	@SuppressWarnings("deprecation")
	private static class GcpCredentialProvider {

		public static GcpCredentialSupplier getGoogleCredential(GcpIamProperties gcp) {
			return () -> {

				GcpCredentials credentialProperties = gcp.getCredentials();
				if (credentialProperties.getLocation() != null) {
					return GoogleCredential.fromStream(
							credentialProperties.getLocation().getInputStream());
				}

				if (StringUtils.hasText(credentialProperties.getEncodedKey())) {
					return GoogleCredential.fromStream(new ByteArrayInputStream(Base64
							.getDecoder().decode(credentialProperties.getEncodedKey())));
				}

				return GoogleCredential.getApplicationDefault();
			};
		}

	}

	static class ConfigTokenProviderAuthentication implements ClientAuthentication {

		private final ConfigTokenProvider tokenProvider;

		ConfigTokenProviderAuthentication(ConfigTokenProvider tokenProvider) {
			this.tokenProvider = tokenProvider;
		}

		@Override
		public VaultToken login() throws VaultException {
			String token = tokenProvider.getToken();
			if (!StringUtils.hasLength(token)) {
				throw new IllegalArgumentException(
						"A Vault token must be supplied by a token provider");
			}
			return VaultToken.of(token);
		}

	}

}
