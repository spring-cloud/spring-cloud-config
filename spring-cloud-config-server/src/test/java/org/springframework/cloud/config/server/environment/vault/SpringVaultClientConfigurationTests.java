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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.server.environment.VaultEnvironmentProperties;
import org.springframework.cloud.config.server.environment.vault.SpringVaultClientConfiguration.ConfigTokenProviderAuthentication;
import org.springframework.cloud.config.server.environment.vault.authentication.AppRoleClientAuthenticationProvider;
import org.springframework.cloud.config.server.environment.vault.authentication.AwsEc2ClientAuthenticationProvider;
import org.springframework.cloud.config.server.environment.vault.authentication.AwsIamClientAuthenticationProvider;
import org.springframework.cloud.config.server.environment.vault.authentication.AzureMsiClientAuthenticationProvider;
import org.springframework.cloud.config.server.environment.vault.authentication.CertificateClientAuthenticationProvider;
import org.springframework.cloud.config.server.environment.vault.authentication.CubbyholeClientAuthenticationProvider;
import org.springframework.cloud.config.server.environment.vault.authentication.GcpGceClientAuthenticationProvider;
import org.springframework.cloud.config.server.environment.vault.authentication.GcpIamClientAuthenticationProvider;
import org.springframework.cloud.config.server.environment.vault.authentication.KubernetesClientAuthenticationProvider;
import org.springframework.cloud.config.server.environment.vault.authentication.PcfClientAuthenticationProvider;
import org.springframework.cloud.config.server.environment.vault.authentication.TokenClientAuthenticationProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AwsEc2Authentication;
import org.springframework.vault.authentication.AwsIamAuthentication;
import org.springframework.vault.authentication.AzureMsiAuthentication;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.ClientCertificateAuthentication;
import org.springframework.vault.authentication.CubbyholeAuthentication;
import org.springframework.vault.authentication.GcpComputeAuthentication;
import org.springframework.vault.authentication.GcpIamAuthentication;
import org.springframework.vault.authentication.KubernetesAuthentication;
import org.springframework.vault.authentication.PcfAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.support.SslConfiguration.KeyStoreConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod.APPROLE;
import static org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod.AWS_EC2;
import static org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod.AWS_IAM;
import static org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod.AZURE_MSI;
import static org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod.CERT;
import static org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod.CUBBYHOLE;
import static org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod.GCP_GCE;
import static org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod.GCP_IAM;
import static org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod.KUBERNETES;
import static org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod.PCF;
import static org.springframework.cloud.config.server.environment.VaultEnvironmentProperties.AuthenticationMethod.TOKEN;

class SpringVaultClientConfigurationTests {

	private VaultEnvironmentProperties properties = new VaultEnvironmentProperties();

	private List<SpringVaultClientAuthenticationProvider> authProviders;

	@BeforeEach
	public void setUp() {
		authProviders = Arrays.asList(new AppRoleClientAuthenticationProvider(),
				new AwsEc2ClientAuthenticationProvider(),
				new AwsIamClientAuthenticationProvider(),
				new AzureMsiClientAuthenticationProvider(),
				new CertificateClientAuthenticationProvider(),
				new CubbyholeClientAuthenticationProvider(),
				new GcpGceClientAuthenticationProvider(),
				new GcpIamClientAuthenticationProvider(),
				new KubernetesClientAuthenticationProvider(),
				new PcfClientAuthenticationProvider(),
				new TokenClientAuthenticationProvider());
	}

	@Test
	public void defaultAuthentication() {
		assertClientAuthenticationOfType(properties,
				ConfigTokenProviderAuthentication.class);
	}

	@Test
	public void appRoleAuthentication() {
		properties.setAuthentication(APPROLE);
		properties.getAppRole().setRoleId("role-id");

		assertClientAuthenticationOfType(properties, AppRoleAuthentication.class);
	}

	@Test
	public void awsEc2Authentication() {
		properties.setAuthentication(AWS_EC2);
		properties.getAwsEc2().setRole("server");
		properties.getAwsEc2().setAwsEc2Path("aws-ec2");

		assertClientAuthenticationOfType(properties, AwsEc2Authentication.class);
	}

	@Test
	public void awsIamAuthentication() {
		System.setProperty("aws.accessKeyId", "access-key-id");
		System.setProperty("aws.secretKey", "secret-key");

		properties.setAuthentication(AWS_IAM);
		properties.getAwsIam().setRole("server");
		properties.getAwsIam().setAwsPath("aws-iam");

		assertClientAuthenticationOfType(properties, AwsIamAuthentication.class);
	}

	@Test
	public void azureMsiAuthentication() {
		properties.setAuthentication(AZURE_MSI);
		properties.getAzureMsi().setRole("server");
		properties.getAzureMsi().setAzurePath("azure-msi");

		assertClientAuthenticationOfType(properties, AzureMsiAuthentication.class);
	}

