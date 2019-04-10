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

package org.springframework.cloud.config.server.support;

import java.net.URI;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.util.ValidationUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

import static org.springframework.util.StringUtils.hasText;

/**
 * Provides a jgit {@link CredentialsProvider} implementation that can provide the
 * appropriate credentials to connect to an AWS CodeCommit repository.
 * <p>
 * From the command line, you can configure git to use AWS code commit with a credential
 * helper. However, jgit does not support credential helper commands, but it does provider
 * a CredentialsProvider abstract class we can extend. Connecting to an AWS CodeCommit
 * (codecommit) repository requires an AWS access key and secret key. These are used to
 * calculate a signature for the git request. The AWS access key is used as the codecommit
 * username, and the calculated signature is used as the password. The process for
 * calculating this signature is documented very well at
 * https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html.
 * </p>
 * Connecting to an AWS CodeCommit (codecommit) repository requires an AWS access key and
 * secret key. These are used to calculate a signature for the git request. The AWS access
 * key is used as the codecommit username, and the calculated signature is used as the
 * password. The process for calculating this signature is documented very well at
 * https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html.
 *
 * @author Don Laidlaw
 */
public class AwsCodeCommitCredentialProvider extends CredentialsProvider {

	private static final String SHA_256 = "SHA-256"; //$NON-NLS-1$

	private static final String UTF8 = "UTF8"; //$NON-NLS-1$

	private static final String HMAC_SHA256 = "HmacSHA256"; //$NON-NLS-1$

	private static final char[] hexArray = "0123456789abcdef".toCharArray(); //$NON-NLS-1$

	protected Log logger = LogFactory.getLog(getClass());

	/**
	 * The AWSCredentialsProvider will be used to provide the access key and secret key if
	 * they are not specified.
	 */
	private AWSCredentialsProvider awsCredentialProvider;

	/**
	 * If the access and secret keys are provided, then the AWSCredentialsProvider will
	 * not be used. The username is the awsAccessKeyId.
	 */
	private String username;

	/**
	 * If the access and secret keys are provided, then the AWSCredentialsProvider will
	 * not be used. The password is the awsSecretKey.
	 */
	private String password;

	/**
	 * Calculate the AWS CodeCommit password for the provided URI and AWS secret key. This
	 * uses the algorithm published by AWS at
	 * https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html
	 * @param uri the codecommit repository uri
	 * @param awsSecretKey the aws secret key
	 * @return the password to use in the git request
	 */
	protected static String calculateCodeCommitPassword(URIish uri, String awsSecretKey) {
		String[] split = uri.getHost().split("\\.");
		if (split.length < 4) {
			throw new CredentialException("Cannot detect AWS region from URI", null);
		}
		String region = split[1];

		Date now = new Date();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

		String dateStamp = dateFormat.format(now);
		String shortDateStamp = dateStamp.substring(0, 8);

		String codeCommitPassword;
		try {
			StringBuilder stringToSign = new StringBuilder();
			stringToSign.append("AWS4-HMAC-SHA256\n").append(dateStamp).append("\n")
					.append(shortDateStamp).append("/").append(region)
					.append("/codecommit/aws4_request\n")
					.append(bytesToHexString(canonicalRequestDigest(uri)));

			byte[] signedRequest = sign(awsSecretKey, shortDateStamp, region,
					stringToSign.toString());
			codeCommitPassword = dateStamp + "Z" + bytesToHexString(signedRequest);
		}
		catch (Exception e) {
			throw new CredentialException("Error calculating AWS CodeCommit password", e);
		}

		return codeCommitPassword;
	}

	private static byte[] hmacSha256(String data, byte[] key) throws Exception {
		String algorithm = HMAC_SHA256;
		Mac mac = Mac.getInstance(algorithm);
		mac.init(new SecretKeySpec(key, algorithm));
		return mac.doFinal(data.getBytes(UTF8));
	}

