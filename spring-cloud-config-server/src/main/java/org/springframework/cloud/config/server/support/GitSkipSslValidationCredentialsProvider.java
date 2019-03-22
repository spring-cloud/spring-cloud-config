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

package org.springframework.cloud.config.server.support;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

/**
 * A {@link CredentialsProvider} that will ignore any SSL validation errors that occur.
 * This is primarily intended as a convenience for testing scenarios where self-signed
 * certificates are being used.
 *
 * This class can be used as a decorator for another CredentialsProvider, adding SSL
 * validation skipping behaviour to a repository that also requires authentication, for
 * example.
 *
 * @author Gareth Clay
 */
public class GitSkipSslValidationCredentialsProvider extends CredentialsProvider {

	private static final Pattern FORMAT_PLACEHOLDER_PATTERN = Pattern
			.compile("\\s*\\{\\d}\\s*");

	private final CredentialsProvider delegate;

	public GitSkipSslValidationCredentialsProvider(CredentialsProvider delegate) {
		this.delegate = delegate;
	}

	/**
	 * This provider can handle uris like https://github.com/org/repo .
	 * @param uri uri to verify if can be handled
	 * @return {@code true} if it can be handled
	 */
	public static boolean canHandle(String uri) {
		return uri != null && uri.toLowerCase().startsWith("https://");
	}

	private static String stripFormattingPlaceholders(String string) {
		return FORMAT_PLACEHOLDER_PATTERN.matcher(string).replaceAll("");
	}

	@Override
	public boolean isInteractive() {
		return (this.delegate != null) && this.delegate.isInteractive();
	}

	@Override
	public boolean supports(CredentialItem... items) {
		List<CredentialItem> unprocessedItems = new ArrayList<>();

		for (CredentialItem item : items) {
			if (item instanceof CredentialItem.InformationalMessage
					&& item.getPromptText() != null && item.getPromptText()
							.contains(JGitText.get().sslFailureTrustExplanation)) {
				continue;
			}

			if (item instanceof CredentialItem.YesNoType && item.getPromptText() != null
					&& (item.getPromptText().equals(JGitText.get().sslTrustNow)
							|| item.getPromptText()
									.startsWith(stripFormattingPlaceholders(
											JGitText.get().sslTrustForRepo))
							|| item.getPromptText()
									.equals(JGitText.get().sslTrustAlways))) {
				continue;
			}

			unprocessedItems.add(item);
		}

		return unprocessedItems.isEmpty() || (this.delegate != null && this.delegate
				.supports(unprocessedItems.toArray(new CredentialItem[0])));
	}

	@Override
	public boolean get(URIish uri, CredentialItem... items)
			throws UnsupportedCredentialItem {
		List<CredentialItem> unprocessedItems = new ArrayList<>();

		for (CredentialItem item : items) {
			if (item instanceof CredentialItem.YesNoType) {
				CredentialItem.YesNoType yesNoItem = (CredentialItem.YesNoType) item;
				String prompt = yesNoItem.getPromptText();
				if (prompt == null) {
					unprocessedItems.add(item);
				}
				else if (prompt.equals(JGitText.get().sslTrustNow) || prompt.startsWith(
						stripFormattingPlaceholders(JGitText.get().sslTrustForRepo))) {
					yesNoItem.setValue(true);
				}
				else if (prompt.equals(JGitText.get().sslTrustAlways)) {
					yesNoItem.setValue(false);
				}
				else {
					unprocessedItems.add(item);
				}
			}
			else if (!item.getPromptText()
					.contains(JGitText.get().sslFailureTrustExplanation)) {
				unprocessedItems.add(item);
			}
		}

		if (unprocessedItems.isEmpty()) {
			return true;
		}
		if (this.delegate != null) {
			return this.delegate.get(uri,
					unprocessedItems.toArray(new CredentialItem[0]));
		}
		throw new UnsupportedCredentialItem(uri,
				unprocessedItems.size() + " credential items not supported");
	}

	@Override
	public void reset(URIish uri) {
		if (this.delegate != null) {
			this.delegate.reset(uri);
		}
	}

}