	@Test
	public void clientCertificateAuthentication() {
		properties.setAuthentication(CERT);

		assertClientAuthenticationOfType(properties,
				ClientCertificateAuthentication.class);
	}

	@Test
	public void cubbyholeAuthentication() {
		properties.setAuthentication(CUBBYHOLE);
		properties.setToken("token");

		assertClientAuthenticationOfType(properties, CubbyholeAuthentication.class);
	}

	@Test
	public void gcpComputeAuthentication() {
		properties.setAuthentication(GCP_GCE);
		properties.getGcpGce().setRole("server");
		properties.getGcpGce().setServiceAccount("service-account");

		assertClientAuthenticationOfType(properties, GcpComputeAuthentication.class);
	}

	@Test
	public void gcpIamAuthentication() {
		final String GCE_JSON = "{" + "  \"type\": \"service_account\","
				+ "  \"project_id\": \"project\","
				+ "  \"private_key_id\": \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
				+ "  \"private_key\": \""
				+ "-----BEGIN PRIVATE KEY-----\\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC5qHafKgP/FAKE\\n"
				+ "xfRl0i47zXKbGQJvGAGpcmiXRgeWkZp+kwNwBguOYNwO1qDcmewKvMPazj7EL0hV\\n"
				+ "XMkPxgshZ9ZSxPwg7/XHHcyGCYBJhDc2hyunQvc2WGUOlQKg/nOlq3Dg8d9c/0yF\\n"
				+ "mFOh2K+IrbV6Vqs3nXsupV1q2FbUCVg6NGB0HCdTBZO4e36tmcaWgC1cKTv/Nh+j\\n"
				+ "f2Bf7qBTk0GOL9AjKoa/HP24Yto5zFoOFLU+2ZkVbb8hhO8OMUKW8dLIynqqRqwv\\n"
				+ "oI8e4oiHX3dBvwcS0zZkEUtQiDI80OCbU7ZhPgn5xQpndanD9dZ4TYSgKuXRVTzr\\n"
				+ "1cyoyP7HAgMBAAECggEAV2fOYOSg+V60WvYhN4aaKaFxoT9G/BJrReENCJr5m5N1\\n"
				+ "Dr4b0jOmYSOMtpepJ/J3RB7Wfj63Ihm4jieeqQRt3Q5Lwq/mm4MdTN7kmP4EHZhX\\n"
				+ "fh5pGNfYFwfKm/DfSfhBbe+mtuBobhnrZsHuLbYb/db6J1yCQy6q/azwrAqp5iyq\\n"
				+ "GjNN+WiDIcrydPKKiaszMnb9mNH+Y6Ianx1mvSLT35nBEF6Z4rJVERl26diOoo3I\\n"
				+ "F6WadIwTqcoLo5duUO3SaHKKLcoSEEaGkutuTCHcFOzhvZrXbuIyD567Vp5oVFe9\\n"
				+ "SHN10vceQQdWPh2UsKrVQfIdc70+9tlslka5X6BbIQKBgQDss/APs65NNev2lPdb\\n"
				+ "Jzdd+0YwKEQXeENWkU1xJJkNH/wF0ZGuYxoKafZR0efs1LnrbaPfHveFCnUcDuXU\\n"
				+ "yDnzG2zMw1Q72F8eGHpLItPSh0ZkfSlN58uM1oYMdTFUE6ezlOYEnKIYdhjmQWiE\\n"
				+ "uEa1G4ZW0aX0NLICet597GnLKQKBgQDIyzRVbOOzOxGUgrWT0RPT12VVNCn7edn1\\n"
				+ "UWLKDl4L2uF8vE4g8WW7gwNkVbuO3VPKqdGuCBDfVyyysOOOCDN0IxSxDk3458VY\\n"
				+ "4I3jAuBcgDsixwC28l0QtFnz2yRuD2fsBhLnoSfsM/T2hNbf7atDtMQhbbgU37me\\n"
				+ "X+Ewtr+obwKBgCdbb/IcbUH3UknI0Sw95A3jZvNA7rl8TK4LMPY8IJq3E7+f7foy\\n"
				+ "DjVnEwbdwRN294b2zwWdb4iWiYxlyb9Mn54VlEyjudDNlFs7tLHjk5bw2TqCOSjz\\n"
				+ "/rtnPBi8L7yMHBlXC7v+k1E/6bn3bDqNLWyVrAuphk+Jp4OUDIShl6GpAoGAFIAC\\n"
				+ "mNIkMTFPqyzpIu1Oq+sq0lcgDiezpAMqJdzvpyAys0x6YYyjyVAn8X97Rau9GUzb\\n"
				+ "NnxmVJcO3jOHGAIoVqwaObVvKoFnOZq7gbjSdT82Smes4ADAlasEIAx4nK//+S3p\\n"
				+ "kjJ24/ut/9kyIuyd9qym9Y7BI4hv6AZ79EBEMwsCgYEAgXzq5+NCfJIi6Zduugym\\n"
				+ "iUU3y/3CWc/pLhnw3XZ5r3M5fLXokLhLU6FsNflTpdcf2QoNL58mE0tanPqg09Xh\\n"
				+ "7fHWR/8rISt2TsMlqFjc5rQxWg8yRpdd5Ti/Ln8v7EV3RGbhFlOqlC9hiyqfyd7V\\n"
				+ "qZjZg4zUxPO1I8ae8hbGMWs=\\n" + "-----END PRIVATE KEY-----\\n\","
				+ "  \"client_email\": \"test@example.com\","
				+ "  \"client_id\": \"111111111111111111111\","
				+ "  \"auth_uri\": \"https://accounts.google.com/o/oauth2/auth\","
				+ "  \"token_uri\": \"https://accounts.google.com/o/oauth2/token\","
				+ "  \"auth_provider_x509_cert_url\": \"https://www.googleapis.com/oauth2/v1/certs\","
				+ "  \"client_x509_cert_url\": \"https://www.googleapis.com/robot/v1/metadata/x509/toolsmiths-pcf-sa%40cf-spinnaker.iam.gserviceaccount.com\""
				+ "}";

		properties.setAuthentication(GCP_IAM);
		properties.getGcpIam().setRole("server");
		properties.getGcpIam().setProjectId("project");
		properties.getGcpIam().setServiceAccountId("service-account");
		properties.getGcpIam().getCredentials().setEncodedKey(base64(GCE_JSON));

		assertClientAuthenticationOfType(properties, GcpIamAuthentication.class);
	}

