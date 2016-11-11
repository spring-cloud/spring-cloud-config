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

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

/**
 * 
 * @author don laidlaw
 *
 */
public class GitCredentialsProviderFactory {
	
	/**
	 * Search for a credential provider that will handle the specified URI. If
	 * not found, and the username has text, then create a default using the
	 * provided username and password. Otherwise null.
	 * @param uri the URI of the repository (cannot be null)
	 * @param username the username provided for the repository (may be null)
	 * @param password the password provided for the repository (may be null)
	 * @return the first matched credentials provider or the default or null.
	 */
	public static CredentialsProvider createFor(String uri, String username, String password) {
		CredentialsProvider provider = null;
		if (AwsCodeCommitCredentialProvider.canHandle(uri)) {
			AwsCodeCommitCredentialProvider aws = new AwsCodeCommitCredentialProvider();
			aws.setUsername(username);
			aws.setPassword(password);
			provider = aws;
		} else if (hasText(username)) {
			provider = new UsernamePasswordCredentialsProvider(username, password.toCharArray());
		}
		
		return provider;
	}
	
}
