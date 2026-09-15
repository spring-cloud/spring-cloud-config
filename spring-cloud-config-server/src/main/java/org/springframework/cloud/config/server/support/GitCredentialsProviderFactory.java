/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.config.server.support;

import java.io.StringReader;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ClassUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.util.StringUtils.hasText;

/**
 * A CredentialsProvider factory for Git repositories. Can handle AWS CodeCommit
 * repositories and other repositories with username/password.
 *
 * @author Don Laidlaw
 * @author Gareth Clay
 *
 */
public class GitCredentialsProviderFactory {

	protected Log logger = LogFactory.getLog(getClass());

	/**
	 * Enable the AWS Code Commit credentials provider for Git URI's that match the AWS
	 * Code Commit pattern of
	 * https://git-codecommit.${AWS_REGION}.amazonaws.com/${repoPath}. Enabled by default.
	 */
	protected boolean awsCodeCommitEnabled = true;

	/**
	 * If the GitHub App mode should be activated.
	 */
	@Value("${spring.cloud.config.server.git.app:false}")
	public boolean isApp;

	/**
	 * The id of the GitHub app.
	 */
	@Value("${spring.cloud.config.server.git.appId:}")
	public String appId;

	/**
	 * The uri of the GitHub api.
	 */
	@Value("${spring.cloud.config.server.git.apiUri:}")
	public String apiUri;

	/**
	 * The installation id of the GitHub app.
	 */
	@Value("${spring.cloud.config.server.git.installationId:}")
	public String installationId;

	/**
	 * The expiration minutes for the jwt token.
	 */
	@Value("${spring.cloud.config.server.git.jwtExpirationMinutes:0}")
	private int jwtExpirationMinutes;

	private String jwtToken;

	private final ObjectMapper mapper = new ObjectMapper();

	/**
	 * Search for a credential provider that will handle the specified URI. If not found,
	 * and the username or passphrase has text, then create a default using the provided
	 * username and password or passphrase.
	 *
	 * If skipSslValidation is true and the URI has an https scheme, the default
	 * credential provider's behaviour is modified to suppress any SSL validation errors
	 * that occur when communicating via the URI.
	 *
	 * Otherwise null.
	 * @param uri the URI of the repository (cannot be null)
	 * @param username the username provided for the repository (may be null)
	 * @param password the password provided for the repository (may be null)
	 * @param passphrase the passphrase to unlock the ssh private key (may be null)
	 * @param skipSslValidation whether to skip SSL validation when connecting via HTTPS
	 * @return the first matched credentials provider or the default or null.
	 */
	public CredentialsProvider createFor(String uri, String username, String password, String passphrase,
			boolean skipSslValidation) {
		CredentialsProvider provider = null;
		if (awsAvailable() && AwsCodeCommitCredentialProvider.canHandle(uri)) {
			this.logger.debug("Constructing AwsCodeCommitCredentialProvider for URI " + uri);
			AwsCodeCommitCredentialProvider aws = new AwsCodeCommitCredentialProvider();
			aws.setUsername(username);
			aws.setPassword(password);
			provider = aws;
		}
		else if (hasText(username) && password != null) {
			this.logger.debug("Constructing UsernamePasswordCredentialsProvider for URI " + uri);
			password = appToken(username, password);
			provider = new UsernamePasswordCredentialsProvider(username, password.toCharArray());
		}
		else if (hasText(passphrase)) {
			this.logger.debug("Constructing PassphraseCredentialsProvider for URI " + uri);
			provider = new PassphraseCredentialsProvider(passphrase);
		}

		if (skipSslValidation && GitSkipSslValidationCredentialsProvider.canHandle(uri)) {
			this.logger.debug("Constructing GitSkipSslValidationCredentialsProvider for URI " + uri);
			provider = new GitSkipSslValidationCredentialsProvider(provider);
		}

		if (provider == null) {
			this.logger.debug("No credentials provider required for URI " + uri);
		}

		return provider;
	}

