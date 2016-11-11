/**
 * ---Begin Copyright Notice---20160101T000000Z
 *
 * NOTICE
 *
 * THIS SOFTWARE IS THE PROPERTY OF AND CONTAINS CONFIDENTIAL INFORMATION OF
 * INFOR AND/OR ITS AFFILIATES OR SUBSIDIARIES AND SHALL NOT BE DISCLOSED
 * WITHOUT PRIOR WRITTEN PERMISSION. LICENSED CUSTOMERS MAY COPY AND ADAPT
 * THIS SOFTWARE FOR THEIR OWN USE IN ACCORDANCE WITH THE TERMS OF THEIR
 * SOFTWARE LICENSE AGREEMENT. ALL OTHER RIGHTS RESERVED.
 *
 * (c) COPYRIGHT 2016 INFOR. ALL RIGHTS RESERVED. THE WORD AND DESIGN MARKS
 * SET FORTH HEREIN ARE TRADEMARKS AND/OR REGISTERED TRADEMARKS OF INFOR
 * AND/OR ITS AFFILIATES AND SUBSIDIARIES. ALL RIGHTS RESERVED. ALL OTHER
 * TRADEMARKS LISTED HEREIN ARE THE PROPERTY OF THEIR RESPECTIVE OWNERS.
 *
 * ---End Copyright Notice---
 */
package org.springframework.cloud.config.server.credentials;

import static org.springframework.util.StringUtils.hasText;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;

//import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;

/**
 * @author dlaidlaw
 *
 */
public class AwsCodeCommitCredentialProvider extends CredentialsProvider {
	
	private final static char[] hexArray = "0123456789abcdef".toCharArray();
	
	/**
	 * The AWSCredentialsProvider will be used to provide the access key and secret
	 * key if they are not specified.
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
	 * This credentials provider cannot run interactively.
	 * @return false
	 * @see org.eclipse.jgit.transport.CredentialsProvider#isInteractive()
	 */
	@Override
	public boolean isInteractive() {
		return false;
	}


	/**
	 * We support username and password only.
	 * @see org.eclipse.jgit.transport.CredentialsProvider#supports(org.eclipse.jgit.transport.CredentialItem[])
	 */
	@Override
	public boolean supports(CredentialItem... items) {
		for (CredentialItem i : items) {
			if (i instanceof CredentialItem.Username) {
				continue;
			} else if (i instanceof CredentialItem.Password) {
				continue;
			} else {
				return false;
			}
		}
		return true;
	}
	
	private AWSCredentials awsCredentials() {
		if (awsCredentialProvider == null) {
			if (username != null && password != null) {
				awsCredentialProvider = new AWSStaticCredentialsProvider(new BasicAWSCredentials(username, password));
			} else {
				awsCredentialProvider = new DefaultAWSCredentialsProviderChain();
			}
		}
		return awsCredentialProvider.getCredentials();
	}


	/**
	 * Get the username and password to use for the given uri.
	 * @see org.eclipse.jgit.transport.CredentialsProvider#get(org.eclipse.jgit.transport.URIish, org.eclipse.jgit.transport.CredentialItem[])
	 */
	@Override
	public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {
		AWSCredentials awsCredentials = awsCredentials();
		String awsKey = awsCredentials.getAWSAccessKeyId();
		String awsSecretKey = awsCredentials.getAWSSecretKey();
		
        String[] split = uri.getHost().split("\\.");
        if (split.length < 3) {
            throw new UnsupportedCredentialItem(uri, "Can not detect region from URI");
        }
        String region = split[1];

        Date now = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        String dateStamp = dateFormat.format(now);
        String shortDateStamp = dateStamp.substring(0, 8);
        
        String toSign = "AWS4-HMAC-SHA256\n" +
                dateStamp + "\n" +
                shortDateStamp + "/" + region + "/codecommit/aws4_request\n"
                + bytesToHex(digest(uri));

		byte[] signedRequest;
		try {
			signedRequest = sign(awsSecretKey, shortDateStamp, region, toSign);
		} catch (Exception e) {
			throw new CredentialException("Error calculating AWS Signature", e);
		}
		String pass = dateStamp + "Z" + bytesToHex(signedRequest);

		for (CredentialItem i : items) {
			if (i instanceof CredentialItem.Username) {
				((CredentialItem.Username) i).setValue(awsKey);
				continue;
			}
			if (i instanceof CredentialItem.Password) {
				((CredentialItem.Password) i).setValue(pass.toCharArray());
				continue;
			}
			if (i instanceof CredentialItem.StringType) {
				if (i.getPromptText().equals("Password: ")) { //$NON-NLS-1$
					((CredentialItem.StringType) i).setValue(new String(pass));
					continue;
				}
			}
			throw new UnsupportedCredentialItem(uri, i.getClass().getName() + ":" + i.getPromptText()); //$NON-NLS-1$
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
	}

	private static byte[] hmacSha256(String data, byte[] key) throws Exception {
	    String algorithm="HmacSHA256";
	    Mac mac = Mac.getInstance(algorithm);
	    mac.init(new SecretKeySpec(key, algorithm));
	    return mac.doFinal(data.getBytes("UTF8"));
	}
	
	private static byte[] sign(String secret, String shortDateStamp, String region, String toSign) throws Exception {
	    byte[] kSecret = ("AWS4" + secret).getBytes("UTF8");
	    byte[] kDate = hmacSha256(shortDateStamp, kSecret);
	    byte[] kRegion = hmacSha256(region, kDate);
	    byte[] kService = hmacSha256("codecommit", kRegion);
	    byte[] kSigning = hmacSha256("aws4_request", kService);
	    return hmacSha256(toSign, kSigning);
	}
	
	private static byte[] digest(URIish uri) {
		String canonicalRequest = "GIT\n" + uri.getPath() + "\n" + "\n" + "host:" + uri.getHost() + "\n" + "\n"
				+ "host\n";
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new CredentialException("No SHA-256 message digest algorithm found", e);
		}
		
		return digest.digest(canonicalRequest.getBytes());
	}
	
	/**
	 * Convert bytes to a hex string
	 * @param bytes the bytes
	 * @return a string of hex characters encoding the bytes.
	 */
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }


	/**
	 * @return the awsCredentialProvider
	 */
	public AWSCredentialsProvider getAwsCredentialProvider() {
		return awsCredentialProvider;
	}

	/**
	 * @param awsCredentialProvider the awsCredentialProvider to set
	 */
	public void setAwsCredentialProvider(AWSCredentialsProvider awsCredentialProvider) {
		this.awsCredentialProvider = awsCredentialProvider;
	}

	/**
	 * This provider can handle uris like https://git-codecommit.$AWS_REGION.amazonaws.com/v1/repos/$REPO
	 * @see org.springframework.cloud.config.server.credentials.GitCredentialsProvider#canHandleUri(java.lang.String)
	 */
	public static boolean canHandle(String uri) {
		if (!hasText(uri)) {
			return false;
		}
		
		try {
			URI u = new URI(uri.toLowerCase());
			if (u.getScheme().equals("https")) {
				String host = u.getHost();
				if (host.endsWith(".amazonaws.com") && host.startsWith("git-codecommit.")) {
					return true;
				}
			}
		} catch (Throwable t) {
			// ignore all, we can't handle it
		}
		
		return false;
	}

	/**
	 * @return the username
	 */
	public String getUsername() {
		return username;
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
		return password;
	}

	/**
	 * @param password the password to set
	 */
	public void setPassword(String password) {
		this.password = password;
	}

}
