/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.config.server.environment;

import java.util.Arrays;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

/**
 * This is based on the class
 * org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider but provides better
 * support for ssh git urls. The idea was borrowed from:
 * https://github.com/centic9/jgit-cookbook/blob/master/src/main/java/org/dstadler/jgit/
 * porcelain/CloneRemoteRepositoryWithAuthentication.java by Dominik Stadler.
 * 
 * @author Michael Davis
 *
 */
public class AllowHostsUsernamePasswordCredentialProvider extends CredentialsProvider {

	private static final String PASSWORD_PROMPT = "Password: ";
	private String username;
	private char[] password;

	public AllowHostsUsernamePasswordCredentialProvider(String username,
			String password) {
		this.username = username;
		this.password = password.toCharArray();
	}

	@Override
	public boolean supports(CredentialItem... items) {

		for (CredentialItem i : items) {

			if (i instanceof CredentialItem.Password)
				continue;

			if (i instanceof CredentialItem.StringType)
				continue;

			if (i instanceof CredentialItem.Username)
				continue;

			if (i instanceof CredentialItem.YesNoType)
				continue;

			return false;

		}
		return true;
	}

	@Override
	public boolean get(URIish uri, CredentialItem... items)
			throws UnsupportedCredentialItem {

		for (CredentialItem i : items) {

			if (i instanceof CredentialItem.Password) {
				((CredentialItem.Password) i).setValue(password);
				continue;
			}

			if (i instanceof CredentialItem.StringType
					&& PASSWORD_PROMPT.equals(i.getPromptText())) {
				((CredentialItem.StringType) i).setValue(new String(password));
				continue;
			}

			if (i instanceof CredentialItem.Username) {
				((CredentialItem.Username) i).setValue(username);
				continue;
			}

			if (i instanceof CredentialItem.YesNoType) {
				((CredentialItem.YesNoType) i).setValue(true);
				return true;
			}

			throw new UnsupportedCredentialItem(uri,
					i.getClass().getName() + ":" + i.getPromptText());
		}

		return true;
	}

	@Override
	public boolean isInteractive() {
		return false;
	}

	public void clear() {
		username = null;

		if (password != null) {
			Arrays.fill(password, (char) 0);
			password = null;
		}
	}

}