	@Test
	public void kuberneteAuthentication() throws IOException {
		Files.write(Paths.get("target", "token"), "token".getBytes());

		properties.setAuthentication(KUBERNETES);
		properties.getKubernetes().setRole("server");
		properties.getKubernetes().setServiceAccountTokenFile("target/token");

		assertClientAuthenticationOfType(properties, KubernetesAuthentication.class);
	}

	@Test
	public void pcfAuthentication() {
		properties.setAuthentication(PCF);
		properties.getPcf().setRole("my-role");
		properties.getPcf()
				.setInstanceKey(new ClassPathResource("configserver-test.yml"));
		properties.getPcf()
				.setInstanceCertificate(new ClassPathResource("configserver-test.yml"));

		assertClientAuthenticationOfType(properties, PcfAuthentication.class);
	}

	@Test
	public void tokenAuthentication() {
		properties.setAuthentication(TOKEN);
		properties.setToken("token");

		assertClientAuthenticationOfType(properties, TokenAuthentication.class);
	}

	@Test
	public void defaultSslConfiguration() {
		VaultEnvironmentProperties properties = new VaultEnvironmentProperties();

		SpringVaultClientConfiguration configuration = getConfiguration(properties);
		SslConfiguration sslConfiguration = configuration.sslConfiguration();

		assertThat(sslConfiguration.getKeyStoreConfiguration())
				.isEqualTo(KeyStoreConfiguration.unconfigured());
		assertThat(sslConfiguration.getTrustStoreConfiguration())
				.isEqualTo(KeyStoreConfiguration.unconfigured());
	}

	@Test
	public void customSslConfiguration() {
		VaultEnvironmentProperties properties = new VaultEnvironmentProperties();
		properties.getSsl().setKeyStore(new ClassPathResource("ssl-test.jks"));
		properties.getSsl().setKeyStorePassword("password");
		properties.getSsl().setTrustStore(new ClassPathResource("ssl-test.jks"));
		properties.getSsl().setTrustStorePassword("password");

		SpringVaultClientConfiguration configuration = getConfiguration(properties);
		SslConfiguration sslConfiguration = configuration.sslConfiguration();

		KeyStoreConfiguration keyStoreConfiguration = sslConfiguration
				.getKeyStoreConfiguration();
		KeyStoreConfiguration trustStoreConfiguration = sslConfiguration
				.getTrustStoreConfiguration();
		assertThat(keyStoreConfiguration.isPresent()).isTrue();
		assertThat(new String(keyStoreConfiguration.getStorePassword()))
				.isEqualTo("password");
		assertThat(trustStoreConfiguration.isPresent()).isTrue();
		assertThat(new String(trustStoreConfiguration.getStorePassword()))
				.isEqualTo("password");
	}

	private void assertClientAuthenticationOfType(VaultEnvironmentProperties properties,
			Class<? extends ClientAuthentication> type) {
		ClientAuthentication clientAuthentication = getConfiguration(properties)
				.clientAuthentication();

		assertThat(clientAuthentication).isInstanceOf(type);
	}

	private SpringVaultClientConfiguration getConfiguration(
			VaultEnvironmentProperties properties) {
		return new SpringVaultClientConfiguration(properties, () -> null, authProviders);
	}

	private String base64(String value) {
		return new String(Base64.getEncoder().encode(value.getBytes()));
	}

}