	/**
	 * Check to see if the AWS Authentication API is available.
	 * @return true if the com.amazonaws.auth.DefaultAWSCredentialsProviderChain is
	 * present, false otherwise.
	 */
	private boolean awsAvailable() {
		return this.awsCodeCommitEnabled
				&& ClassUtils.isPresent("software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain", null);
	}

	/**
	 * @return the awsCodeCommitEnabled
	 */
	public boolean isAwsCodeCommitEnabled() {
		return this.awsCodeCommitEnabled;
	}

	/**
	 * @param awsCodeCommitEnabled the awsCodeCommitEnabled to set
	 */
	public void setAwsCodeCommitEnabled(boolean awsCodeCommitEnabled) {
		this.awsCodeCommitEnabled = awsCodeCommitEnabled;
	}

	/**
	 * Gets the app token to be used to perform GitHub repository calls with.
	 *
	 * @param username the username to authenticate with
	 * @param password the password to authenticate with
	 * @return the token to be used as password
	 */
	private String appToken(String username, String password) {
		if (isApp && "x-access-token".equals(username)) {
			this.logger.debug("Using GitHub App mode for authentication - please ensure that you use the private key as password.");
			try {
				// if jwtToken is null or expired, generate a new one and set it to the field, otherwise use the existing one
				if (jwtToken == null || Instant.parse(mapper.readTree(jwtToken).get("expires_at").asText()).isBefore(Instant.now())) {
					PrivateKey privateKey = loadPkcs1PrivateKey(password);
					HttpHeaders headers = new HttpHeaders();
					headers.setBearerAuth(generateJwt(appId, privateKey));
					headers.set("Accept", "application/vnd.github+json");
					HttpEntity<String> entity = new HttpEntity<>(headers);
					RestTemplate restTemplate = new RestTemplate();
					UriComponentsBuilder uriBuilder =
						UriComponentsBuilder.fromUriString(apiUri)
							.path("app/installations")
							.pathSegment(installationId)
							.path("access_tokens");
					ResponseEntity<String> response = restTemplate.exchange(
						uriBuilder.build().toUriString(),
						HttpMethod.POST,
						entity,
						String.class
					);
					this.jwtToken = response.getBody();
				}
				password = mapper.readTree(jwtToken).get("token").asText();
			}
			catch (Exception e) {
				this.logger.error("Error while retrieving the app token", e);
			}
		}
		return password;
	}

	/**
	 * Converts the password into a private key.
	 *
	 * @param password the password to convert
	 * @return the PrivateKey
	 */
	private PrivateKey loadPkcs1PrivateKey(String password) {
		try (PEMParser pemParser = new PEMParser(new StringReader(password))) {
			Object object = pemParser.readObject();
			PrivateKeyInfo privateKeyInfo;
			if (object instanceof PEMKeyPair pemKeyPair) {
				privateKeyInfo = pemKeyPair.getPrivateKeyInfo();
			}
			else if (object instanceof PrivateKeyInfo privateKeyInfo1) {
				privateKeyInfo = privateKeyInfo1;
			}
			else {
				throw new IllegalArgumentException("Unknown PEM object: " + object.getClass());
			}
			byte[] pkcs8Bytes = privateKeyInfo.getEncoded();
			PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pkcs8Bytes);
			KeyFactory kf = KeyFactory.getInstance("RSA");
			return kf.generatePrivate(spec);
		}
		catch (Exception e) {
			throw new IllegalStateException("An error occurred while loading the private key", e);
		}
	}

	/**
	 * Generates a jwt token based on the appId and the privateKey.
	 *
	 * @param appId the id of the GitHub App
	 * @param privateKey the private key to sign with
	 * @return the jwt to be used for the api call
	 */
	private String generateJwt(String appId, PrivateKey privateKey) {
		Instant now = Instant.now();
		return Jwts.builder()
			.issuer(appId)
			.issuedAt(Date.from(now))
			.expiration(Date.from(now.plus(jwtExpirationMinutes, ChronoUnit.MINUTES)))
			.signWith(privateKey)
			.compact();
	}
}
