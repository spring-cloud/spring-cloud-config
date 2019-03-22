/*
 * Copyright 2016-2019 the original author or authors.
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

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

/**
 * A {@link CredentialsProvider} that uses a passphrase.
 *
 * @author Chris Fraser
 */
public class PassphraseCredentialsProvider extends CredentialsProvider {

	/**
	 * Prompt to skip iteration for.
	 */
	public static final String PROMPT = "Passphrase for";

	private final String passphrase;

	/**
	 * Initialize the provider with a the ssh passphrase.
	 * @param passphrase passphrase to populate the credential items with
	 */
	public PassphraseCredentialsProvider(String passphrase) {
		super();
		this.passphrase = passphrase;
	}

	/**
	 * {@inheritDoc}
	 * @return
	 */
	@Override
	public boolean isInteractive() {
		return false;
	}

	/**
	 * {@inheritDoc}
	 * @return
	 */
	@Override
	public boolean supports(CredentialItem... items) {
		for (final CredentialItem item : items) {
			if (item instanceof CredentialItem.StringType
					&& item.getPromptText().startsWith(PROMPT)) {
				continue;
			}
			else {
				return false;
			}
		}
		return true;
	}

	/**
	 * Ask for the credential items to be populated with the passphrase.
	 * @param uri the URI of the remote resource that needs authentication.
	 * @param items the items the application requires to complete authentication.
	 * @return {@code true} if the request was successful and values were supplied;
	 * {@code false} if the user canceled the request and did not supply all requested
	 * values.
	 * @throws UnsupportedCredentialItem if one of the items supplied is not supported.
	 */
	@Override
	public boolean get(URIish uri, CredentialItem... items)
			throws UnsupportedCredentialItem {
		for (final CredentialItem item : items) {
			if (item instanceof CredentialItem.StringType
					&& item.getPromptText().startsWith(PROMPT)) {
				((CredentialItem.StringType) item).setValue(this.passphrase);
				continue;
			}
			throw new UnsupportedCredentialItem(uri,
					item.getClass().getName() + ":" + item.getPromptText());
		}
		return true;
	}

}
