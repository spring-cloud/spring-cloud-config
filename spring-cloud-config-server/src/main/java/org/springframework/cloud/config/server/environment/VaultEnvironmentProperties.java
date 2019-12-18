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

package org.springframework.cloud.config.server.environment;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import javax.validation.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.config.server.proxy.ProxyHostProperties;
import org.springframework.cloud.config.server.support.HttpEnvironmentRepositoryProperties;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

/**
 * @author Dylan Roberts
 * @author Haroun Pacquee
 * @author Scott Frederick
 */
@ConfigurationProperties("spring.cloud.config.server.vault")
public class VaultEnvironmentProperties implements HttpEnvironmentRepositoryProperties {

	/** Vault host. Defaults to 127.0.0.1. */
	private String host = "127.0.0.1";

	/** Vault port. Defaults to 8200. */
	private Integer port = 8200;

	/** Vault scheme. Defaults to http. */
	private String scheme = "http";

	/** Timeout (in seconds) for obtaining HTTP connection, defaults to 5 seconds. */
	private int timeout = 5;

	/** Vault backend. Defaults to secret. */
	private String backend = "secret";

	/**
	 * The key in vault shared by all applications. Defaults to application. Set to empty
	 * to disable.
	 */
	private String defaultKey = "application";

	/** Vault profile separator. Defaults to comma. */
	private String profileSeparator = ",";

	/**
	 * Flag to indicate that SSL certificate validation should be bypassed when
	 * communicating with a repository served over an HTTPS connection.
	 */
	private boolean skipSslValidation = false;

	/**
	 * HTTP proxy configuration.
	 */
	private Map<ProxyHostProperties.ProxyForScheme, ProxyHostProperties> proxy = new HashMap<>();

	private int order = Ordered.LOWEST_PRECEDENCE;

	/**
	 * Value to indicate which version of Vault kv backend is used. Defaults to 1.
	 */
	private int kvVersion = 1;

	/**
	 * The value of the Vault X-Vault-Namespace header. Defaults to null. This a Vault
	 * Enterprise feature only.
	 */
	private String namespace;

	/**
	 * Static vault token. Required if {@link #authentication} is {@code TOKEN}.
	 */
	private String token;

	private AppRoleProperties appRole = new AppRoleProperties();

	private AwsEc2Properties awsEc2 = new AwsEc2Properties();

	private AwsIamProperties awsIam = new AwsIamProperties();

	private AzureMsiProperties azureMsi = new AzureMsiProperties();

	private GcpGceProperties gcpGce = new GcpGceProperties();

	private GcpIamProperties gcpIam = new GcpIamProperties();

	private KubernetesProperties kubernetes = new KubernetesProperties();

	private PcfProperties pcf = new PcfProperties();

	private Ssl ssl = new Ssl();

	private AuthenticationMethod authentication;