	private static byte[] sign(String secret, String shortDateStamp, String region,
			String toSign) throws Exception {
		byte[] kSecret = ("AWS4" + secret).getBytes(UTF8);
		byte[] kDate = hmacSha256(shortDateStamp, kSecret);
		byte[] kRegion = hmacSha256(region, kDate);
		byte[] kService = hmacSha256("codecommit", kRegion);
		byte[] kSigning = hmacSha256("aws4_request", kService);
		return hmacSha256(toSign, kSigning);
	}

	/**
	 * Creates a message digest.
	 * @param uri uri to process
	 * @return a message digest
	 * @throws NoSuchAlgorithmException when the SHA 256 algorithm is not found
	 */
	private static byte[] canonicalRequestDigest(URIish uri)
			throws NoSuchAlgorithmException {
		StringBuilder canonicalRequest = new StringBuilder();
		canonicalRequest.append("GIT\n") // codecommit uses GIT as the request method
				.append(uri.getPath()).append("\n") // URI request path
				.append("\n") // Query string, always empty for codecommit
				// Next is canonical headers, codecommit only requires the host header
				.append("host:").append(uri.getHost()).append("\n").append("\n") // canonical
																					// headers
																					// are
																					// always
																					// terminated
																					// by
																					// newline
				.append("host\n"); // The list of canonical headers, only one for
									// codecommit

		MessageDigest digest = MessageDigest.getInstance(SHA_256);

		return digest.digest(canonicalRequest.toString().getBytes());
	}

	/**
	 * Convert bytes to a hex string.
	 * @param bytes the bytes
	 * @return a string of hex characters encoding the bytes.
	 */
	private static String bytesToHexString(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	/**
	 * This provider can handle uris like
	 * https://git-codecommit.$AWS_REGION.amazonaws.com/v1/repos/$REPO .
	 * @param uri uri to parse
	 * @return {@code true} if the URI can be handled
	 */
	public static boolean canHandle(String uri) {
		if (!hasText(uri)) {
			return false;
		}

		try {
			URL url = new URL(uri);
			URI u = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(),
					url.getPort(), url.getPath(), url.getQuery(), url.getRef());
			if (u.getScheme().equals("https")) {
				String host = u.getHost();
				if (host.endsWith(".amazonaws.com")
						&& host.startsWith("git-codecommit.")) {
					return true;
				}
			}
		}
		catch (Throwable t) {
			// ignore all, we can't handle it
		}

		return false;
	}

	/**
	 * This credentials provider cannot run interactively.
	 * @return false
	 * @see org.eclipse.jgit.transport.CredentialsProvider#isInteractive()
	 */
	@Override
	public boolean isInteractive() {
		return false;
	}

	/**
	 * We support username and password credential items only.
	 * @see org.eclipse.jgit.transport.CredentialsProvider#supports(org.eclipse.jgit.transport.CredentialItem[])
	 */
	@Override
	public boolean supports(CredentialItem... items) {
		for (CredentialItem i : items) {
			if (i instanceof CredentialItem.Username) {
				continue;
			}
			else if (i instanceof CredentialItem.Password) {
				continue;
			}
			else {
				return false;
			}
		}
		return true;
	}

	/**
	 * Get the AWSCredentials. If an AWSCredentialProvider was specified, use that,
	 * otherwise, create a new AWSCredentialsProvider. If the username and password are
	 * provided, then use those directly as AWSCredentials. Otherwise us the
	 * {@link DefaultAWSCredentialsProviderChain} as is standard with AWS applications.
	 * @return the AWS credentials.
	 */
	private AWSCredentials retrieveAwsCredentials() {
		if (this.awsCredentialProvider == null) {
			if (this.username != null && this.password != null) {
				this.logger.debug("Creating a static AWSCredentialsProvider");
				this.awsCredentialProvider = new AWSStaticCredentialsProvider(
						new BasicAWSCredentials(this.username, this.password));
			}
			else {
				this.logger.debug("Creating a default AWSCredentialsProvider");
				this.awsCredentialProvider = new DefaultAWSCredentialsProviderChain();
			}
		}
		return this.awsCredentialProvider.getCredentials();
	}