	public String getHost() {
		return this.host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public Integer getPort() {
		return this.port;
	}

	public void setPort(Integer port) {
		this.port = port;
	}

	public String getScheme() {
		return this.scheme;
	}

	public void setScheme(String scheme) {
		this.scheme = scheme;
	}

	public String getBackend() {
		return this.backend;
	}

	public void setBackend(String backend) {
		this.backend = backend;
	}

	public String getDefaultKey() {
		return this.defaultKey;
	}

	public void setDefaultKey(String defaultKey) {
		this.defaultKey = defaultKey;
	}

	public String getProfileSeparator() {
		return this.profileSeparator;
	}

	public void setProfileSeparator(String profileSeparator) {
		this.profileSeparator = profileSeparator;
	}

	@Override
	public boolean isSkipSslValidation() {
		return this.skipSslValidation;
	}

	public void setSkipSslValidation(boolean skipSslValidation) {
		this.skipSslValidation = skipSslValidation;
	}

	@Override
	public Map<ProxyHostProperties.ProxyForScheme, ProxyHostProperties> getProxy() {
		return this.proxy;
	}

	public void setProxy(
			Map<ProxyHostProperties.ProxyForScheme, ProxyHostProperties> proxy) {
		this.proxy = proxy;
	}

	public int getOrder() {
		return this.order;
	}

	@Override
	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getTimeout() {
		return this.timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public int getKvVersion() {
		return this.kvVersion;
	}

	public void setKvVersion(int kvVersion) {
		this.kvVersion = kvVersion;
	}

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public String getToken() {
		return this.token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public AppRoleProperties getAppRole() {
		return this.appRole;
	}

	public AwsEc2Properties getAwsEc2() {
		return this.awsEc2;
	}

	public AwsIamProperties getAwsIam() {
		return this.awsIam;
	}

	public AzureMsiProperties getAzureMsi() {
		return this.azureMsi;
	}

	public GcpGceProperties getGcpGce() {
		return this.gcpGce;
	}

	public GcpIamProperties getGcpIam() {
		return this.gcpIam;
	}

	public KubernetesProperties getKubernetes() {
		return this.kubernetes;
	}

	public PcfProperties getPcf() {
		return this.pcf;
	}

	public Ssl getSsl() {
		return this.ssl;
	}

	public void setAuthentication(AuthenticationMethod authentication) {
		this.authentication = authentication;
	}

	public AuthenticationMethod getAuthentication() {
		return authentication;
	}

	public enum AuthenticationMethod {

		/**
		 * Vault AppRole machine authentication.
		 */
		APPROLE,

		/**
		 * Amazon Web Services Compute authentication.
		 */
		AWS_EC2,

		/**
		 * Amazon Web Services IAM authentication.
		 */
		AWS_IAM,

		/**
		 * Azure Cloud MSI authentication.
		 */
		AZURE_MSI,

		/**
		 * TLS certificate authentication.
		 */
		CERT,

		/**
		 * Cubbyhole token authentication.
		 */
		CUBBYHOLE,

		/**
		 * Google Cloud Compute authentication.
		 */
		GCP_GCE,

		/**
		 * Google Cloud IAM authentication.
		 */
		GCP_IAM,

		/**
		 * Kubernetes service account token authentication.
		 */
		KUBERNETES,

		/**
		 * Cloud Foundry instance identity certificate authentication.
		 */
		PCF,

		/**
		 * Static token authentication.
		 */
		TOKEN

	}

	/**
	 * AppRole properties.
	 */
	@Validated
	public static class AppRoleProperties {

		/**
		 * Mount path of the AppRole authentication backend.
		 */
		private String appRolePath = "approle";

		/**
		 * Name of the role, optional, used for pull-mode.
		 */
		private String role = "";

		/**
		 * The RoleId.
		 */
		private String roleId = null;

		/**
		 * The SecretId.
		 */
		private String secretId = null;

		public String getAppRolePath() {
			return this.appRolePath;
		}

		public String getRole() {
			return this.role;
		}

		public String getRoleId() {
			return this.roleId;
		}

		public String getSecretId() {
			return this.secretId;
		}

		public void setAppRolePath(String appRolePath) {
			this.appRolePath = appRolePath;
		}

		public void setRole(String role) {
			this.role = role;
		}

		public void setRoleId(String roleId) {
			this.roleId = roleId;
		}

		public void setSecretId(String secretId) {
			this.secretId = secretId;
		}

	}

	/**
	 * AWS-EC2 properties.
	 */
	@Validated
	public static class AwsEc2Properties {

		/**
		 * URL of the AWS-EC2 PKCS7 identity document.
		 */
		@NotEmpty
		private String identityDocument = "http://169.254.169.254/latest/dynamic/instance-identity/pkcs7";

		/**
		 * Mount path of the AWS-EC2 authentication backend.
		 */
		@NotEmpty
		private String awsEc2Path = "aws-ec2";

		/**
		 * Name of the role, optional.
		 */
		private String role = "";

		/**
		 * Nonce used for AWS-EC2 authentication. An empty nonce defaults to nonce
		 * generation.
		 */
		private String nonce;

		public String getIdentityDocument() {
			return this.identityDocument;
		}

		public String getAwsEc2Path() {
			return this.awsEc2Path;
		}

		public String getRole() {
			return this.role;
		}

		public String getNonce() {
			return this.nonce;
		}

		public void setIdentityDocument(String identityDocument) {
			this.identityDocument = identityDocument;
		}

		public void setAwsEc2Path(String awsEc2Path) {
			this.awsEc2Path = awsEc2Path;
		}

		public void setRole(String role) {
			this.role = role;
		}

		public void setNonce(String nonce) {
			this.nonce = nonce;
		}

	}

	/**
	 * AWS-IAM properties.
	 */
	public static class AwsIamProperties {

		/**
		 * Mount path of the AWS authentication backend.
		 */
		@NotEmpty
		private String awsPath = "aws";

		/**
		 * Name of the role, optional. Defaults to the friendly IAM name if not set.
		 */
		private String role = "";

		/**
		 * Name of the server used to set {@code X-Vault-AWS-IAM-Server-ID} header in the
		 * headers of login requests.
		 */
		private String serverName;

		/**
		 * STS server URI.
		 *
		 * @since 2.2
		 */
		private URI endpointUri;

		public String getAwsPath() {
			return this.awsPath;
		}

		public String getRole() {
			return this.role;
		}

		public String getServerName() {
			return this.serverName;
		}

		public void setAwsPath(String awsPath) {
			this.awsPath = awsPath;
		}

		public void setRole(String role) {
			this.role = role;
		}

		public void setServerName(String serverName) {
			this.serverName = serverName;
		}

		public URI getEndpointUri() {
			return this.endpointUri;
		}

		public void setEndpointUri(URI endpointUri) {
			this.endpointUri = endpointUri;
		}

	}

	/**
	 * Azure MSI properties.
	 */
	public static class AzureMsiProperties {

		/**
		 * Mount path of the Azure MSI authentication backend.
		 */
		@NotEmpty
		private String azurePath = "azure";

		/**
		 * Name of the role.
		 */
		private String role = "";

		public String getAzurePath() {
			return this.azurePath;
		}

		public String getRole() {
			return this.role;
		}

		public void setAzurePath(String azurePath) {
			this.azurePath = azurePath;
		}

		public void setRole(String role) {
			this.role = role;
		}

	}

	/**
	 * GCP-GCE properties.
	 */
	public static class GcpGceProperties {

		/**
		 * Mount path of the Kubernetes authentication backend.
		 */
		@NotEmpty
		private String gcpPath = "gcp";

		/**
		 * Name of the role against which the login is being attempted.
		 */
		private String role = "";

		/**
		 * Optional service account id. Using the default id if left unconfigured.
		 */
		private String serviceAccount = "";

		public String getGcpPath() {
			return this.gcpPath;
		}

		public String getRole() {
			return this.role;
		}

		public String getServiceAccount() {
			return this.serviceAccount;
		}

		public void setGcpPath(String gcpPath) {
			this.gcpPath = gcpPath;
		}

		public void setRole(String role) {
			this.role = role;
		}

		public void setServiceAccount(String serviceAccount) {
			this.serviceAccount = serviceAccount;
		}

	}

	/**
	 * GCP-IAM properties.
	 */
	public static class GcpIamProperties {

		/**
		 * Credentials configuration.
		 */
		private final GcpCredentials credentials = new GcpCredentials();

		/**
		 * Mount path of the Kubernetes authentication backend.
		 */
		@NotEmpty
		private String gcpPath = "gcp";

		/**
		 * Name of the role against which the login is being attempted.
		 */
		private String role = "";

		/**
		 * Overrides the GCP project Id.
		 */
		private String projectId = "";

		/**
		 * Overrides the GCP service account Id.
		 */
		private String serviceAccountId = "";

		/**
		 * Validity of the JWT token.
		 */
		private Duration jwtValidity = Duration.ofMinutes(15);

		public GcpCredentials getCredentials() {
			return this.credentials;
		}

		public String getGcpPath() {
			return this.gcpPath;
		}

		public String getRole() {
			return this.role;
		}

		public String getProjectId() {
			return this.projectId;
		}

		public String getServiceAccountId() {
			return this.serviceAccountId;
		}

		public Duration getJwtValidity() {
			return this.jwtValidity;
		}

		public void setGcpPath(String gcpPath) {
			this.gcpPath = gcpPath;
		}

		public void setRole(String role) {
			this.role = role;
		}

		public void setProjectId(String projectId) {
			this.projectId = projectId;
		}

		public void setServiceAccountId(String serviceAccountId) {
			this.serviceAccountId = serviceAccountId;
		}

		public void setJwtValidity(Duration jwtValidity) {
			this.jwtValidity = jwtValidity;
		}

	}

	/**
	 * GCP credential properties.
	 */
	public static class GcpCredentials {

		/**
		 * Location of the OAuth2 credentials private key.
		 *
		 * <p>
		 * Since this is a Resource, the private key can be in a multitude of locations,
		 * such as a local file system, classpath, URL, etc.
		 */
		private Resource location;

		/**
		 * The base64 encoded contents of an OAuth2 account private key in JSON format.
		 */
		private String encodedKey;

		public Resource getLocation() {
			return this.location;
		}

		public String getEncodedKey() {
			return this.encodedKey;
		}

		public void setLocation(Resource location) {
			this.location = location;
		}

		public void setEncodedKey(String encodedKey) {
			this.encodedKey = encodedKey;
		}

	}

	/**
	 * Kubernetes properties.
	 */
	public static class KubernetesProperties {

		/**
		 * Mount path of the Kubernetes authentication backend.
		 */
		@NotEmpty
		private String kubernetesPath = "kubernetes";

		/**
		 * Name of the role against which the login is being attempted.
		 */
		private String role = "";

		/**
		 * Path to the service account token file.
		 */
		@NotEmpty
		private String serviceAccountTokenFile = "/var/run/secrets/kubernetes.io/serviceaccount/token";

		public String getKubernetesPath() {
			return this.kubernetesPath;
		}

		public String getRole() {
			return this.role;
		}

		public String getServiceAccountTokenFile() {
			return this.serviceAccountTokenFile;
		}

		public void setKubernetesPath(String kubernetesPath) {
			this.kubernetesPath = kubernetesPath;
		}

		public void setRole(String role) {
			this.role = role;
		}

		public void setServiceAccountTokenFile(String serviceAccountTokenFile) {
			this.serviceAccountTokenFile = serviceAccountTokenFile;
		}

	}

	/**
	 * PCF properties.
	 */
	public static class PcfProperties {

		/**
		 * Mount path of the Kubernetes authentication backend.
		 */
		@NotEmpty
		private String pcfPath = "pcf";

		/**
		 * Name of the role against which the login is being attempted.
		 */
		private String role = "";

		/**
		 * Path to the instance certificate (PEM). Defaults to {@code CF_INSTANCE_CERT}
		 * env variable.
		 */
		private Resource instanceCertificate;

		/**
		 * Path to the instance key (PEM). Defaults to {@code CF_INSTANCE_KEY} env
		 * variable.
		 */
		private Resource instanceKey;

		public String getPcfPath() {
			return this.pcfPath;
		}

		public void setPcfPath(String pcfPath) {
			this.pcfPath = pcfPath;
		}

		public String getRole() {
			return this.role;
		}

		public void setRole(String role) {
			this.role = role;
		}

		public Resource getInstanceCertificate() {
			return this.instanceCertificate;
		}

		public void setInstanceCertificate(Resource instanceCertificate) {
			this.instanceCertificate = instanceCertificate;
		}

		public Resource getInstanceKey() {
			return this.instanceKey;
		}

		public void setInstanceKey(Resource instanceKey) {
			this.instanceKey = instanceKey;
		}

	}

	/**
	 * SSL properties.
	 */
	@Validated
	public static class Ssl {

		/**
		 * Trust store that holds certificates and private keys.
		 */
		private Resource keyStore;

		/**
		 * Password used to access the key store.
		 */
		private String keyStorePassword;

		/**
		 * Trust store that holds SSL certificates.
		 */
		private Resource trustStore;

		/**
		 * Password used to access the trust store.
		 */
		private String trustStorePassword;

		/**
		 * Mount path of the TLS cert authentication backend.
		 */
		@NotEmpty
		private String certAuthPath = "cert";

		public Resource getKeyStore() {
			return this.keyStore;
		}

		public String getKeyStorePassword() {
			return this.keyStorePassword;
		}

		public Resource getTrustStore() {
			return this.trustStore;
		}

		public String getTrustStorePassword() {
			return this.trustStorePassword;
		}

		public String getCertAuthPath() {
			return this.certAuthPath;
		}

		public void setKeyStore(Resource keyStore) {
			this.keyStore = keyStore;
		}

		public void setKeyStorePassword(String keyStorePassword) {
			this.keyStorePassword = keyStorePassword;
		}

		public void setTrustStore(Resource trustStore) {
			this.trustStore = trustStore;
		}

		public void setTrustStorePassword(String trustStorePassword) {
			this.trustStorePassword = trustStorePassword;
		}

		public void setCertAuthPath(String certAuthPath) {
			this.certAuthPath = certAuthPath;
		}

	}

}