	/**
	 * Get the username and password to use for the given uri.
	 * @see org.eclipse.jgit.transport.CredentialsProvider#get(org.eclipse.jgit.transport.URIish,
	 * org.eclipse.jgit.transport.CredentialItem[])
	 */
	@Override
	public boolean get(URIish uri, CredentialItem... items)
			throws UnsupportedCredentialItem {
		String codeCommitPassword;
		String awsAccessKey;
		String awsSecretKey;
		try {
			AWSCredentials awsCredentials = retrieveAwsCredentials();
			StringBuilder awsKey = new StringBuilder();
			awsKey.append(awsCredentials.getAWSAccessKeyId());
			awsSecretKey = awsCredentials.getAWSSecretKey();
			if (awsCredentials instanceof AWSSessionCredentials) {
				AWSSessionCredentials sessionCreds = (AWSSessionCredentials) awsCredentials;
				if (sessionCreds.getSessionToken() != null) {
					awsKey.append('%').append(sessionCreds.getSessionToken());
				}
			}
			awsAccessKey = awsKey.toString();
		}
		catch (Throwable t) {
			this.logger.warn("Unable to retrieve AWS Credentials", t);
			return false;
		}
		try {
			codeCommitPassword = calculateCodeCommitPassword(uri, awsSecretKey);
		}
		catch (Throwable t) {
			this.logger.warn("Error calculating the AWS CodeCommit password", t);
			return false;
		}

		for (CredentialItem i : items) {
			if (i instanceof CredentialItem.Username) {
				((CredentialItem.Username) i).setValue(awsAccessKey);
				this.logger.trace("Returning username " + awsAccessKey);
				continue;
			}
			if (i instanceof CredentialItem.Password) {
				((CredentialItem.Password) i).setValue(codeCommitPassword.toCharArray());
				this.logger.trace("Returning password " + codeCommitPassword);
				continue;
			}
			if (i instanceof CredentialItem.StringType
					&& i.getPromptText().equals("Password: ")) { //$NON-NLS-1$
				((CredentialItem.StringType) i).setValue(codeCommitPassword);
				this.logger.trace("Returning password string " + codeCommitPassword);
				continue;
			}
			throw new UnsupportedCredentialItem(uri,
					i.getClass().getName() + ":" + i.getPromptText()); //$NON-NLS-1$
		}

		return true;
	}

	/**
	 * Throw out cached data and force retrieval of AWS credentials.
	 * @param uri This parameter is not used in this implementation.
	 */
	@Override
	public void reset(URIish uri) {
		// Should throw out cached info.
		// Note that even though the credentials (password) we calculate here is
		// valid for 15 minutes, we do not cache it. Instead we just re-calculate
		// it each time we need it. However, the AWSCredentialProvider will cache
		// its AWSCredentials object.
	}

	/**
	 * @return the awsCredentialProvider
	 */
	public AWSCredentialsProvider getAwsCredentialProvider() {
		return this.awsCredentialProvider;
	}

	/**
	 * @param awsCredentialProvider the awsCredentialProvider to set
	 */
	public void setAwsCredentialProvider(AWSCredentialsProvider awsCredentialProvider) {
		this.awsCredentialProvider = awsCredentialProvider;
	}

	/**
	 * @return the username
	 */
	public String getUsername() {
		return this.username;
	}

	/**
	 * @param username the username to set
	 */
	public void setUsername(String username) {
		this.username = username;
	}

	/**
	 * @return the password
	 */
	public String getPassword() {
		return this.password;
	}

	/**
	 * @param password the password to set
	 */
	public void setPassword(String password) {
		this.password = password;
	}

	/**
	 * Simple implementation of AWSCredentialsProvider that just wraps static
	 * AWSCredentials. AWS Actually provides this class in newer versions of the AWS API.
	 */
	public class AWSStaticCredentialsProvider implements AWSCredentialsProvider {

		private final AWSCredentials credentials;

		public AWSStaticCredentialsProvider(AWSCredentials credentials) {
			this.credentials = ValidationUtils.assertNotNull(credentials, "credentials");
		}

		public AWSCredentials getCredentials() {
			return this.credentials;
		}

		public void refresh() {
			// Nothing to do for static credentials.
		}

	}

}
